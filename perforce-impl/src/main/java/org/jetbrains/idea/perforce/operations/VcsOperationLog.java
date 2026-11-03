// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.operations;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.AccessToken;
import consulo.application.Application;
import consulo.application.progress.PerformInBackgroundOption;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.persist.StoragePathMacros;
import consulo.language.util.ProcessingContext;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.perforce.localize.PerforceLocalize;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.HashingStrategy;
import consulo.util.collection.Maps;
import consulo.util.collection.MultiMap;
import consulo.util.io.FileUtil;
import consulo.util.lang.function.ThrowableRunnable;
import consulo.util.xml.serializer.annotation.AbstractCollection;
import consulo.versionControlSystem.AbstractVcsHelper;
import consulo.versionControlSystem.VcsException;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.idea.perforce.application.PerforceVcs;
import org.jetbrains.idea.perforce.perforce.PerforceRunner;
import org.jetbrains.idea.perforce.perforce.PerforceSettings;
import org.jetbrains.idea.perforce.perforce.VcsConnectionProblem;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jetbrains.idea.perforce.perforce.connections.PerforceConnectionManager;
import org.jetbrains.idea.perforce.perforce.login.PerforceLoginManager;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SequencedSet;
import java.util.Set;
import java.util.TreeMap;

@Singleton
@State(name = "VcsOperationLog", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public final class VcsOperationLog implements PersistentStateComponent<VcsOperationLog.OperationList> {
    private static final Logger LOG = Logger.getInstance(VcsOperationLog.class);

    /**
     * Path keys are compared as canonical file paths (respecting file system case sensitivity).
     */
    private static final HashingStrategy<String> CANONICAL_PATH_STRATEGY = new HashingStrategy<>() {
        @Override
        public int hashCode(@Nullable String path) {
            return FileUtil.pathHashCode(path);
        }

        @Override
        public boolean equals(@Nullable String path1, @Nullable String path2) {
            return FileUtil.pathsEqual(path1, path2);
        }
    };

    public static class OperationList {
        private final MultiMap<String, VcsOperation> myOperationsByOutputPath =
            new MultiMap<>(Maps.<String, Collection<VcsOperation>>newLinkedHashMap(CANONICAL_PATH_STRATEGY));

        @AbstractCollection(
            elementTypes = {
                P4AddOperation.class, P4CopyOperation.class, P4DeleteOperation.class, P4MoveRenameOperation.class, P4EditOperation.class,
                P4RevertOperation.class, P4MoveToChangeListOperation.class
            })
        public List<VcsOperation> getOperations() {
            return new ArrayList<>(myOperationsByOutputPath.values());
        }

        @AbstractCollection(
            elementTypes = {
                P4AddOperation.class, P4CopyOperation.class, P4DeleteOperation.class, P4MoveRenameOperation.class, P4EditOperation.class,
                P4RevertOperation.class, P4MoveToChangeListOperation.class
            })
        public void setOperations(List<VcsOperation> operations) {
            myOperationsByOutputPath.clear();
            for (VcsOperation operation : operations) {
                addOperation(operation);
            }
        }

        private void addOperation(VcsOperation operation) {
            myOperationsByOutputPath.putValue(operation.getOutputPath(), operation);
        }

        private void removeOperation(VcsOperation operation) {
            myOperationsByOutputPath.remove(operation.getOutputPath(), operation);
        }
    }

    private final Object myLock = new Object();
    private final Project myProject;
    private OperationList myOperations = new OperationList();

    @Inject
    public VcsOperationLog(Project project) {
        myProject = project;
    }

    public static VcsOperationLog getInstance(Project project) {
        return project.getInstance(VcsOperationLog.class);
    }

    @Override
    public OperationList getState() {
        synchronized (myLock) {
            return myOperations;
        }
    }

    @Override
    public void loadState(OperationList state) {
        synchronized (myLock) {
            myOperations = state;
        }
    }

    public void addToLog(VcsOperation vcsOperation) {
        synchronized (myLock) {
            for (VcsOperation oldOp : myOperations.myOperationsByOutputPath.get(vcsOperation.getInputPath())) {
                VcsOperation mergedOp = vcsOperation.checkMerge(oldOp);
                if (mergedOp != oldOp) {
                    myOperations.removeOperation(oldOp);
                    if (mergedOp != null) {
                        myOperations.addOperation(mergedOp);
                    }
                    return;
                }
            }
            LOG.debug("Add to log " + vcsOperation);
            vcsOperation.prepareOffline(myProject);
            myOperations.addOperation(vcsOperation);
        }
    }

    public boolean runOperations(List<VcsOperation> operations,
                                 LocalizeValue title,
                                 PerformInBackgroundOption option,
                                 List<VcsException> exceptions) {
        Runnable runnable = enqueueOperations(operations, title, option, exceptions);
        if (runnable == null) {
            return false;
        }
        int startSize = exceptions.size();
        runnable.run();
        return startSize == exceptions.size();
    }

    public void queueOperations(List<? extends VcsOperation> operations, LocalizeValue title, PerformInBackgroundOption option) {
        queueOperations(operations, title, option, null);
    }

    public void queueOperations(List<? extends VcsOperation> operations,
                                LocalizeValue title,
                                PerformInBackgroundOption option,
                                @Nullable Runnable edtCallback) {
        List<VcsException> exceptions = new ArrayList<>();
        Runnable runnable = enqueueOperations(operations, title, option, exceptions);

        if (runnable == null) {
            return;
        }
        PerforceVcs.getInstance(myProject).runBackgroundTask(title.get(), option, runnable, edtCallback);
    }

    private @Nullable Runnable enqueueOperations(List<? extends VcsOperation> operations,
                                                 LocalizeValue title,
                                                 PerformInBackgroundOption option,
                                                 List<VcsException> exceptions) {
        synchronized (myLock) {
            for (VcsOperation operation : operations) {
                addToLog(operation);
            }
        }

        if (!PerforceSettings.getSettings(myProject).ENABLED) {
            return null;
        }

        return () -> {
            Set<P4Connection> authorized = new HashSet<>();
            while (true) {
                try (AccessToken ignored = PerforceVcs.getInstance(myProject).writeLockP4()) {
                    if (!new MergedOperationExecutor(takeMergeableOperations(), title, option, authorized).executeOperations()) {
                        break;
                    }
                }
                catch (VcsException e) {
                    exceptions.add(e);
                }
            }
            if (!exceptions.isEmpty()) {
                AbstractVcsHelper.getInstance(myProject).showErrors(exceptions, title);
            }
        };
    }

    public void replayLog() {
        queueOperations(Collections.emptyList(), PerforceLocalize.replayingOfflineOperations(), PerformInBackgroundOption.DEAF);
    }

    private List<VcsOperation> takeMergeableOperations() {
        synchronized (myLock) {
            if (!myOperations.myOperationsByOutputPath.isEmpty()) {
                Collection<? extends VcsOperation> allOperations = myOperations.myOperationsByOutputPath.values();
                VcsOperation firstOp = allOperations.iterator().next();
                List<VcsOperation> mergeable = ContainerUtil.filter(allOperations, op -> op.getClass() == firstOp.getClass());
                mergeable.forEach(myOperations::removeOperation);
                return mergeable;
            }
            return Collections.emptyList();
        }
    }

    public List<VcsOperation> getPendingOperations() {
        synchronized (myLock) {
            return myOperations.getOperations();
        }
    }

    /**
     * Returns a map from a file path to the name of the changelist in which the file was reopened while offline.
     * If the value is null, the file has been reverted while offline.
     *
     * @return map of path to changelist name or null
     */
    public Map<String, @Nullable String> getReopenedPaths() {
        Map<String, @Nullable String> result = new TreeMap<>();
        for (VcsOperation op : getPendingOperations()) {
            op.fillReopenedPaths(result);
        }
        return result;
    }

    private class MergedOperationExecutor {
        private final SequencedSet<VcsOperation> myRemaining;
        private final LocalizeValue myTitle;
        private final PerformInBackgroundOption myOption;
        private final Set<P4Connection> myAuthorized;
        private final ProcessingContext myContext = new ProcessingContext();

        MergedOperationExecutor(List<VcsOperation> operations,
                                LocalizeValue title,
                                PerformInBackgroundOption option,
                                Set<P4Connection> authorized) {
            myRemaining = new LinkedHashSet<>(operations);
            myTitle = title;
            myOption = option;
            myAuthorized = authorized;
        }

        private @Nullable Map<ThrowableRunnable<VcsException>, Collection<VcsOperation>> mergeOperations() throws VcsConnectionProblem {
            Map<ThrowableRunnable<VcsException>, Collection<VcsOperation>> result = new LinkedHashMap<>();

            MultiMap<P4Connection, VcsOperation> byConnection = new MultiMap<>();

            for (VcsOperation operation : myRemaining) {
                Set<P4Connection> touchedConnections = ensureAuthorized(operation);
                if (touchedConnections == null) {
                    return null;
                }

                if (touchedConnections.size() == 1) {
                    byConnection.putValue(touchedConnections.iterator().next(), operation);
                }
                else {
                    handleNonMergeableOperation(result, operation);
                }
            }

            if (myRemaining.getFirst() instanceof P4RevertOperation) {
                for (P4Connection connection : byConnection.keySet()) {
                    mergeRevert(result, connection, byConnection.get(connection));
                }
            }
            else {
                for (VcsOperation operation : byConnection.values()) {
                    handleNonMergeableOperation(result, operation);
                }
            }

            return result;
        }

        private void mergeRevert(Map<ThrowableRunnable<VcsException>, Collection<VcsOperation>> result,
                                 P4Connection connection,
                                 Collection<VcsOperation> operations) {
            if (operations.size() == 1) {
                handleNonMergeableOperation(result, operations.iterator().next());
                return;
            }

            result.put(() -> {
                List<String> toRevert = new ArrayList<>();
                List<File> toDelete = new ArrayList<>();
                for (VcsOperation operation : operations) {
                    ((P4RevertOperation) operation).prepareRevert(toRevert, toDelete);
                }
                PerforceRunner.getInstance(myProject).revertAll(toRevert, connection);
                P4RevertOperation.refreshAfterRevert(toRevert, toDelete, myProject);
            }, operations);
        }

        private void handleNonMergeableOperation(Map<ThrowableRunnable<VcsException>, Collection<VcsOperation>> result,
                                                 VcsOperation operation) {
            result.put(() -> operation.execute(myProject, myContext), Collections.singletonList(operation));
        }

        private @Nullable Set<P4Connection> ensureAuthorized(VcsOperation operation) throws VcsConnectionProblem {
            Set<P4Connection> touchedConnections = new HashSet<>();
            for (String path : operation.getAffectedPaths()) {
                P4Connection connection = PerforceConnectionManager.getInstance(myProject).getConnectionForFile(new File(path));
                if (connection == null) {
                    return null;
                }
                touchedConnections.add(connection);
                if (myAuthorized.add(connection)) {
                    PerforceLoginManager.getInstance(myProject).check(connection, true);
                }
            }
            return touchedConnections;
        }

        private void fixLater(VcsConnectionProblem e) {
            LOG.info(e);
            Application application = Application.get();
            application.invokeLater(() -> {
                boolean inTests = application.isUnitTestMode();
                if (e.attemptQuickFix(!inTests) && PerforceSettings.getSettings(myProject).ENABLED) {
                    queueOperations(Collections.emptyList(), myTitle, myOption);
                }
                else {
                    PerforceSettings.getSettings(myProject).disable(false);
                }
            });
        }

        private void pushBackOperations() {
            synchronized (myLock) {
                List<VcsOperation> operations = new ArrayList<>(myRemaining);
                operations.addAll(myOperations.getOperations());
                myOperations.setOperations(operations);
            }
        }

        private boolean executeOperations() throws VcsException {
            if (myRemaining.isEmpty()) {
                return false;
            }

            try {
                Map<ThrowableRunnable<VcsException>, Collection<VcsOperation>> map = mergeOperations();
                if (map == null) {
                    pushBackOperations();
                    AbstractVcsHelper.getInstance(myProject)
                        .showError(new VcsException(PerforceLocalize.errorCanNotExecuteInvalidConnectionSettings(myTitle)), myTitle);
                    return false;
                }

                for (Map.Entry<ThrowableRunnable<VcsException>, Collection<VcsOperation>> entry : map.entrySet()) {
                    entry.getKey().run();
                    myRemaining.removeAll(entry.getValue());
                }
            }
            catch (VcsConnectionProblem e) {
                pushBackOperations();
                fixLater(e);
                return false;
            }

            return true;
        }
    }
}

/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.perforce.application;

import consulo.application.AccessToken;
import consulo.application.Application;
import consulo.application.progress.PerformInBackgroundOption;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.progress.Task;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.localize.LocalizeValue;
import consulo.perforce.icon.PerforceIconGroup;
import consulo.perforce.localize.PerforceLocalize;
import consulo.platform.base.localize.CommonLocalize;
import consulo.project.Project;
import consulo.ui.ex.awt.Messages;
import consulo.ui.image.Image;
import consulo.util.collection.MultiMap;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ThreeState;
import consulo.versionControlSystem.AbstractVcs;
import consulo.versionControlSystem.EditFileProvider;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.ProjectLevelVcsManager;
import consulo.versionControlSystem.RemoteDifferenceStrategy;
import consulo.versionControlSystem.TreeDiffProvider;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.VcsExceptionsHotFixer;
import consulo.versionControlSystem.VcsKey;
import consulo.versionControlSystem.annotate.AnnotationProvider;
import consulo.versionControlSystem.change.ChangeListManager;
import consulo.versionControlSystem.change.ChangeProvider;
import consulo.versionControlSystem.change.LocalChangeList;
import consulo.versionControlSystem.change.VcsDirtyScopeManager;
import consulo.versionControlSystem.diff.DiffProvider;
import consulo.versionControlSystem.history.VcsHistoryProvider;
import consulo.versionControlSystem.history.VcsRevisionNumber;
import consulo.versionControlSystem.merge.MergeProvider;
import consulo.versionControlSystem.rollback.RollbackEnvironment;
import consulo.versionControlSystem.update.UpdateEnvironment;
import consulo.versionControlSystem.versionBrowser.CommittedChangeList;
import consulo.virtualFileSystem.RefreshQueue;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.status.FileStatus;
import consulo.virtualFileSystem.util.ReadOnlyAttributeUtil;
import org.jetbrains.idea.perforce.actions.ShelfUtils;
import org.jetbrains.idea.perforce.merge.PerforceMergeProvider;
import org.jetbrains.idea.perforce.operations.P4EditOperation;
import org.jetbrains.idea.perforce.operations.VcsOperation;
import org.jetbrains.idea.perforce.operations.VcsOperationLog;
import org.jetbrains.idea.perforce.perforce.P4File;
import org.jetbrains.idea.perforce.perforce.PerforceSettings;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jetbrains.idea.perforce.perforce.connections.PerforceConnectionManager;
import org.jetbrains.idea.perforce.perforce.connections.PerforceConnectionManagerI;
import org.jetbrains.idea.perforce.perforce.jobs.PerforceJob;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Perforce version control system. Created by {@link PerforceVcsFactory}.
 */
public final class PerforceVcs extends AbstractVcs<CommittedChangeList> {
    public static final String NAME = "Perforce";
    private static final VcsKey ourKey = createKey(NAME);

    private @Nullable PerforceCheckinEnvironment myPerforceCheckinEnvironment;
    private @Nullable PerforceUpdateEnvironment myPerforceUpdateEnvironment;
    private @Nullable PerforceIntegrateEnvironment myPerforceIntegrateEnvironment;
    private @Nullable RollbackEnvironment myPerforceRollbackEnvironment;
    private @Nullable RollbackEnvironment myOfflineRollbackEnvironment;
    private @Nullable PerforceCommittedChangesProvider myCommittedChangesProvider;

    private final MyEditFileProvider myMyEditFileProvider;
    private @Nullable PerforceChangeProvider myChangeProvider;
    private @Nullable ChangeProvider myOfflineChangeProvider;
    private @Nullable PerforceVcsHistoryProvider myHistoryProvider;
    private @Nullable PerforceAnnotationProvider myAnnotationProvider;
    private @Nullable PerforceDiffProvider myDiffProvider;
    private @Nullable PerforceTreeDiffProvider myTreeDiffProvider;

    private @Nullable MergeProvider myMergeProvider;

    private final AtomicReference<@Nullable Disposable> myActiveDisposable = new AtomicReference<>();

    private final Set<VirtualFile> myAsyncEditFiles = new HashSet<>();

    private final Map<ConnectionKey, List<PerforceJob>> myDefaultAssociated = new HashMap<>();

    private final ReentrantReadWriteLock myP4Lock = new ReentrantReadWriteLock();

    public PerforceVcs(Project project) {
        super(project, NAME);
        myMyEditFileProvider = new MyEditFileProvider();
    }

    @Override
    public LocalizeValue getDisplayName() {
        return PerforceLocalize.perforce();
    }

    @Override
    public LocalizeValue getShortNameWithMnemonic() {
        return PerforceLocalize.perforceNameWithMnemonic();
    }

    @Override
    public Image getIcon() {
        return PerforceIconGroup.p4toolwindow();
    }

    private <T> @Nullable T validProvider(T initialValue) {
        return getSettings().ENABLED ? initialValue : null;
    }

    @Override
    public boolean allowsRemoteCalls(VirtualFile file) {
        return getSettings().ENABLED;
    }

    @Override
    public boolean isTrackingUnchangedContent() {
        return true;
    }

    @Override
    public EditFileProvider getEditFileProvider() {
        return myMyEditFileProvider;
    }

    @Override
    public PerforceCheckinEnvironment getCheckinEnvironment() {
        PerforceCheckinEnvironment environment = myPerforceCheckinEnvironment;
        if (environment == null) {
            environment = new PerforceCheckinEnvironment(myProject, this);
            myPerforceCheckinEnvironment = environment;
        }
        return environment;
    }

    @Override
    public RollbackEnvironment getRollbackEnvironment() {
        if (PerforceSettings.getSettings(myProject).ENABLED) {
            RollbackEnvironment environment = myPerforceRollbackEnvironment;
            if (environment == null) {
                environment = new PerforceRollbackEnvironment(myProject);
                myPerforceRollbackEnvironment = environment;
            }
            return environment;
        }
        else {
            RollbackEnvironment environment = myOfflineRollbackEnvironment;
            if (environment == null) {
                environment = new PerforceOfflineRollbackEnvironment(myProject);
                myOfflineRollbackEnvironment = environment;
            }
            return environment;
        }
    }

    private void autoEditVFile(VirtualFile[] vFiles) {
        ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
        // check whether it will be under any clientspec
        Application.get().runWriteAction(() -> {
            for (VirtualFile file : vFiles) {
                PerforceVFSListener.updateLastUnchangedContent(file, changeListManager);
                try {
                    ReadOnlyAttributeUtil.setReadOnlyAttribute(file, false);
                }
                catch (IOException e) {
                    // ignore - we'll get some message from 'p4 revert'
                }
            }
        });
        startAsyncEdit(vFiles);

        List<VcsOperation> operations = new ArrayList<>();
        for (VirtualFile vFile : vFiles) {
            LocalChangeList list = changeListManager.getChangeList(vFile);
            if (list == null) {
                list = changeListManager.getDefaultChangeList();
            }
            operations.add(new P4EditOperation(list.getName(), vFile, false));
        }
        VcsOperationLog.getInstance(myProject).queueOperations(
            operations,
            PerforceLocalize.progressTitlePerforceEdit(),
            PerformInBackgroundOption.ALWAYS_BACKGROUND,
            () -> refreshFiles(vFiles)
        );
    }

    public void refreshFiles(VirtualFile... filesToRefresh) {
        List<VirtualFile> files = Arrays.asList(filesToRefresh);
        RefreshQueue.getInstance().refresh(true, false, null, files);
        asyncEditCompleted(files);
        VcsDirtyScopeManager.getInstance(myProject).filesDirty(files, null);
    }

    public void startAsyncEdit(VirtualFile... vFiles) {
        synchronized (myAsyncEditFiles) {
            Collections.addAll(myAsyncEditFiles, vFiles);
        }
    }

    public Set<VirtualFile> getAsyncEditedFiles() {
        synchronized (myAsyncEditFiles) {
            return new HashSet<>(myAsyncEditFiles);
        }
    }

    public void asyncEditCompleted(Collection<VirtualFile> files) {
        synchronized (myAsyncEditFiles) {
            myAsyncEditFiles.removeAll(files);
        }
    }

    public void asyncEditCompleted(VirtualFile file) {
        synchronized (myAsyncEditFiles) {
            myAsyncEditFiles.remove(file);
        }
    }

    public static PerforceVcs getInstance(Project project) {
        return (PerforceVcs) Objects.requireNonNull(ProjectLevelVcsManager.getInstance(project).findVcsByName(NAME));
    }

    @Override
    public UpdateEnvironment getUpdateEnvironment() {
        PerforceUpdateEnvironment environment = myPerforceUpdateEnvironment;
        if (environment == null) {
            environment = new PerforceUpdateEnvironment(myProject);
            myPerforceUpdateEnvironment = environment;
        }
        return environment;
    }

    public PerforceSettings getSettings() {
        return PerforceSettings.getSettings(myProject);
    }

    public static @Nullable String getFileNameComplaint(P4File file) {
        String filename = file.getLocalPath();
        if (StringUtil.containsAnyChar(filename, "@#%*")) {
            return PerforceLocalize.fileWildcardsRestricted(filename).get();
        }
        return null;
    }

    private class MyEditFileProvider implements EditFileProvider {
        @Override
        public void editFiles(VirtualFile[] files) {
            autoEditVFile(files);
        }

        @Override
        public String getRequestText() {
            return PerforceLocalize.confirmationTextOpenFilesForEdit().get();
        }
    }

    @Override
    public boolean fileExistsInVcs(FilePath filePath) {
        VirtualFile virtualFile = filePath.getVirtualFile();
        if (virtualFile == null) {
            return false;
        }

        PerforceManager perforceManager = PerforceManager.getInstance(getProject());

        if (!getSettings().ENABLED) {
            return true;
        }

        if (virtualFile.isDirectory()) {
            try {
                return perforceManager.isUnderPerforceRoot(virtualFile);
            }
            catch (VcsException e) {
                return false;
            }
        }
        else {
            FileStatus fileStatus = ChangeListManager.getInstance(myProject).getStatus(virtualFile);
            if (FileStatus.ADDED.equals(fileStatus)) {
                if (Boolean.TRUE.equals(virtualFile.getUserData(OpenedResultProcessor.BRANCHED_FILE))) {
                    return true;
                }
            }
            return fileStatus != FileStatus.UNKNOWN && fileStatus != FileStatus.ADDED;
        }
    }

    @Override
    public @Nullable VcsHistoryProvider getVcsHistoryProvider() {
        PerforceVcsHistoryProvider provider = myHistoryProvider;
        if (provider == null) {
            provider = new PerforceVcsHistoryProvider(this);
            myHistoryProvider = provider;
        }
        return validProvider(provider);
    }

    @Override
    public @Nullable VcsHistoryProvider getVcsBlockHistoryProvider() {
        return getVcsHistoryProvider();
    }

    @Override
    public @Nullable AnnotationProvider getAnnotationProvider() {
        PerforceAnnotationProvider provider = myAnnotationProvider;
        if (provider == null) {
            provider = new PerforceAnnotationProvider(myProject);
            myAnnotationProvider = provider;
        }
        return validProvider(provider);
    }

    @Override
    public @Nullable DiffProvider getDiffProvider() {
        PerforceDiffProvider provider = myDiffProvider;
        if (provider == null) {
            provider = new PerforceDiffProvider(myProject);
            myDiffProvider = provider;
        }
        return validProvider(provider);
    }

    @Override
    public ChangeProvider getChangeProvider() {
        if (getSettings().ENABLED) {
            return getOnlineChangeProvider();
        }
        else {
            ChangeProvider provider = myOfflineChangeProvider;
            if (provider == null) {
                provider = new PerforceOfflineChangeProvider(myProject);
                myOfflineChangeProvider = provider;
            }
            return provider;
        }
    }

    public PerforceChangeProvider getOnlineChangeProvider() {
        return initChangeProvider();
    }

    private PerforceChangeProvider initChangeProvider() {
        PerforceChangeProvider provider = myChangeProvider;
        if (provider == null) {
            provider = new PerforceChangeProvider(this);
            myChangeProvider = provider;
        }
        return provider;
    }

    @Override
    public @Nullable UpdateEnvironment getIntegrateEnvironment() {
        PerforceIntegrateEnvironment environment = myPerforceIntegrateEnvironment;
        if (environment == null) {
            environment = new PerforceIntegrateEnvironment(myProject);
            myPerforceIntegrateEnvironment = environment;
        }
        return validProvider(environment);
    }

    @Override
    public void activate() {
        Disposable disposable = Disposable.newDisposable("PerforceVcs");
        Disposer.register(PerforceDisposable.getInstance(myProject), disposable);
        Disposable oldDisposable = myActiveDisposable.getAndSet(disposable);
        if (oldDisposable != null) {
            Disposer.dispose(oldDisposable);
        }

        Disposer.register(disposable, PerforceVFSListener.createInstance(myProject));

        PerforceManager.getInstance(myProject).startListening(disposable);
        ((PerforceConnectionManager) PerforceConnectionManager.getInstance(myProject)).startListening(disposable);
        PerforceNumberNameSynchronizer.getInstance(myProject).startListening(disposable);
        PerforceSettings.getSettings(myProject).ensureOfflineNotify();

        initChangeProvider().activate(disposable);
    }

    @Override
    public void deactivate() {
        Disposable oldDisposable = myActiveDisposable.getAndSet(null);
        if (oldDisposable != null) {
            Disposer.dispose(oldDisposable);
        }
    }

    @Override
    public PerforceCommittedChangesProvider getCommittedChangesProvider() {
        PerforceCommittedChangesProvider provider = myCommittedChangesProvider;
        if (provider == null) {
            provider = new PerforceCommittedChangesProvider(myProject);
            myCommittedChangesProvider = provider;
        }
        return provider;
    }

    public void runBackgroundTask(
        String title,
        PerformInBackgroundOption option,
        Runnable runnable,
        @Nullable Runnable edtCallback
    ) {
        if (Application.get().isUnitTestMode()) {
            runnable.run();
            if (edtCallback != null) {
                edtCallback.run();
            }
        }
        else {
            ProgressManager.getInstance().run(new Task.Backgroundable(myProject, LocalizeValue.of(title), false, option) {
                @Override
                public void run(ProgressIndicator indicator) {
                    runnable.run();
                }

                @Override
                public void onFinished() {
                    if (edtCallback != null) {
                        edtCallback.run();
                    }
                }
            });
        }
    }

    @Override
    public @Nullable VcsRevisionNumber parseRevisionNumber(String revisionNumberString) {
        long revision;
        try {
            revision = Long.parseLong(revisionNumberString);
        }
        catch (NumberFormatException ex) {
            return null;
        }
        return new PerforceOnlyRevisionNumber(revision);
    }

    @Override
    public String getRevisionPattern() {
        return ourIntegerPattern;
    }

    @Override
    public MergeProvider getMergeProvider() {
        MergeProvider provider = myMergeProvider;
        if (provider == null) {
            provider = new PerforceMergeProvider(myProject);
            myMergeProvider = provider;
        }
        return provider;
    }

    public Map<ConnectionKey, List<PerforceJob>> getDefaultAssociated() {
        synchronized (myDefaultAssociated) {
            return new HashMap<>(myDefaultAssociated);
        }
    }

    public void setDefaultAssociated(Map<ConnectionKey, List<PerforceJob>> jobs) {
        synchronized (myDefaultAssociated) {
            myDefaultAssociated.clear();
            myDefaultAssociated.putAll(jobs);
        }
    }

    public void clearDefaultAssociated() {
        synchronized (myDefaultAssociated) {
            myDefaultAssociated.clear();
        }
    }

    @Override
    public VcsExceptionsHotFixer getVcsExceptionsHotFixer() {
        return myProject.getInstance(PerforceExceptionsHotFixer.class);
    }

    public static VcsKey getKey() {
        return ourKey;
    }

    public Collection<Pair<P4Connection, Collection<VirtualFile>>> getRootsByConnections() throws VcsException {
        ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);
        PerforceConnectionManagerI connectionManager = PerforceConnectionManager.getInstance(myProject);
        VirtualFile[] roots = vcsManager.getRootsUnderVcs(this);

        MultiMap<ConnectionKey, VirtualFile> rootsByConnections = new MultiMap<>();
        Map<ConnectionKey, P4Connection> connMap = new HashMap<>();
        for (VirtualFile root : roots) {
            P4Connection connection = connectionManager.getConnectionForFile(root);
            if (connection != null) {
                PerforceManager.ensureValidClient(myProject, connection);
                ConnectionKey key = connection.getConnectionKey();
                connMap.put(key, connection);
                rootsByConnections.putValue(key, root);
            }
        }

        Collection<Pair<P4Connection, Collection<VirtualFile>>> result = new LinkedList<>();
        for (ConnectionKey key : rootsByConnections.keySet()) {
            P4Connection connection = connMap.get(key);
            if (connection != null) {
                result.add(Pair.create(connection, rootsByConnections.get(key)));
            }
        }

        return result;
    }

    @Override
    protected TreeDiffProvider getTreeDiffProviderImpl() {
        PerforceTreeDiffProvider provider = myTreeDiffProvider;
        if (provider == null) {
            provider = new PerforceTreeDiffProvider(this);
            myTreeDiffProvider = provider;
        }
        return provider;
    }

    @Override
    public RemoteDifferenceStrategy getRemoteDifferenceStrategy() {
        return RemoteDifferenceStrategy.ASK_TREE_PROVIDER;
    }

    @Override
    public boolean allowsNestedRoots() {
        return true;
    }

    public static boolean revisionsSame(VcsRevisionNumber number1, VcsRevisionNumber number2) {
        long revision1 = extractNumber(number1);
        long revision2 = extractNumber(number2);
        return revision1 > 0 && revision1 == revision2;
    }

    private static long extractNumber(VcsRevisionNumber number) {
        if (number instanceof VcsRevisionNumber.Long) {
            return ((VcsRevisionNumber.Long) number).getLongValue();
        }
        if (number instanceof PerforceVcsRevisionNumber) {
            return ((PerforceVcsRevisionNumber) number).getChangeNumber();
        }
        if (number instanceof PerforceOnlyRevisionNumber) {
            return ((PerforceOnlyRevisionNumber) number).getNumber();
        }
        return -1;
    }

    @Override
    public ThreeState mayRemoveChangeList(LocalChangeList list, boolean explicitly) {
        List<ShelvedChange> shelvedChanges = PerforceManager.getInstance(myProject).getShelf().getShelvedChanges(list);
        if (!shelvedChanges.isEmpty()) {
            if (!explicitly) {
                return ThreeState.NO;
            }

            String message = PerforceLocalize.changelistShelvedChangesDelete(list.getName()).get();
            int rc = Messages.showDialog(
                myProject,
                message,
                PerforceLocalize.changelistShelvedChangesFound().get(),
                new String[]{
                    PerforceLocalize.shelfBrowseMnemonic().get(),
                    CommonLocalize.buttonRemove().get(),
                    CommonLocalize.buttonCancel().get()
                },
                0,
                Messages.getQuestionIcon()
            );
            if (rc == 1) {
                ShelfUtils.deleteFromShelf(shelvedChanges, myProject);
                return ThreeState.YES;
            }
            if (rc == 0) {
                ShelfUtils.browseShelf(myProject, shelvedChanges);
            }
            return ThreeState.NO;
        }

        return ThreeState.UNSURE;
    }

    public AccessToken readLockP4() {
        ReentrantReadWriteLock.ReadLock lock = myP4Lock.readLock();
        lock.lock();
        return new AccessToken() {
            @Override
            public void finish() {
                lock.unlock();
            }
        };
    }

    public AccessToken writeLockP4() {
        ReentrantReadWriteLock.WriteLock lock = myP4Lock.writeLock();
        lock.lock();
        return new AccessToken() {
            @Override
            public void finish() {
                lock.unlock();
            }
        };
    }
}

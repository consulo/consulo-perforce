// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).

package org.jetbrains.idea.perforce.application;

import consulo.application.progress.PerformInBackgroundOption;
import consulo.logging.Logger;
import consulo.perforce.localize.PerforceLocalize;
import consulo.project.Project;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.AnAction;
import consulo.util.collection.MultiMap;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.change.Change;
import consulo.versionControlSystem.change.ChangeListManager;
import consulo.versionControlSystem.change.ChangesUtil;
import consulo.versionControlSystem.change.LocalChangeList;
import consulo.versionControlSystem.change.VcsDirtyScopeManager;
import consulo.versionControlSystem.rollback.RollbackEnvironment;
import consulo.versionControlSystem.rollback.RollbackProgressListener;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.idea.perforce.operations.P4RevertOperation;
import org.jetbrains.idea.perforce.operations.VcsOperation;
import org.jetbrains.idea.perforce.operations.VcsOperationLog;
import org.jetbrains.idea.perforce.perforce.FStat;
import org.jetbrains.idea.perforce.perforce.P4File;
import org.jetbrains.idea.perforce.perforce.PerforceRunner;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jetbrains.idea.perforce.perforce.connections.PerforceConnectionManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PerforceRollbackEnvironment implements RollbackEnvironment {
    private static final Logger LOG = Logger.getInstance(PerforceRollbackEnvironment.class);

    private final Project myProject;
    private final PerforceRunner myRunner;

    public PerforceRollbackEnvironment(Project project) {
        myProject = project;
        myRunner = PerforceRunner.getInstance(project);
    }

    @Override
    public String getRollbackOperationName() {
        return PerforceLocalize.operationNameRevert().get();
    }

    @Override
    public void rollbackChanges(List<Change> changes, List<VcsException> vcsExceptions, RollbackProgressListener listener) {
        Map<Long, P4Connection> lists2Delete = getChangeListsToDelete(changes, vcsExceptions);

        List<VcsOperation> operations = new ArrayList<>();
        for (Change change : changes) {
            operations.add(new P4RevertOperation(change));
        }
        if (!VcsOperationLog.getInstance(myProject).runOperations(
            operations,
            PerforceLocalize.progressTitleRevert(),
            PerformInBackgroundOption.ALWAYS_BACKGROUND,
            vcsExceptions
        )) {
            return;
        }

        boolean listsChanged = false;
        for (Map.Entry<Long, P4Connection> entry : lists2Delete.entrySet()) {
            long id = entry.getKey();
            try {
                if (myRunner.deleteChangeList(entry.getValue(), id, true, true, true)) {
                    listsChanged = true;
                }
            }
            catch (VcsException e) {
                vcsExceptions.add(e);
            }
        }

        if (listsChanged) {
            VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
        }
    }

    private Map<Long, P4Connection> getChangeListsToDelete(List<Change> changes, List<? super VcsException> vcsExceptions) {
        Map<Long, P4Connection> lists2Delete = new LinkedHashMap<>();
        for (LocalChangeList list : ChangeListManager.getInstance(myProject).getChangeLists()) {
            Collection<Change> reverted = list.getChanges();
            if (!reverted.isEmpty() && changes.containsAll(reverted)) {
                for (Change c : reverted) {
                    FilePath afterPath = ChangesUtil.getAfterPath(c);
                    if (afterPath != null) {
                        P4Connection connection = PerforceConnectionManager.getInstance(myProject).getConnectionForFile(afterPath);
                        if (connection != null) {
                            try {
                                PerforceManager.ensureValidClient(myProject, connection);
                            }
                            catch (VcsException e) {
                                vcsExceptions.add(e);
                            }

                            ConnectionKey key = connection.getConnectionKey();
                            Long number = PerforceNumberNameSynchronizer.getInstance(myProject).getNumber(key, list.getName());
                            if (number != null) {
                                lists2Delete.put(number, connection);
                            }
                            break;
                        }
                    }
                }
            }
        }
        return lists2Delete;
    }

    @Override
    public void rollbackMissingFileDeletion(List<FilePath> files, List<VcsException> exceptions, RollbackProgressListener listener) {
        for (FilePath file : files) {
            listener.accept(file);
            try {
                P4File p4file = P4File.create(file);
                FStat fStat;
                try {
                    fStat = p4file.getFstat(myProject, true);
                }
                catch (VcsException e) {
                    LOG.info(e);
                    continue;
                }
                if (fStat.local == FStat.LOCAL_CHECKED_OUT || fStat.local == FStat.LOCAL_INTEGRATING || fStat.local == FStat.LOCAL_ADDING) {
                    myRunner.revert(p4file, false);
                }
                else {
                    myRunner.sync(p4file, true);
                }
                VcsDirtyScopeManager.getInstance(myProject).fileDirty(file);
            }
            catch (VcsException e) {
                exceptions.add(e);
            }
        }
    }

    @Override
    public void rollbackModifiedWithoutCheckout(List<VirtualFile> files, List<VcsException> exceptions, RollbackProgressListener listener) {
        MultiMap<P4Connection, VirtualFile> map = FileGrouper.distributeFilesByConnection(files, myProject);
        for (Map.Entry<P4Connection, Collection<VirtualFile>> entry : map.entrySet()) {
            List<String> paths = new ArrayList<>();
            List<P4File> p4Files = new ArrayList<>();
            for (VirtualFile file : entry.getValue()) {
                P4File p4File = P4File.create(file);
                p4Files.add(p4File);
                paths.add(p4File.getEscapedPath());
            }

            P4Connection connection = entry.getKey();
            try {
                myRunner.editAll(p4Files, -1, false, connection);
                myRunner.revertAll(paths, connection);
            }
            catch (VcsException e) {
                exceptions.add(e);
            }
        }
    }

    @Override
    public void rollbackIfUnchanged(VirtualFile file) {
    }

    @Override
    public Collection<? extends AnAction> createCustomRollbackActions() {
        AnAction action = ActionManager.getInstance().getAction("RevertUnchanged");
        return action == null ? List.of() : List.of(action);
    }
}

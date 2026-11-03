// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.operations;

import consulo.perforce.localize.PerforceLocalize;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.lang.Comparing;
import consulo.util.io.FileUtil;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.action.VcsContextFactory;
import consulo.versionControlSystem.change.Change;
import consulo.versionControlSystem.change.ChangeListManagerGate;
import consulo.versionControlSystem.change.ContentRevision;
import consulo.versionControlSystem.change.CurrentContentRevision;
import consulo.language.util.ProcessingContext;
import consulo.versionControlSystem.util.ActionWithTempFile;
import org.jetbrains.idea.perforce.ClientVersion;
import org.jetbrains.idea.perforce.ServerVersion;
import org.jetbrains.idea.perforce.application.PerforceClient;
import org.jetbrains.idea.perforce.application.PerforceManager;
import org.jetbrains.idea.perforce.perforce.FStat;
import org.jetbrains.idea.perforce.perforce.P4File;
import org.jetbrains.idea.perforce.perforce.P4WhereResult;
import org.jetbrains.idea.perforce.perforce.PerforceCachingContentRevision;
import org.jetbrains.idea.perforce.perforce.PerforceRunner;
import org.jetbrains.idea.perforce.perforce.ResolvedFile;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jetbrains.idea.perforce.perforce.connections.PerforceConnectionManager;

import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;


public class P4MoveRenameOperation extends VcsOperation {
    private static final Logger LOG = Logger.getInstance(P4MoveRenameOperation.class);
    private static final int CYCLE = 3;
    private static final int DOUBLE_RENAME = 2;
    private static final int SIMPLE = 1;

    public String oldPath = "";
    public String newPath = "";

    @SuppressWarnings("unused") // used by deserialization reflection
    public P4MoveRenameOperation() {
    }

    public P4MoveRenameOperation(@Nullable String changeList, String oldPath, String newPath) {
        super(changeList);
        this.oldPath = oldPath;
        this.newPath = newPath;
        if (LOG.isDebugEnabled()) {
            LOG.debug("P4MoveRenameOperation.P4MoveRenameOperation " + Thread.currentThread());
            LOG.debug("changeList = [" + changeList + "], oldPath = [" + oldPath + "], newPath = [" + newPath + "]");
        }
    }

    @Override
    public void execute(Project project, ProcessingContext context) throws VcsException {
        PerforceRunner runner = PerforceRunner.getInstance(project);
        if (useMove(project, runner, context)) {
            return;
        }

        useIntegrate(project, oldPath, newPath, runner, context);
    }

    private boolean useMove(Project project, PerforceRunner runner, ProcessingContext context) throws VcsException {
        P4File oldP4File = P4File.createInefficientFromLocalPath(oldPath);
        P4File newP4File = P4File.createInefficientFromLocalPath(newPath);
        P4Connection oldConnection = PerforceConnectionManager.getInstance(project).getConnectionForFile(oldP4File);
        P4Connection newConnection = PerforceConnectionManager.getInstance(project).getConnectionForFile(newP4File);

        if (oldConnection == null || newConnection == null || !isCompatibleConnection(oldConnection, newConnection)
            || runner.getClient(newConnection) == null) {
            return false;
        }

        FStat oldFStat = oldP4File.getFstat(project, false);
        if (oldFStat.status == FStat.STATUS_NOT_ADDED) {
            return false;
        }

        ClientVersion clientVersion = PerforceManager.getInstance(project).getClientVersion();
        ServerVersion serverVersion = PerforceManager.getInstance(project).getServerVersion(newConnection);

        if (clientVersion != null && clientVersion.supportsMove() && serverVersion != null && serverVersion.supportsMove()) {
            try {
                long changeList = getPerforceChangeList(project, oldP4File, context);
                if (!oldFStat.isOpenedOrAdded()) {
                    runner.edit(oldP4File, changeList, true);
                }
                runner.move(oldP4File, newP4File, oldConnection, true, changeList);

                File oldFile = oldP4File.getLocalFile();
                if (oldFile.exists() && !isCaseChange(oldP4File, newP4File) && !oldFile.delete()) {
                    throw new VcsException(PerforceLocalize.exceptionTextCannotDeleteLocalFile(oldFile));
                }

                return true;
            }
            finally {
                clearCachesAfterMove(project, oldP4File, newP4File, new RefreshForVcs());
            }
        }

        return false;
    }

    private void useIntegrate(Project project, String fromPath, String toPath, PerforceRunner runner, ProcessingContext context)
        throws VcsException {
        String oldPath = fromPath;
        P4File oldP4File = P4File.createInefficientFromLocalPath(oldPath);
        P4File newP4File = P4File.createInefficientFromLocalPath(toPath);
        P4Connection oldConnection = PerforceConnectionManager.getInstance(project).getConnectionForFile(oldP4File);
        P4Connection newConnection = PerforceConnectionManager.getInstance(project).getConnectionForFile(newP4File);

        if (newConnection == null || runner.getClient(newConnection) == null || oldConnection == null) {
            return;
        }

        RefreshForVcs refreshWorker = new RefreshForVcs();
        try {
            FStat oldfstat = oldP4File.getFstat(project, false);
            if (oldfstat.status == FStat.STATUS_NOT_ADDED) {
                return;
            }

            boolean moveWasUsed = (oldfstat.local == FStat.LOCAL_MOVE_ADDING);
            if (moveWasUsed) {
                if (oldfstat.movedFile == null) {
                    throw new VcsException(PerforceLocalize.errorCanNotFindMovedDeletedFile(oldPath));
                }

                long changeList = getPerforceChangeList(project, oldP4File, context);
                runner.revert(oldP4File, true);

                refreshWorker.addDeletedFile(new File(oldPath));

                oldP4File.clearCache();
                oldP4File.invalidateFstat();

                P4WhereResult p4WhereResult = runner.where(oldfstat.movedFile, oldConnection);
                oldPath = p4WhereResult.getLocal();
                oldP4File = P4File.createInefficientFromLocalPath(oldPath);
                runner.edit(oldP4File, changeList);
                oldfstat = oldP4File.getFstat(project, false);

                // if moving back -> that's all
                if (new File(oldPath).equals(new File(toPath))) {
                    return;
                }
            }

            moveUsingBranchCommand(project, oldP4File, newP4File, oldConnection, newConnection, runner, oldfstat, context);
        }
        finally {
            clearCachesAfterMove(project, oldP4File, newP4File, refreshWorker);
        }
    }

    private static void clearCachesAfterMove(Project project, P4File oldP4File, P4File newP4File, RefreshForVcs refreshWorker) {
        oldP4File.clearCache();
        newP4File.clearCache();
        refreshWorker.addDeletedFile(oldP4File.getLocalFile());
        refreshWorker.refreshFile(newP4File.getLocalFile());
        refreshWorker.run(project);
    }

    private void moveUsingBranchCommand(Project project, P4File oldP4File, P4File newP4File, P4Connection oldConnection,
                                        P4Connection newConnection,
                                        PerforceRunner runner, FStat oldfstat, ProcessingContext context) throws VcsException {
        if (oldfstat.local == FStat.LOCAL_DELETING || oldfstat.local == FStat.LOCAL_MOVE_DELETING) {
            return; //it's already a state screwed by moving a file onto a deleted file, let's not make it worse
        }

        boolean fileWasLocallyAdded = oldfstat.local == FStat.LOCAL_ADDING;
        boolean processedDoubleRename = false;
        if (fileWasLocallyAdded) {
            List<ResolvedFile> resolvedFiles = runner.getResolvedFiles(oldConnection, Collections.emptyList());
            for (ResolvedFile resolvedFile : resolvedFiles) {
                String resolvedFilePath = FileUtil.toSystemIndependentName(resolvedFile.getLocalFile().toString());
                if (resolvedFilePath.equals(oldPath)) {
                    PerforceClient perforceClient = PerforceManager.getInstance(project).getClient(oldConnection);
                    File realOldFile = PerforceManager.getFileByDepotName(resolvedFile.getDepotPath(), perforceClient);
                    assert realOldFile != null;
                    P4File realOldP4File = P4File.create(realOldFile);
                    FStat realOldFStat = realOldP4File.getFstat(project, true);
                    if (realOldFStat.local == FStat.LOCAL_DELETING) {
                        if (new File(newPath).equals(realOldFile)) {
                            processCycle(project, newP4File, oldP4File);
                        }
                        else {
                            processDoubleRename(project, realOldP4File, newP4File, oldP4File);
                        }
                        processedDoubleRename = true;
                    }
                    break;
                }
            }
        }

        if (!processedDoubleRename) {
            boolean fileWasUnderPerforce = oldfstat.status != FStat.STATUS_NOT_ADDED;
            if (fileWasLocallyAdded || !fileWasUnderPerforce || !isCompatibleConnection(oldConnection, newConnection)) {
                if (fileWasLocallyAdded) {
                    assureNoFile(project, oldP4File, true);
                }
                else {
                    runner.assureDel(oldP4File, null);
                }

                createNewFile(project, newP4File);
            }
            else {
                int type = detectType(oldfstat, oldP4File, newPath);
                switch (type) {
                    case CYCLE -> processCycle(project, newP4File, oldP4File);
                    case DOUBLE_RENAME -> processDoubleRename(project, Objects.requireNonNull(oldfstat.fromFile), newP4File, oldP4File);
                    case SIMPLE -> processRename(project, newP4File, oldP4File, context);
                }
            }
        }
    }

    private static int detectType(FStat oldfstat, P4File oldP4File, String newPath) {
        boolean doubleRename = false;
        P4File realOldP4File = null;
        if (oldfstat.local == FStat.LOCAL_BRANCHING) {
            realOldP4File = oldfstat.fromFile;
            doubleRename = true;
        }
        if (realOldP4File == null) {
            realOldP4File = oldP4File;
            doubleRename = false;
        }

        if (doubleRename) {
            if (FileUtil.filesEqual(new File(newPath), realOldP4File.getLocalFile())) {
                return CYCLE;
            }
            else {
                return DOUBLE_RENAME;
            }

        }
        else {
            return SIMPLE;
        }

    }

    private static void createNewFile(Project project, P4File p4File) throws VcsException {
        PerforceRunner runner = PerforceRunner.getInstance(project);
        FStat fstat = p4File.getFstat(project, false);
        if (fstat.status == FStat.STATUS_NOT_IN_CLIENTSPEC) {
            // this is not OK, that's what we DON'T want
            throw new VcsException(PerforceLocalize.exceptionTextCannotAddFileNotUnderAnySpec(p4File));
        }
        else if (fstat.status == FStat.STATUS_NOT_ADDED || fstat.status == FStat.STATUS_DELETED) {
            runner.add(p4File);
        }
        else if (fstat.status == FStat.STATUS_ONLY_LOCAL) {
            // I hope this means it is being added
        }
        else {
            // here it EXISTS on server
            if (fstat.local == FStat.LOCAL_DELETING) {
                new ActionWithTempFile(p4File.getLocalFile()) {
                    @Override
                    protected void executeInternal() throws VcsException {
                        runner.revert(p4File, false);
                        runner.edit(p4File);
                    }
                }.execute();
            }
            else if (fstat.local == FStat.LOCAL_CHECKED_IN) {
                new ActionWithTempFile(p4File.getLocalFile()) {
                    @Override
                    protected void executeInternal() throws VcsException {
                        runner.sync(p4File, false);
                        runner.edit(p4File);
                    }
                }.execute();

            }
            else {
                // we hope this means the file is being edited, added, etc.
            }
        }
    }

    private void processRename(Project project, P4File newP4File, P4File oldP4File, ProcessingContext context) throws VcsException {
        PerforceRunner runner = PerforceRunner.getInstance(project);

        long changeList = getPerforceChangeList(project, newP4File, context);
        new ActionWithTempFile(newP4File.getLocalFile()) {
            @Override
            protected void executeInternal() throws VcsException {
                assureNoFile(project, newP4File, false);

                runner.integrate(oldP4File, newP4File, changeList);
                runner.edit(newP4File, changeList);
                // todo ??? - check why is it here -> test offline mode
                if (!isCaseChange(oldP4File, newP4File)) {
                    runner.assureDel(oldP4File, changeList);
                }
            }
        }.execute();
    }

    private static boolean isCaseChange(P4File oldP4File, P4File newP4File) {
        return Comparing.equal(oldP4File.getLocalPath(), newP4File.getLocalPath(), oldP4File.isCaseSensitive());
    }

    private static void processDoubleRename(Project project, P4File realOldP4File, P4File newP4File, P4File oldP4File) throws VcsException {
        PerforceRunner runner = PerforceRunner.getInstance(project);
        FStat realOldfstat = realOldP4File.getFstat(project, false);
        if (realOldfstat.status == FStat.STATUS_DELETED) {
            throw new VcsException(PerforceLocalize.exceptionTextCannotMoveOriginalDeleted());
        }
        new ActionWithTempFile(newP4File.getLocalFile()) {
            @Override
            protected void executeInternal() throws VcsException {
                assureNoFile(project, newP4File, false);
                runner.revert(oldP4File, true);
                runner.revert(realOldP4File, true);
                runner.integrate(realOldP4File, newP4File);
                runner.edit(newP4File);
            }
        }.execute();
        runner.assureDel(oldP4File, null);
        runner.assureDel(realOldP4File, null);
    }

    private static void processCycle(Project project, P4File newP4File, P4File oldP4File) throws VcsException {
        PerforceRunner runner = PerforceRunner.getInstance(project);
        FStat newfstat = newP4File.getFstat(project, true);
        if (newfstat.local == FStat.LOCAL_DELETING) {
            new ActionWithTempFile(newP4File.getLocalFile()) {
                @Override
                protected void executeInternal() throws VcsException {
                    runner.revert(newP4File, false);
                    runner.edit(newP4File);
                }
            }.execute();
        }
        else if (newfstat.status == FStat.STATUS_NOT_ADDED || newfstat.status == FStat.STATUS_ONLY_LOCAL) {
            createNewFile(project, newP4File);
        }
        else {
            throw new VcsException(PerforceLocalize.exceptionTextCannotRename());
        }
        runner.assureDel(oldP4File, null);
    }

    private static void assureNoFile(Project project, P4File p4File, boolean canBeEdit) throws VcsException {
        PerforceRunner runner = PerforceRunner.getInstance(project);
        FStat fstat = p4File.getFstat(project, false);
        if (fstat.status == FStat.STATUS_NOT_ADDED || fstat.status == FStat.STATUS_NOT_IN_CLIENTSPEC
            || fstat.status == FStat.STATUS_DELETED) {
            // this is OK, that's what we want
        }
        else {
            if (fstat.status == FStat.STATUS_ONLY_LOCAL) {
                runner.revert(p4File, false);
            }
            else if (canBeEdit) {
                runner.revert(p4File, true);
                runner.edit(p4File);
            }
            else {
                throw new VcsException(PerforceLocalize.exceptionTextCannotAssureNoFileBeingOnServer(p4File.getLocalPath()));
            }
        }
    }

    @Override
    public Change getChange(Project project, ChangeListManagerGate addGate) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("getChange");
            LOG.debug("oldPath = " + oldPath);
            LOG.debug("newPath = " + newPath);
        }

        VcsContextFactory factory = VcsContextFactory.getInstance();
        FilePath beforePath = factory.createFilePathOn(new File(oldPath), false);
        FilePath afterPath = factory.createFilePathOn(new File(newPath), false);
        ContentRevision beforeRevision = PerforceCachingContentRevision.createOffline(project, beforePath, afterPath);
        ContentRevision afterRevision = CurrentContentRevision.create(afterPath);
        return new Change(beforeRevision, afterRevision);
    }

    @Override
    String getInputPath() {
        return oldPath;
    }

    @Override
    String getOutputPath() {
        return newPath;
    }

    @Override
    public @Nullable VcsOperation checkMerge(VcsOperation oldOp) {
        if (oldOp instanceof P4AddOperation) {
            String oldPath = ((P4AddOperation) oldOp).getPath();
            if (FileUtil.pathsEqual(oldPath, this.oldPath)) {
                return new P4AddOperation(myChangeList, newPath);
            }
        }
        else if (oldOp instanceof P4MoveRenameOperation moveOp) {
            if (FileUtil.pathsEqual(moveOp.newPath, oldPath)) {
                if (FileUtil.pathsEqual(moveOp.oldPath, newPath)) {
                    return new P4EditOperation(myChangeList, newPath);
                }
                return new P4MoveRenameOperation(myChangeList, moveOp.oldPath, newPath);
            }
        }
        else if (oldOp instanceof P4EditOperation) {
            String oldPath = ((P4EditOperation) oldOp).getPath();
            if (FileUtil.pathsEqual(oldPath, this.oldPath)) {
                return this;
            }
        }
        return super.checkMerge(oldOp);
    }

    static boolean isCompatibleConnection(@Nullable P4Connection oldConnection, @Nullable P4Connection newConnection)
        throws VcsException {
        return oldConnection != null && newConnection != null &&
                      oldConnection.getConnectionKey().equals(newConnection.getConnectionKey());
    }

    @Override
    public List<String> getAffectedPaths() {
        return Arrays.asList(oldPath, newPath);
    }

    @Override
    public String toString() {
        return "P4MoveRenameOperation{" +
                      "oldPath='" + oldPath + '\'' +
                      ", newPath='" + newPath + '\'' +
                      '}';
    }
}

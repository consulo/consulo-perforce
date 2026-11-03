// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.operations;

import consulo.application.Application;
import consulo.language.util.ProcessingContext;
import consulo.logging.Logger;
import consulo.perforce.localize.PerforceLocalize;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.SimpleReference;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.change.Change;
import consulo.versionControlSystem.change.ChangeListManagerGate;
import consulo.versionControlSystem.change.ContentRevision;
import consulo.versionControlSystem.change.CurrentContentRevision;
import consulo.versionControlSystem.change.VcsDirtyScopeManager;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.status.FileStatus;
import org.jetbrains.idea.perforce.application.PerforceVcs;
import org.jetbrains.idea.perforce.perforce.FStat;
import org.jetbrains.idea.perforce.perforce.P4File;
import org.jetbrains.idea.perforce.perforce.PerforceCachingContentRevision;
import org.jetbrains.idea.perforce.perforce.PerforceRunner;
import org.jspecify.annotations.Nullable;

public class P4EditOperation extends VcsOperationOnPath {
    private static final Logger LOG = Logger.getInstance(P4EditOperation.class);
    private static final String CANNOT_FIND_ERROR = "the system cannot find";
    private static final String CANNOT_FIND_ERROR_2 = "no such file or directory";
    private boolean mySuppressErrors;

    private boolean myNeedPathRefresh;

    @SuppressWarnings("unused") // used by deserialization reflection
    public P4EditOperation() {
        myNeedPathRefresh = true;
    }

    public P4EditOperation(String changeList, VirtualFile file) {
        this(changeList, file, true);
    }

    public P4EditOperation(String changeList, VirtualFile file, boolean needPathRefresh) {
        super(changeList, file.getPath());
        myNeedPathRefresh = needPathRefresh;
    }

    public P4EditOperation(@Nullable String changeList, String path) {
        super(changeList, path);
    }

    @SuppressWarnings("unused") // used by deserialization reflection
    public boolean isSuppressErrors() {
        return mySuppressErrors;
    }

    public void setSuppressErrors(boolean suppressErrors) {
        mySuppressErrors = suppressErrors;
    }

    @Override
    public void execute(Project project, ProcessingContext context) throws VcsException {
        SimpleReference<PerforceVcs> vcsRef = new SimpleReference<>();
        SimpleReference<PerforceRunner> runnerRef = new SimpleReference<>();
        Application.get().runReadAction(() -> {
            if (project.isDisposed()) {
                return;
            }
            vcsRef.set(PerforceVcs.getInstance(project));
            runnerRef.set(PerforceRunner.getInstance(project));
        });

        PerforceVcs vcs = vcsRef.get();
        PerforceRunner runner = runnerRef.get();
        if (vcs == null || runner == null) {
            return;
        }

        try {
            P4File p4File = P4File.createInefficientFromLocalPath(myPath);
            FStat p4FStat = p4File.getFstat(project, true);

            if (p4FStat == null) {
                return;
            }
            if ((p4FStat.status == FStat.STATUS_NOT_ADDED || p4FStat.status == FStat.STATUS_ONLY_LOCAL) &&
                p4FStat.local != FStat.LOCAL_BRANCHING) {
                throw new VcsException(PerforceLocalize.confirmationTextAutoEditFileNotRegisteredOnServer(p4File.getLocalPath()));
            }
            else if (p4FStat.status == FStat.STATUS_DELETED) {
                throw new VcsException(PerforceLocalize.exceptionTextFileDeletedFromServerCannotEdit(p4File.getLocalPath()));
            }
            else if (p4FStat.local == FStat.LOCAL_MOVE_DELETING) {
                return; // edit already done by rename/move handlers
            }
            else if (p4FStat.local != FStat.LOCAL_CHECKED_IN && p4FStat.local != FStat.LOCAL_INTEGRATING &&
                p4FStat.local != FStat.LOCAL_BRANCHING) {
                throw new VcsException(PerforceLocalize.exceptionTextFileShouldNotBeReadonlyCannotEdit(p4File.getLocalPath()));
            }

            long changeListNumber = getPerforceChangeList(project, p4File, context);
            runner.edit(p4File, changeListNumber);
        }
        catch (VcsException e) {
            if (mySuppressErrors) {
                // in allwrite workspace, 'p4 edit' is executed when modifying a file that's unchanged according to IDE status.
                // "not changed" file status might've been outdated, so the file might be not available for 'p4 edit'. Not a big deal.
                LOG.debug(e);
            }
            else {
                // check if file was deleted while we were waiting to perform background edit
                String message = StringUtil.toLowerCase(StringUtil.notNullize(e.getMessage()));
                if (!message.contains(CANNOT_FIND_ERROR) && !message.contains(CANNOT_FIND_ERROR_2)) {
                    throw e;
                }
            }
        }
        finally {
            if (myNeedPathRefresh) {
                VirtualFile vFile = getFilePath().getVirtualFile();
                if (vFile != null) {
                    vFile.refresh(true, false);
                    vcs.asyncEditCompleted(vFile);
                }
            }
        }
        if (myNeedPathRefresh) {
            FilePath filePath = getFilePath();
            VcsDirtyScopeManager.getInstance(project).fileDirty(filePath);
        }
    }

    @Override
    public Change getChange(Project project, ChangeListManagerGate addGate) {
        FilePath path = getFilePath();
        FileStatus status = addGate.getStatus(path);
        ContentRevision beforeRevision =
            FileStatus.ADDED.equals(status) ? null : PerforceCachingContentRevision.createOffline(project, path, path);
        ContentRevision afterRevision = CurrentContentRevision.create(path);
        return new Change(beforeRevision, afterRevision);
    }
}

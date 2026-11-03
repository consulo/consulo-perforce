// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.operations;

import consulo.language.util.ProcessingContext;
import consulo.project.Project;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.change.Change;
import consulo.versionControlSystem.change.ChangeListManagerGate;
import consulo.versionControlSystem.change.CurrentContentRevision;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.idea.perforce.application.PerforceChangeProvider;
import org.jetbrains.idea.perforce.application.PerforceVcs;
import org.jetbrains.idea.perforce.perforce.FStat;
import org.jetbrains.idea.perforce.perforce.P4File;
import org.jetbrains.idea.perforce.perforce.PerforceRunner;
import org.jspecify.annotations.Nullable;

public class P4AddOperation extends VcsOperationOnPath {
    @SuppressWarnings("unused") // used by deserialization reflection
    public P4AddOperation() {
    }

    public P4AddOperation(@Nullable String changeList, String path) {
        super(changeList, path);
    }

    public P4AddOperation(String changeList, VirtualFile file) {
        super(changeList, file.getPath());
    }

    @Override
    public void execute(Project project, ProcessingContext context) throws VcsException {
        P4File p4File = P4File.createInefficientFromLocalPath(myPath);

        // check whether it will be under any clientspec
        FStat p4FStat = p4File.getFstat(project, true);
        if (p4FStat.status == FStat.STATUS_NOT_IN_CLIENTSPEC ||
            p4FStat.status == FStat.STATUS_UNKNOWN) {
            return;
        }
        // already being added or edited or something
        if (p4FStat.local == FStat.LOCAL_ADDING ||
            p4FStat.local == FStat.LOCAL_BRANCHING ||
            p4FStat.local == FStat.LOCAL_CHECKED_OUT ||
            p4FStat.local == FStat.LOCAL_INTEGRATING ||
            p4FStat.local == FStat.LOCAL_MOVE_ADDING) {
            return;
        }

        long changeListNumber = getPerforceChangeList(project, p4File, context);
        PerforceRunner runner = PerforceRunner.getInstance(project);
        if (p4FStat.local == FStat.Local.DELETING || p4FStat.local == FStat.Local.MOVE_DELETING) {
            runner.revert(p4File, true);
            runner.edit(p4File);
        }
        else {
            runner.add(p4File, changeListNumber);
        }

        FilePath path = getFilePath();
        ((PerforceChangeProvider) PerforceVcs.getInstance(project).getChangeProvider()).clearUnversionedStatus(path);

        p4File.clearCache();
        markFileAsDirty(project, myPath);
    }

    @Override
    public void prepareOffline(Project project) {
        markFileAsDirty(project, myPath);
    }

    @Override
    public Change getChange(Project project, ChangeListManagerGate addGate) {
        return new Change(null, CurrentContentRevision.create(getFilePath()));
    }

    @Override
    public @Nullable VcsOperation checkMerge(VcsOperation oldOp) {
        if (oldOp instanceof P4DeleteOperation deleteOp && deleteOp.getPath().equals(myPath)) {
            return new P4EditOperation(myChangeList, myPath);
        }

        return super.checkMerge(oldOp);
    }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.operations;

import consulo.language.util.ProcessingContext;
import consulo.project.Project;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.change.Change;
import consulo.versionControlSystem.change.ChangesUtil;
import consulo.versionControlSystem.change.VcsDirtyScopeManager;
import org.jetbrains.idea.perforce.perforce.P4File;
import org.jetbrains.idea.perforce.perforce.PerforceRunner;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.Map;

public class P4MoveToChangeListOperation extends VcsOperationOnPath {
    @SuppressWarnings("unused") // used by deserialization reflection
    public P4MoveToChangeListOperation() {
    }

    public P4MoveToChangeListOperation(Change c, String changeList) {
        super(changeList, ChangesUtil.getFilePath(c).getPath());
    }

    @Override
    public void execute(Project project, ProcessingContext context) throws VcsException {
        File f = new File(myPath);
        long changeListNumber = getPerforceChangeList(project, P4File.createInefficientFromLocalPath(myPath), context);
        PerforceRunner.getInstance(project).reopen(new File[]{f}, changeListNumber);
        VcsDirtyScopeManager.getInstance(project).fileDirty(getFilePath());
    }

    @Override
    public void fillReopenedPaths(Map<String, @Nullable String> result) {
        result.put(myPath, myChangeList);
    }

    @Override
    public @Nullable VcsOperation checkMerge(VcsOperation oldOp) {
        if ((oldOp instanceof VcsOperationOnPath opOnPath && opOnPath.getPath().equals(myPath)) ||
            (oldOp instanceof P4MoveRenameOperation renameOp && renameOp.newPath.equals(myPath))) {
            VcsOperation clone = (VcsOperation) oldOp.clone();
            clone.setChangeList(myChangeList);
            return clone;
        }
        return super.checkMerge(oldOp);
    }
}

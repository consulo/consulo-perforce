// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.operations;

import consulo.language.util.ProcessingContext;
import consulo.project.Project;
import consulo.util.io.FileUtil;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.action.VcsContextFactory;
import consulo.versionControlSystem.change.Change;
import consulo.versionControlSystem.change.ChangeListManagerGate;
import consulo.versionControlSystem.change.ContentRevision;
import org.jetbrains.idea.perforce.perforce.P4File;
import org.jetbrains.idea.perforce.perforce.PerforceCachingContentRevision;
import org.jetbrains.idea.perforce.perforce.PerforceRunner;
import org.jspecify.annotations.Nullable;

import java.io.File;

public class P4DeleteOperation extends VcsOperationOnPath {
    @SuppressWarnings("unused") // used by deserialization reflection
    public P4DeleteOperation() {
    }

    public P4DeleteOperation(String changeList, FilePath item) {
        super(changeList, FileUtil.toSystemIndependentName(item.getPath()));
    }

    @Override
    public void execute(Project project, ProcessingContext context) throws VcsException {
        P4File p4File = P4File.createInefficientFromLocalPath(myPath);
        long list = getPerforceChangeList(project, p4File, context);
        PerforceRunner.getInstance(project).assureDel(p4File, list);
        markFileAsDirty(project, myPath);
    }

    @Override
    public Change getChange(Project project, ChangeListManagerGate addGate) {
        FilePath path = VcsContextFactory.getInstance().createFilePathOn(new File(myPath));
        ContentRevision beforeRevision = PerforceCachingContentRevision.createOffline(project, path, path);
        return new Change(beforeRevision, null);
    }

    @Override
    public @Nullable VcsOperation checkMerge(VcsOperation oldOp) {
        if (oldOp instanceof VcsOperationOnPath opOnPath) {
            boolean pathsEqual = FileUtil.pathsEqual(opOnPath.getPath(), myPath);
            if (oldOp instanceof P4AddOperation && pathsEqual) {
                return null;
            }
            if (oldOp instanceof P4EditOperation && pathsEqual) {
                return this;
            }
        }
        return super.checkMerge(oldOp);
    }
}

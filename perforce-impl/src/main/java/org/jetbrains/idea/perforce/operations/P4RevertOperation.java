// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.operations;

import consulo.language.util.ProcessingContext;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.SmartList;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.change.Change;
import consulo.versionControlSystem.change.ChangesUtil;
import org.jetbrains.idea.perforce.perforce.P4File;
import org.jetbrains.idea.perforce.perforce.PerforceRunner;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class P4RevertOperation extends VcsOperation {
    public @Nullable String beforePath;
    public @Nullable String afterPath;

    @SuppressWarnings("unused") // used by deserialization reflection
    public P4RevertOperation() {
    }

    @Override
    String getInputPath() {
        return StringUtil.notNullize(afterPath);
    }

    @Override
    String getOutputPath() {
        return StringUtil.notNullize(beforePath);
    }

    public P4RevertOperation(Change c) {
        FilePath beforeFilePath = ChangesUtil.getBeforePath(c);
        FilePath afterFilePath = ChangesUtil.getAfterPath(c);
        beforePath = (beforeFilePath == null) ? null : beforeFilePath.getPath();
        afterPath = (afterFilePath == null) ? null : afterFilePath.getPath();
    }

    @Override
    public void execute(Project project, ProcessingContext context) throws VcsException {
        List<String> toRevert = new ArrayList<>();
        List<File> toDelete = new ArrayList<>();
        prepareRevert(toRevert, toDelete);
        for (String path : toRevert) {
            PerforceRunner.getInstance(project).revert(P4File.createInefficientFromLocalPath(path), true);
        }
        refreshAfterRevert(toRevert, toDelete, project);
    }

    boolean isRenameOrMove() {
        return beforePath != null && afterPath != null && !FileUtil.pathsEqual(beforePath, afterPath);
    }

    @Override
    public void fillReopenedPaths(Map<String, @Nullable String> result) {
        String before = beforePath;
        String after = afterPath;
        if (before != null) {
            result.put(before, null);
        }
        if (after != null && (before == null || !FileUtil.pathsEqual(before, after))) {
            result.put(after, null);
        }
    }

    @Override
    public @Nullable VcsOperation checkMerge(VcsOperation oldOp) {
        if (!isRenameOrMove()) {
            if (oldOp instanceof P4EditOperation || oldOp instanceof P4AddOperation) {
                VcsOperationOnPath opOnPath = (VcsOperationOnPath) oldOp;
                if (FileUtil.pathsEqual(opOnPath.getPath(), afterPath)) {
                    return null;
                }
            }
        }
        else if (oldOp instanceof P4MoveRenameOperation renameOp) {
            if (FileUtil.pathsEqual(renameOp.newPath, afterPath)) {
                return null;
            }
        }
        return super.checkMerge(oldOp);
    }

    @Override
    public List<String> getAffectedPaths() {
        List<String> result = new SmartList<>();
        ContainerUtil.addIfNotNull(result, beforePath);
        ContainerUtil.addIfNotNull(result, afterPath);
        return result;
    }

    void prepareRevert(List<String> toRevert, List<File> toDelete) {
        String before = beforePath;
        String after = afterPath;
        if (before != null && after != null && !FileUtil.pathsEqual(before, after)) {
            toRevert.add(after);
            toRevert.add(before);
            toDelete.add(new File(after));
        }
        else {
            String path = before != null ? before : after;
            assert path != null;
            toRevert.add(path);
        }
    }

    static void refreshAfterRevert(List<String> toRevert, List<? extends File> toDelete, Project project) {
        RefreshForVcs refreshWorker = new RefreshForVcs();
        for (String file : toRevert) {
            refreshWorker.refreshFile(new File(file));
        }
        for (File file : toDelete) {
            FileUtil.delete(file);
            refreshWorker.addDeletedFile(file);
        }
        refreshWorker.run(project);
    }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.operations;

import consulo.project.Project;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.action.VcsContextFactory;
import consulo.versionControlSystem.change.VcsDirtyScopeManager;

import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.List;

public abstract class VcsOperationOnPath extends VcsOperation {
    protected String myPath = "";

    protected VcsOperationOnPath() {
    }

    protected VcsOperationOnPath(@Nullable String changeList, String path) {
        super(changeList);
        myPath = path;
    }

    public String getPath() {
        return myPath;
    }

    @Override
    String getInputPath() {
        return getPath();
    }

    @Override
    String getOutputPath() {
        return getPath();
    }

    @SuppressWarnings("unused")
    public void setPath(String path) {
        myPath = path;
    }

    protected FilePath getFilePath() {
        return VcsContextFactory.getInstance().createFilePathOn(new File(myPath), false);
    }

    @Override
    public List<String> getAffectedPaths() {
        return Collections.singletonList(myPath);
    }

    public static void markFileAsDirty(Project project, String path) {
        FilePath filePath = VcsContextFactory.getInstance().createFilePathOn(new File(path));
        VcsDirtyScopeManager.getInstance(project).fileDirty(filePath);
    }
}

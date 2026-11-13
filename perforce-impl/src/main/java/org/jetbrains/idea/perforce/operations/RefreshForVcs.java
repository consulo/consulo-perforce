// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.idea.perforce.operations;

import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.versionControlSystem.change.VcsDirtyScopeManager;
import consulo.virtualFileSystem.NewVirtualFile;
import consulo.virtualFileSystem.RefreshQueue;
import consulo.virtualFileSystem.StandardFileSystems;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Refreshes VFS; refreshes VCS statuses as needed
 */
public class RefreshForVcs {
    private final List<File> myFiles = new ArrayList<>();
    private final List<File> myDirs = new ArrayList<>();

    public void refreshFile(File file) {
        myFiles.add(file);
    }

    public void addDeletedFile(File file) {
        // to be refreshed recursively for simplicity
        File parent = file.getParentFile();
        if (parent != null) {
            myDirs.add(parent);
        }
    }

    public void run(Project project) {
        RefreshQueue.getInstance().refreshPaths(false, false, null, ContainerUtil.map(myFiles, File::toPath));
        RefreshQueue.getInstance().refreshPaths(false, false, null, ContainerUtil.map(myDirs, File::toPath));

        List<VirtualFile> vFiles = new ArrayList<>();
        for (File file : myFiles) {
            ContainerUtil.addIfNotNull(vFiles, StandardFileSystems.local().findFileByPath(file.getAbsolutePath()));
        }

        List<VirtualFile> vDirs = new ArrayList<>();
        for (File dir : myDirs) {
            ContainerUtil.addIfNotNull(vDirs, refreshDir(dir));
        }

        VcsDirtyScopeManager.getInstance(project).filesDirty(vFiles, vDirs);
    }

    private static @Nullable VirtualFile refreshDir(@Nullable File dir) {
        if (dir == null) {
            return null;
        }

        VirtualFile vf = StandardFileSystems.local().findFileByPath(dir.getAbsolutePath());
        if (vf == null) {
            return refreshDir(dir.getParentFile());
        }
        ((NewVirtualFile) vf).markDirtyRecursively();
        vf.refresh(false, true);
        return vf;
    }
}

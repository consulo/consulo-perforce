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
package org.jetbrains.idea.perforce.perforce;

import consulo.application.Application;
import consulo.application.ReadAction;
import consulo.project.Project;
import consulo.util.dataholder.Key;
import consulo.platform.Platform;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.ProjectLevelVcsManager;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.change.ChangeListManager;
import consulo.virtualFileSystem.StandardFileSystems;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileVisitor;
import consulo.virtualFileSystem.NewVirtualFile;
import consulo.perforce.localize.PerforceLocalize;
import org.jetbrains.idea.perforce.application.PerforceBaseInfoWorker;
import org.jetbrains.idea.perforce.application.PerforceClientRootsChecker;
import org.jetbrains.idea.perforce.application.PerforceVcs;

import org.jspecify.annotations.Nullable;

import java.io.File;

public final class P4File {

    //
    // constructor data
    //
    private final @Nullable VirtualFile myVFile;
    private final @Nullable VirtualFile myParentDirOfDeleted;
    private final @Nullable String myName;

    private P4File(@Nullable String name, @Nullable VirtualFile vFile, @Nullable VirtualFile parentDirOfDeleted) {
        myParentDirOfDeleted = parentDirOfDeleted;
        myVFile = vFile;
        myName = name;
    }

    //
    // cached values
    //
    private @Nullable FStat myFstat = null;

    private @Nullable String myLocalPath = null;
    private static final String VF_CACHE = "P4_VF_CACHE";
    public static final Key<P4File> KEY = new Key<>(VF_CACHE);

    public static P4File createInefficientFromLocalPath(String localPath) {
        P4File p4File = new P4File(null, null, null);
        p4File.myLocalPath = localPath;
        return p4File;
    }
    public static P4File createInefficientFromLocalPath(String localPath,
                                                                                                                              @Nullable String name,
                                                                                                                              @Nullable VirtualFile vFile,
                                                                                                                              @Nullable VirtualFile parentDirOfDeleted) {
        P4File p4File = new P4File(name, vFile, parentDirOfDeleted);
        p4File.myLocalPath = localPath;
        return p4File;
    }

    public static P4File create(VirtualFile vFile) {
        P4File userData = vFile.getUserData(KEY);
        P4File p4File;
        if (userData != null) {
            p4File = userData;
        }
        else {
            p4File = new P4File(null, vFile, null);
            vFile.putUserData(KEY, p4File);
        }
        return p4File;
    }
    public static P4File create(FilePath filePath) {
        VirtualFile virtualFile = filePath.getVirtualFile();
        if (virtualFile != null) {
            return create(virtualFile);
        }
        else {
            return createInefficientFromLocalPath(filePath.getPath(), filePath.getName(), null, filePath.getVirtualFileParent());
        }
    }
    public static P4File create(File file) {
        VirtualFile virtualFile = findVirtualFile(file);
        if (virtualFile != null) {
            return create(virtualFile);
        }
        else {
            return createInefficientFromLocalPath(file.getAbsolutePath(), file.getName(), null, findVirtualFile(file.getParentFile()));
        }
    }

    private static @Nullable VirtualFile findVirtualFile(@Nullable File file) {
        if (file == null) {
            return null;
        }
        return ReadAction.compute(() -> StandardFileSystems.local().findFileByPath(file.getAbsolutePath()));
    }

    public String getEscapedPath() {
        return escapeWildcards(getLocalPath());
    }

    public String getRecursivePath() {
        String filePath = getEscapedPath();
        if (isDirectory()) {
            return filePath + "/...";
        }
        return filePath;
    }

    public static String escapeWildcards(String path) {
        return path.replace("%", "%25").replace("@", "%40").replace("#", "%23");
    }

    public static String unescapeWildcards(String path) {
        return path.replace("%23", "#").replace("%40", "@").replace("%25", "%");
    }

    public void invalidateFstat() {
        myFstat = null;

        if (myVFile != null && myVFile.isValid()) {
            invalidateFstat(myVFile);
        }
    }

    public static void invalidateFstat(Project project) {
        Application.get().runReadAction(() -> {
            PerforceVcs perforceVcs = PerforceVcs.getInstance(project);
            for (VirtualFile contentRoot : ProjectLevelVcsManager.getInstance(project).getRootsUnderVcsWithoutFiltering(perforceVcs)) {
                invalidateFstat(contentRoot);
            }
        });
    }

    public static void invalidateFstat(VirtualFile file) {
        Application.get().runReadAction(() -> invalidateFStatImpl(file));
    }

    private static void invalidateFStatImpl(VirtualFile file) {
        if (file.getFileSystem() == StandardFileSystems.local()) {
            VirtualFileUtil.visitChildrenRecursively(file, new VirtualFileVisitor<Void>(VirtualFileVisitor.NO_FOLLOW_SYMLINKS) {
                @Override
                public Iterable<VirtualFile> getChildrenIterable(VirtualFile file) {
                    return ((NewVirtualFile)file).getCachedChildren();
                }

                @Override
                public boolean visitFile(VirtualFile file) {
                    file.putUserData(KEY, null);
                    return true;
                }
            });
        }
    }

    public void clearCache() {
        myFstat = null;
        if (myVFile != null) {
            myLocalPath = null;
        }
    }

    public FStat getFstat(Project project, boolean forceNew) throws VcsException {
        ChangeListManager changeListManager = ChangeListManager.getInstance(project);
        PerforceRunner perforceRunner = PerforceRunner.getInstance(project);
        return getFstat(project.getInstance(PerforceBaseInfoWorker.class), changeListManager, perforceRunner, forceNew);
    }

    public FStat getFstat(PerforceBaseInfoWorker baseInfoWorker, ChangeListManager changeListManager, PerforceRunner perforceRunner,
                                                boolean forceNew) throws VcsException {
        long lastValidTime = baseInfoWorker.getLastValidTime();
        FStat fstat = myFstat;
        if (fstat == null || forceNew ||
                fstat.statTime < lastValidTime
                || lastValidTime == -1) {
            if (myVFile != null && changeListManager.isUnversioned(myVFile) && !forceNew) {
                fstat = new FStat();
                fstat.status = FStat.STATUS_NOT_ADDED;
            }
            else {
                // 7-8
                fstat = perforceRunner.getProxy().fstat(this);
            }
            myFstat = fstat;
        }
        return fstat;
    }

    public File getLocalFile() {
        return new File(getLocalPath());
    }

    public String getLocalPath() {
        String localPath = myLocalPath;
        if (localPath == null) {
            localPath = ReadAction.compute(() -> {
                if (myVFile != null) {
                    return myVFile.getPath();
                }
                else if (myParentDirOfDeleted != null) {
                    return myParentDirOfDeleted.getPath() + System.lineSeparator() + myName;
                }
                else {
                    throw new RuntimeException(PerforceLocalize.exceptionTextCannotFigureOutLocalPath().get());
                }
            });
            myLocalPath = localPath;
        }
        return localPath;
    }

    @Override
    public String toString() {
        return "org.jetbrains.idea.perforce.perforce.P4File{'" + getLocalPath() + "'}";
    }

    public boolean isDirectory() {
        if (myVFile != null) {
            return myVFile.isDirectory();
        }
        return PerforceClientRootsChecker.isDirectory(getLocalFile());
    }

    public boolean isCaseSensitive() {
        if (myVFile != null) {
            return myVFile.getFileSystem().isCaseSensitive();
        }
        if (myParentDirOfDeleted != null) {
            return myParentDirOfDeleted.getFileSystem().isCaseSensitive();
        }
        return Platform.current().fs().isCaseSensitive();
    }
}

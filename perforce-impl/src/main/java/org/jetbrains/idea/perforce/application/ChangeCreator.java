// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.perforce.application;

import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.change.Change;
import consulo.versionControlSystem.change.ContentRevision;
import consulo.versionControlSystem.change.CurrentContentRevision;
import consulo.versionControlSystem.util.VcsUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.status.FileStatus;
import org.jetbrains.idea.perforce.perforce.PerforceCachingContentRevision;
import org.jetbrains.idea.perforce.perforce.PerforceContentRevision;
import org.jetbrains.idea.perforce.perforce.ResolvedFile;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class ChangeCreator {
    private final Project myProject;
    final Set<VirtualFile> reportedChanges = new HashSet<>();

    public ChangeCreator(Project project) {
        myProject = project;
    }

    public Change createEditedFileChange(FilePath path, long haveRevision, boolean isResolvedWithConflict) {
        return createChange(
            PerforceCachingContentRevision.create(myProject, path, haveRevision),
            CurrentContentRevision.create(path),
            isResolvedWithConflict ? FileStatus.MERGED_WITH_CONFLICTS : FileStatus.MODIFIED
        );
    }

    public Change createAddedFileChange(FilePath path, boolean isResolvedWithConflict) {
        return createChange(
            null,
            CurrentContentRevision.create(path),
            isResolvedWithConflict ? FileStatus.MERGED_WITH_CONFLICTS : FileStatus.ADDED
        );
    }

    public Change createDeletedFileChange(File file, long haveRevision, boolean isResolvedWithConflict) {
        return createChange(
            PerforceCachingContentRevision.create(myProject, VcsUtil.getFilePath(file, false), haveRevision),
            null,
            isResolvedWithConflict ? FileStatus.MERGED_WITH_CONFLICTS : FileStatus.DELETED
        );
    }

    public Change createRenameChange(P4Connection connection, ResolvedFile resolvedFile, FilePath afterPath) {
        long revision = resolvedFile.getRevision2();
        if (revision < 0) {
            revision = resolvedFile.getRevision1();
        }
        // TODO CACHE IT!!!!
        ContentRevision beforeRevision = PerforceContentRevision.create(myProject, connection, resolvedFile.getDepotPath(), revision, -1);
        ContentRevision afterRevision = CurrentContentRevision.create(afterPath);
        return createChange(beforeRevision, afterRevision, FileStatus.MODIFIED);
    }

    private Change createChange(@Nullable ContentRevision before, @Nullable ContentRevision after, FileStatus fileStatus) {
        Change change = new Change(before, after, fileStatus);
        if (after != null) {
            ContainerUtil.addIfNotNull(reportedChanges, after.getFile().getVirtualFile());
        }
        if (before != null) {
            ContainerUtil.addIfNotNull(reportedChanges, before.getFile().getVirtualFile());
        }
        return change;
    }
}

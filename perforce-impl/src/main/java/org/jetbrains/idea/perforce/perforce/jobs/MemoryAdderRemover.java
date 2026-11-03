// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.jobs;

import consulo.project.Project;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.change.LocalChangeList;
import org.jspecify.annotations.Nullable;

class MemoryAdderRemover implements AdderRemover {
    private final JobsTablePresentation myPresentation;

    MemoryAdderRemover(JobsTablePresentation presentation) {
        myPresentation = presentation;
    }

    @Override
    public @Nullable VcsException add(PerforceJob job, LocalChangeList list, Project project) {
        myPresentation.addJob(job);
        return null;
    }

    @Override
    public @Nullable VcsException remove(PerforceJob job, LocalChangeList list, Project project) {
        myPresentation.removeSelectedJobs();
        return null;
    }
}

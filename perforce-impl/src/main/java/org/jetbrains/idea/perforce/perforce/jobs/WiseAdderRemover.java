// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.jobs;

import consulo.project.Project;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.change.LocalChangeList;
import org.jspecify.annotations.Nullable;

public class WiseAdderRemover implements AdderRemover {
    private final AdderRemover myMemory;
    private final AdderRemover myWriting;

    public WiseAdderRemover(Project project, JobsTablePresentation presentation) {
        myMemory = new MemoryAdderRemover(presentation);
        myWriting = new WritingAdderRemover(new JobsWorker(project), presentation);
    }

    @Override
    public @Nullable VcsException add(PerforceJob job, LocalChangeList list, Project project) {
        if (list.hasDefaultName()) {
            return myMemory.add(job, list, project);
        }
        return myWriting.add(job, list, project);
    }

    @Override
    public @Nullable VcsException remove(PerforceJob job, LocalChangeList list, Project project) {
        if (list.hasDefaultName()) {
            return myMemory.remove(job, list, project);
        }
        return myWriting.remove(job, list, project);
    }
}

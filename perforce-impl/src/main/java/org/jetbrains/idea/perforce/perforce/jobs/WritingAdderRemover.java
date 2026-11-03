// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.jobs;

import consulo.application.progress.ProgressManager;
import consulo.perforce.localize.PerforceLocalize;
import consulo.project.Project;
import consulo.util.lang.ref.SimpleReference;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.change.LocalChangeList;
import org.jspecify.annotations.Nullable;

class WritingAdderRemover implements AdderRemover {
    private final JobsTablePresentation myPresentation;
    private final JobsWorker myWorker;

    WritingAdderRemover(JobsWorker worker, JobsTablePresentation presentation) {
        myWorker = worker;
        myPresentation = presentation;
    }

    @Override
    public @Nullable VcsException add(PerforceJob job, LocalChangeList list, Project project) {
        SimpleReference<VcsException> exceptionRef = SimpleReference.create();
        ProgressManager.getInstance().runProcessWithProgressSynchronously(
            () -> {
                try {
                    myWorker.addJob(job, list);
                    myPresentation.refreshJobs(job);
                }
                catch (VcsException e) {
                    exceptionRef.set(e);
                }
            },
            PerforceLocalize.jobAddingToChangelistProgress(),
            false,
            myWorker.getProject()
        );
        return exceptionRef.get();
    }

    @Override
    public @Nullable VcsException remove(PerforceJob job, LocalChangeList list, Project project) {
        SimpleReference<VcsException> exceptionRef = SimpleReference.create();
        ProgressManager.getInstance().runProcessWithProgressSynchronously(
            () -> {
                try {
                    myWorker.removeJob(job, list);
                    myPresentation.refreshJobs(null);
                }
                catch (VcsException e) {
                    exceptionRef.set(e);
                }
            },
            PerforceLocalize.jobRemovingFromChangelistProgress(),
            false,
            myWorker.getProject()
        );
        return exceptionRef.get();
    }
}

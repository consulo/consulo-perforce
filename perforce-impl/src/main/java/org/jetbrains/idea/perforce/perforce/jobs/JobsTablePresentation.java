// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.jobs;

import consulo.versionControlSystem.VcsException;
import org.jspecify.annotations.Nullable;

public interface JobsTablePresentation {
    void refreshJobs(@Nullable PerforceJob job) throws VcsException;

    void addJob(PerforceJob job);

    void removeSelectedJobs();
}

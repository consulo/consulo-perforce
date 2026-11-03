// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.jobs;

import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.perforce.localize.PerforceLocalize;
import consulo.project.Project;
import consulo.project.util.WaitForProgressToShow;
import consulo.ui.ex.awt.Messages;
import consulo.versionControlSystem.VcsException;

public class ErrorReporter {
    private static final Logger LOG = Logger.getInstance(ErrorReporter.class);

    private final LocalizeValue myOperation;

    public ErrorReporter(LocalizeValue operation) {
        myOperation = operation;
    }

    public void report(Project project, VcsException e) {
        LOG.info(e);
        String message = PerforceLocalize.errorDuring(myOperation, String.valueOf(e.getMessage())).get();
        WaitForProgressToShow.runOrInvokeLaterAboveProgress(
            () -> Messages.showErrorDialog(project, message, PerforceLocalize.jobJobsError().get()),
            null,
            project
        );
    }
}

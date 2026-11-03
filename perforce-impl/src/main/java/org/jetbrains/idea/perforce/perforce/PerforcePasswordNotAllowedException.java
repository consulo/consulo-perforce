// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce;

import consulo.project.Project;
import consulo.project.ui.notification.NotificationType;
import consulo.versionControlSystem.ui.VcsBalloonProblemNotifier;
import consulo.perforce.localize.PerforceLocalize;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jetbrains.idea.perforce.perforce.login.PerforceLoginManager;

public class PerforcePasswordNotAllowedException extends VcsConnectionProblem {
    private final Project myProject;
    private final P4Connection myConnection;

    public PerforcePasswordNotAllowedException(String message, Project project, P4Connection connection) {
        super(message);
        myProject = project;
        myConnection = connection;
    }

    @Override
    public boolean attemptQuickFix(boolean mayDisplayDialogs) {
        PerforceSettings mySettings = PerforceSettings.getSettings(myProject);

        if (!mySettings.USE_LOGIN && mySettings.ENABLED) {
            if (mayDisplayDialogs) {
                return PerforceAuthenticationException
                    .askEnableLogin(PerforceLocalize.confirmationTextPasswordNotAllowedEnableLogin().get(), myProject);
            }
            PerforceLoginManager.getInstance(myProject).ensureNotifyAboutDisabledLogin(myConnection);
        }
        else if (mySettings.USE_LOGIN && mySettings.useP4CONFIG) {
            VcsBalloonProblemNotifier.showOverChangesView(myProject, getMessage(), NotificationType.ERROR);
        }

        return false;
    }
}

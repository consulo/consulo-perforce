// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce;

import consulo.application.Application;
import consulo.application.progress.PerformInBackgroundOption;
import consulo.project.Project;
import consulo.ui.ex.awt.Messages;
import consulo.perforce.localize.PerforceLocalize;
import consulo.platform.base.localize.CommonLocalize;
import org.jetbrains.idea.perforce.actions.MessageManager;
import org.jetbrains.idea.perforce.application.PerforceVcs;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jetbrains.idea.perforce.perforce.connections.PerforceConnectionManager;
import org.jetbrains.idea.perforce.perforce.login.PerforceLoginManager;

public final class PerforceAuthenticationException extends VcsConnectionProblem {
    private final P4Connection myConnection;
    private final Project myProject;

    public PerforceAuthenticationException(String message, P4Connection connection, Project project) {
        super(message);
        myConnection = connection;
        myProject = project;
    }

    public PerforceAuthenticationException(PerforceAuthenticationException cause) {
        super(cause);
        myConnection = cause.myConnection;
        myProject = cause.myProject;
    }

    @Override
    public boolean attemptQuickFix(boolean mayDisplayDialogs) {
        PerforceSettings settings = PerforceSettings.getSettings(myProject);
        if (!settings.USE_LOGIN && String.valueOf(getMessage()).contains(PerforceRunner.PASSWORD_INVALID_MESSAGE)) {
            if (mayDisplayDialogs &&
                    askEnableLogin(PerforceLocalize.loginPasswordInvalidOrUnset().get(), myProject) &&
                    PerforceLoginManager.getInstance(myProject).checkAndRepair(myConnection)) {
                return true;
            }

            PerforceLoginManager.getInstance(myProject).ensureNotifyAboutDisabledLogin(myConnection);

            return false;
        }

        if (mayDisplayDialogs) {
            return PerforceLoginManager.getInstance(myProject).checkAndRepair(myConnection);
        }

        PerforceVcs.getInstance(myProject).runBackgroundTask(
            PerforceLocalize.connectionVerifying().get(), PerformInBackgroundOption.ALWAYS_BACKGROUND,
            () -> {
                try {
                    PerforceLoginManager.getInstance(myProject).check(myConnection, true);
                }
                catch (VcsConnectionProblem e) {
                    PerforceLoginManager.getInstance(myProject).getNotifier().ensureNotify(myConnection, e);
                }
            }, null);

        return false;
    }

    public static boolean askEnableLogin(String msg, Project project) {
        if (Application.get().isUnitTestMode()) {
            return false;
        }

        int dialogResult = MessageManager.showDialog(project, msg, PerforceLocalize.dialogTitlePerforce().get(),
                                                                                                              new String[]{CommonLocalize.buttonYes().get(), CommonLocalize.buttonNo().get(),
                                                                                                                  PerforceLocalize.connectionGoOffline().get()}, 0,
                                                                                                              Messages.getQuestionIcon());
        if (dialogResult == 0) {
            PerforceSettings.getSettings(project).USE_LOGIN = true;
            PerforceConnectionManager.getInstance(project).updateConnections();
            return true;
        }
        if (dialogResult == 2) {
            PerforceSettings.getSettings(project).disable(true);
        }
        return false;
    }
}

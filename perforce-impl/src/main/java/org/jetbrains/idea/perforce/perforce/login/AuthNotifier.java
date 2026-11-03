// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.login;

import consulo.project.ui.notification.Notification;
import consulo.project.ui.notification.NotificationAction;
import consulo.project.ui.notification.NotificationType;
import consulo.project.Project;
import consulo.application.util.HtmlBuilder;
import consulo.application.util.HtmlChunk;
import consulo.localize.LocalizeValue;
import org.jetbrains.idea.perforce.perforce.VcsConnectionProblem;
import org.jetbrains.idea.perforce.util.PerforceGenericNotifier;
import consulo.versionControlSystem.ui.VcsBalloonProblemNotifier;
import consulo.perforce.localize.PerforceLocalize;
import org.jetbrains.idea.perforce.perforce.ConnectionId;
import org.jetbrains.idea.perforce.perforce.PerforceRunner;
import org.jetbrains.idea.perforce.perforce.PerforceSettings;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jetbrains.idea.perforce.perforce.connections.PerforceConnectionProblemsNotifier;
import org.jetbrains.idea.perforce.perforce.connections.PerforceNotificationGroupContributor;

public class AuthNotifier {
    public static final String INSPECT = "inspect";
    public static final String FIX = "fix";
    public static final String RETRY = "retry";
    public static final String OFFLINE = "offline";

    private final Project myProject;
    private final PerforceSettings mySettings;

    private final MyLoginNotifier myLoginNotifier;
    private final MyPasswordNotifier myPasswordNotifier;

    public AuthNotifier(Project project, PerforceLoginManager loginManager, PerforceSettings perforceSettings) {
        myProject = project;
        mySettings = perforceSettings;
        myLoginNotifier = new MyLoginNotifier(myProject, loginManager);
        myPasswordNotifier = new MyPasswordNotifier(myProject, loginManager);
    }

    private static final class MyPasswordNotifier extends PerforceGenericNotifier<P4Connection, String> {
        // all password expiration notifications share a single key, so only one is shown at a time
        private static final String KEY = "password";

        private final PerforceLoginManager myLoginManager;

        public MyPasswordNotifier(Project project, PerforceLoginManager loginManager) {
            super(project, PerforceNotificationGroupContributor.GROUP, PerforceLocalize.loginPasswordExpiredTitle(), NotificationType.ERROR);
            myLoginManager = loginManager;
        }

        @Override
        protected boolean ask(P4Connection obj, String description) {
            if (FIX.equals(description)) {
                return myLoginManager.checkPasswordExpirationAndRepair(obj);
            }
            if (INSPECT.equals(description)) {
                PerforceConnectionProblemsNotifier.showConnectionState(myProject, true);
                return false;
            }
            return false;
        }

        @Override
        protected String getKey(P4Connection obj) {
            return KEY;
        }

        @Override
        protected LocalizeValue getNotificationContent(P4Connection connection) {
            HtmlBuilder builder = new HtmlBuilder();
            builder.append(
                HtmlChunk.raw(PerforceLocalize.loginPasswordExpired(connection.getWorkingDirectory()).get())
            );
            builder.append(HtmlChunk.br());
            builder.append(HtmlChunk.link(FIX, PerforceLocalize.loginFix().get()));
            builder.append(HtmlChunk.br());
            builder.append(HtmlChunk.link(INSPECT, PerforceLocalize.loginInspect().get()));
            return LocalizeValue.of(builder.toString());
        }
    }

    private static final class MyLoginNotifier extends PerforceGenericNotifier<P4Connection, ConnectionId> {
        private final PerforceLoginManager myLoginManager;

        private MyLoginNotifier(Project project, PerforceLoginManager loginManager) {
            super(project, PerforceNotificationGroupContributor.GROUP, PerforceLocalize.loginNotLoggedInTitle(), NotificationType.ERROR);
            myLoginManager = loginManager;
        }

        @Override
        protected LocalizeValue getNotificationContent(P4Connection connection) {
            HtmlBuilder builder = new HtmlBuilder();
            builder.append(
                HtmlChunk.raw(PerforceLocalize.loginErrorNotification(connection.getWorkingDirectory()).get())
            );
            return LocalizeValue.of(builder.toString());
        }

        @Override
        protected void configureNotification(Notification notification, P4Connection connection) {

            NotificationAction fixAction = NotificationAction.create(PerforceLocalize.loginFix(),
                                                                     (e, n) -> {
                                                                         if (myLoginManager.checkAndRepair(connection)) {
                                                                             notification.expire();
                                                                         }
                                                                     });
            notification.addAction(fixAction);
            NotificationAction inspectAction =
              NotificationAction.create(PerforceLocalize.loginInspect(),
                                              (e, n) -> PerforceConnectionProblemsNotifier.showConnectionState(myProject, true));
            notification.addAction(inspectAction);
        }

        @Override
        protected boolean ask(P4Connection obj, String description) {
            return false;
        }

        @Override
        protected ConnectionId getKey(P4Connection obj) {
            return obj.getId();
        }

    }

    public void ensureNotify(P4Connection connection, VcsConnectionProblem exception) {
        if (! (mySettings.ENABLED && mySettings.USE_LOGIN)) {
            return;
        }

        String message = exception.getMessage();
        if (message != null && message.contains(PerforceRunner.PASSWORD_EXPIRED)) {
            myPasswordNotifier.ensureNotify(connection);
        }
        else {
            myLoginNotifier.ensureNotify(connection);
        }
    }

    public void removeLazyNotification(P4Connection connection) {
        myLoginNotifier.removeLazyNotification(connection);
    }

    public void showPasswordWasOk(boolean value) {
        if (value) {
            new VcsBalloonProblemNotifier(myProject, PerforceLocalize.loginSuccessful().get(), NotificationType.INFORMATION).run();
        }
        else {
            new VcsBalloonProblemNotifier(myProject, PerforceLocalize.loginNotLoggedIntoP4().get(), NotificationType.ERROR).run();
        }
    }

    public boolean isEmpty() {
        return myLoginNotifier.isEmpty();
    }

    public void clear() {
        myLoginNotifier.clear();
        myPasswordNotifier.clear();
    }
}

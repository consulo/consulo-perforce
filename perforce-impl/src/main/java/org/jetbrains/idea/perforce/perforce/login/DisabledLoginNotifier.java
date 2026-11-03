// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.login;

import consulo.project.ui.notification.NotificationType;
import consulo.project.Project;
import consulo.localize.LocalizeValue;
import org.jetbrains.idea.perforce.util.PerforceGenericNotifier;
import consulo.perforce.localize.PerforceLocalize;
import org.jetbrains.idea.perforce.perforce.connections.PerforceNotificationGroupContributor;
import org.jetbrains.idea.perforce.perforce.ConnectionId;
import org.jetbrains.idea.perforce.perforce.PerforceSettings;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jetbrains.idea.perforce.perforce.connections.PerforceConnectionManager;
import org.jetbrains.idea.perforce.perforce.connections.PerforceConnectionProblemsNotifier;

class DisabledLoginNotifier extends PerforceGenericNotifier<P4Connection, ConnectionId> {
    private static final String INSPECT = AuthNotifier.INSPECT;
    private static final String FIX = AuthNotifier.FIX;
    private final PerforceLoginManager myLoginManager;

    DisabledLoginNotifier(Project project, PerforceLoginManager loginManager) {
        super(project, PerforceNotificationGroupContributor.GROUP, PerforceLocalize.connectionProblems(), NotificationType.ERROR);
        myLoginManager = loginManager;
    }

    @Override
    protected LocalizeValue getNotificationContent(P4Connection connection) {
        return PerforceLocalize.connectionErrorNotification(connection.getWorkingDirectory(), FIX, INSPECT);
    }

    @Override
    protected boolean ask(P4Connection obj, String description) {
        if (FIX.equals(description)) {
            PerforceSettings.getSettings(myProject).USE_LOGIN = true;
            PerforceConnectionManager.getInstance(myProject).updateConnections();
            return myLoginManager.checkAndRepair(obj);
        }
        if (INSPECT.equals(description)) {
            PerforceConnectionProblemsNotifier.showConnectionState(myProject, true);
            return false;
        }
        return false;
    }

    @Override
    protected ConnectionId getKey(P4Connection obj) {
        return obj.getId();
    }

}

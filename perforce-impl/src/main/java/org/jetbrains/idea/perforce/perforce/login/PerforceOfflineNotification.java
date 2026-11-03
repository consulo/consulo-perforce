// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.login;

import consulo.project.ui.notification.NotificationType;
import consulo.project.Project;
import consulo.localize.LocalizeValue;
import org.jetbrains.idea.perforce.util.PerforceGenericNotifier;
import consulo.perforce.localize.PerforceLocalize;
import org.jetbrains.idea.perforce.perforce.connections.PerforceNotificationGroupContributor;
import org.jetbrains.idea.perforce.perforce.PerforceSettings;

public class PerforceOfflineNotification extends PerforceGenericNotifier<Object, Object> {

    public PerforceOfflineNotification(Project project) {
        super(project, PerforceNotificationGroupContributor.GROUP, PerforceLocalize.connectionOffline(), NotificationType.WARNING);
    }

    @Override
    protected boolean ask(Object obj, String description) {
        PerforceSettings settings = PerforceSettings.getSettings(myProject);
        if (settings.ENABLED) {
            return true;
        }
        settings.enable();
        return settings.ENABLED;
    }

    @Override
    protected Object getKey(Object obj) {
        return obj;
    }

    @Override
    protected LocalizeValue getNotificationContent(Object obj) {
        return PerforceLocalize.connectionWorkOffline();
    }
}

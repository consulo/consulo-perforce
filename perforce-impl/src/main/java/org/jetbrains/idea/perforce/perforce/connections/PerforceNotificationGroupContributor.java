/*
 * Copyright 2013-2026 consulo.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.perforce.perforce.connections;

import consulo.annotation.component.ExtensionImpl;
import consulo.perforce.localize.PerforceLocalize;
import consulo.project.ui.notification.NotificationGroup;
import consulo.project.ui.notification.NotificationGroupContributor;

import java.util.function.Consumer;

/**
 * Registers the "Perforce" balloon notification group used by the connection and login notifiers.
 */
@ExtensionImpl
public final class PerforceNotificationGroupContributor implements NotificationGroupContributor {
    public static final NotificationGroup GROUP = NotificationGroup.balloonGroup("Perforce", PerforceLocalize.notificationGroupPerforce());

    @Override
    public void contribute(Consumer<NotificationGroup> registrator) {
        registrator.accept(GROUP);
    }
}

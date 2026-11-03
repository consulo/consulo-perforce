// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.connections;

import consulo.annotation.component.ExtensionImpl;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import org.jetbrains.idea.perforce.perforce.PerforceSettings;

import java.util.Collections;
import java.util.Set;

/**
 * Provides the connection parameters explicitly configured in the Perforce settings (when P4CONFIG is not used).
 */
@ExtensionImpl(id = "settings", order = "first")
public final class SettingsConnectionParametersProvider implements P4ConnectionParametersProvider {
    @Override
    public Set<P4ConnectionParameters> getConnectionParameters(Project project) {
        PerforceSettings settings = PerforceSettings.getSettings(project);

        if (!isConnectionSettingsDefined(settings)) {
            return Collections.emptySet();
        }

        P4ConnectionParameters parameters = new P4ConnectionParameters();
        parameters.setClient(settings.client);
        parameters.setServer(settings.port);
        parameters.setUser(settings.user);
        if (!settings.isNoneCharset()) {
            parameters.setCharset(settings.CHARSET);
        }
        return Collections.singleton(parameters);
    }

    private static boolean isConnectionSettingsDefined(PerforceSettings settings) {
        return !settings.useP4CONFIG &&
            (!StringUtil.isEmptyOrSpaces(settings.port) || !StringUtil.isEmptyOrSpaces(settings.client) || !StringUtil.isEmptyOrSpaces(settings.user));
    }
}

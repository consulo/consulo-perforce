// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.connections;

import consulo.annotation.component.ExtensionImpl;
import consulo.project.Project;

import java.util.Collections;
import java.util.Set;

/**
 * Provides the default connection parameters taken from the p4 environment ({@code p4 set}, environment variables).
 */
@ExtensionImpl(id = "env", order = "after settings")
public final class EnvConnectionParametersProvider implements P4ConnectionParametersProvider {
    @Override
    public Set<P4ConnectionParameters> getConnectionParameters(Project project) {
        P4ConnectionParameters defaultParams = P4EnvHelper.getConfigHelper(project).getDefaultParams();

        return defaultParams.hasProblems() ? Collections.emptySet() : Collections.singleton(defaultParams);
    }
}

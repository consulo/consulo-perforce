// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.connections;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.application.Application;
import consulo.project.Project;

import java.util.List;
import java.util.Set;

/**
 * Supplies candidate connection parameters, e.g. to discover the workspace for a newly opened project.
 * Implementations may run p4 commands or read files, so they are called on a background thread.
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface P4ConnectionParametersProvider {
    static List<P4ConnectionParametersProvider> getProviders() {
        return Application.get().getExtensionList(P4ConnectionParametersProvider.class);
    }

    Set<P4ConnectionParameters> getConnectionParameters(Project project);
}

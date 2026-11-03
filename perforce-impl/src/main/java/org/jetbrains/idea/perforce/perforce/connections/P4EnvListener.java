// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.connections;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.TopicAPI;

/**
 * Notified when the effective p4 environment (p4 set / environment variables) of a project changes.
 */
@TopicAPI(ComponentScope.PROJECT)
public interface P4EnvListener {
    void environmentChanged();
}

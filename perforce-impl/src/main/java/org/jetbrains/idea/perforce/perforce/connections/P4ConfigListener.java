// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.connections;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.TopicAPI;
import consulo.annotation.component.TopicBroadcastDirection;

@TopicAPI(value = ComponentScope.PROJECT, direction = TopicBroadcastDirection.NONE)
public interface P4ConfigListener {
    Class<P4ConfigListener> TOPIC = P4ConfigListener.class;

    void notifyConfigChanged(String configPath);
}

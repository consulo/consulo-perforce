// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.connections;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;
import org.jetbrains.idea.perforce.perforce.P4File;

import java.io.File;
import java.util.Map;

@ServiceAPI(ComponentScope.PROJECT)
public interface PerforceConnectionManagerI {
    @Nullable PerforceMultipleConnections getMultipleConnectionObject();
    Map<VirtualFile, P4Connection> getAllConnections();
    @Nullable P4Connection getConnectionForFile(File file);
    @Nullable P4Connection getConnectionForFile(P4File file);
    @Nullable P4Connection getConnectionForFile(VirtualFile file);
    boolean isSingletonConnectionUsed();
    void updateConnections();

    boolean isUnderProjectConnections(File file);
    boolean isInitialized();
}

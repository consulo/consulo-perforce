// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.connections;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.versionControlSystem.FilePath;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;
import org.jetbrains.idea.perforce.perforce.P4File;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Map;

@ServiceAPI(ComponentScope.PROJECT)
public interface PerforceConnectionManagerI {
    @Nullable PerforceMultipleConnections getMultipleConnectionObject();
    Map<VirtualFile, P4Connection> getAllConnections();
    @Nullable P4Connection getConnectionForFile(File file);
    @Nullable P4Connection getConnectionForFile(P4File file);
    @Nullable P4Connection getConnectionForFile(VirtualFile file);

    default @Nullable P4Connection getConnectionForFile(FilePath filePath) {
        Path path = filePath.getNioPath();
        // perforce works with files of this machine only
        if (path == null || path.getFileSystem() != FileSystems.getDefault()) {
            return null;
        }
        return getConnectionForFile(path.toFile());
    }

    boolean isSingletonConnectionUsed();
    void updateConnections();

    boolean isUnderProjectConnections(File file);
    boolean isInitialized();
}

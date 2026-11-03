// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.connections;

import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public interface PerforceConnectionMapper {
    /**
     * @see VirtualFile#isInLocalFileSystem()
     * @param file - local file.
     * @return null in case of no connection exists or non-local VirtualFile
     */
    @Nullable P4Connection getConnection(VirtualFile file);
    Map<VirtualFile, P4Connection> getAllConnections();
}

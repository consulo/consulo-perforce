// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.connections;

import consulo.virtualFileSystem.util.VirtualFileUtil;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class P4ConfigHelper {
    private final Map<File, File> myAlreadyFoundConfigs = new HashMap<>();

    public @Nullable File findDirWithP4ConfigFile(VirtualFile parent, String p4ConfigFileName) {
        File current = VirtualFileUtil.virtualToIoFile(parent);

        List<File> paths = new ArrayList<>();
        while (current != null) {
            File calculated = myAlreadyFoundConfigs.get(current);
            if (calculated != null) {
                return cacheForAllChildren(paths, calculated);
            }

            File candidate = new File(current, p4ConfigFileName);
            if (candidate.exists() && !candidate.isDirectory()) {
                myAlreadyFoundConfigs.put(current, current);
                return cacheForAllChildren(paths, current);
            }

            paths.add(current);
            current = current.getParentFile();
        }
        return null;
    }

    private File cacheForAllChildren(List<File> paths, File calculated) {
        for (File path : paths) {
            myAlreadyFoundConfigs.put(path, calculated);
        }
        return calculated;
    }
}

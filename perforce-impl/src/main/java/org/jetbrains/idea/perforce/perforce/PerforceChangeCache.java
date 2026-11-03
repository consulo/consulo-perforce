// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce;

import consulo.project.Project;
import consulo.util.lang.Pair;
import consulo.util.io.FileUtil;
import consulo.versionControlSystem.VcsException;
import consulo.virtualFileSystem.VirtualFile;
import consulo.util.collection.ContainerUtil;
import org.jspecify.annotations.Nullable;
import org.jetbrains.idea.perforce.application.ConnectionKey;
import org.jetbrains.idea.perforce.application.PerforceManager;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PerforceChangeCache {
    private final PerforceRunner myRunner;
    private final PerforceManager myPerforceManager;
    private final Map<Pair<Long, ConnectionKey>, List<PerforceChange>> myCache = new ConcurrentHashMap<>();

    public PerforceChangeCache(Project project) {
        myRunner = PerforceRunner.getInstance(project);
        myPerforceManager = PerforceManager.getInstance(project);
    }

    public List<PerforceChange> getChanges(P4Connection connection, long changeListNumber, @Nullable VirtualFile vcsRoot) {
        List<PerforceChange> all = myCache.get(createKey(connection, changeListNumber));
        if (all == null) {
            try {
                all = myRunner.getChanges(connection, changeListNumber);
            }
            catch (VcsException e) {
                all = Collections.emptyList();
            }
            setChanges(connection, changeListNumber, all);
        }
        if (vcsRoot == null) {
            return all;
        }
        String rootPath = FileUtil.toSystemDependentName(myPerforceManager.convertP4ParsedPath(null, vcsRoot.getPath()));

        return ContainerUtil.findAll(all, change -> {
            File file = change.getFile();
            return file != null && FileUtil.startsWith(file.getAbsolutePath(), rootPath);
        });
    }

    public void setChanges(P4Connection connection, long changeListNumber, List<PerforceChange> changes) {
        myCache.put(createKey(connection, changeListNumber), changes);
    }

    private static Pair<Long, ConnectionKey> createKey(P4Connection connection, long changeListNumber) {
        return new Pair<>(changeListNumber, connection.getConnectionKey());
    }
}

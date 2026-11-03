// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.perforce.perforce.connections;

import consulo.project.Project;
import consulo.versionControlSystem.ProjectLevelVcsManager;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.idea.perforce.application.PerforceVcs;
import org.jetbrains.idea.perforce.perforce.P4File;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Connection manager used while testing connection settings that are not applied yet.
 */
public final class TestPerforceConnectionManager implements PerforceConnectionManagerI {
    private final Project myProject;
    private final boolean mySingleton;

    private volatile @Nullable P4Connection mySingletonConnection;
    private volatile @Nullable PerforceMultipleConnections myMc;

    public TestPerforceConnectionManager(Project project, boolean singleton) {
        myProject = project;
        mySingleton = singleton;
    }

    public void setSingletonConnection(@Nullable P4Connection connection) {
        mySingletonConnection = connection;
    }

    public void setMultipleConnectionObject(@Nullable PerforceMultipleConnections mc) {
        myMc = mc;
    }

    @Override
    public @Nullable PerforceMultipleConnections getMultipleConnectionObject() {
        return myMc;
    }

    @Override
    public Map<VirtualFile, P4Connection> getAllConnections() {
        if (mySingleton) {
            Map<VirtualFile, P4Connection> result = new LinkedHashMap<>();
            P4Connection connection = Objects.requireNonNull(mySingletonConnection);
            for (VirtualFile root : ProjectLevelVcsManager.getInstance(myProject).getRootsUnderVcs(PerforceVcs.getInstance(myProject))) {
                result.put(root, connection);
            }
            return result;
        }
        return Objects.requireNonNull(myMc).getAllConnections();
    }

    @Override
    public @Nullable P4Connection getConnectionForFile(File file) {
        if (mySingleton) {
            return mySingletonConnection;
        }
        VirtualFile vf = PerforceConnectionManager.findNearestLiveParentFor(file);
        if (vf == null) {
            return null;
        }
        return Objects.requireNonNull(myMc).getConnection(vf);
    }

    @Override
    public @Nullable P4Connection getConnectionForFile(P4File file) {
        return mySingleton ? mySingletonConnection : getConnectionForFile(file.getLocalFile());
    }

    @Override
    public @Nullable P4Connection getConnectionForFile(VirtualFile file) {
        return mySingleton ? mySingletonConnection : Objects.requireNonNull(myMc).getConnection(file);
    }

    @Override
    public boolean isSingletonConnectionUsed() {
        return mySingleton;
    }

    @Override
    public void updateConnections() {
    }

    @Override
    public boolean isUnderProjectConnections(File file) {
        return true;
    }

    @Override
    public boolean isInitialized() {
        return true;
    }
}

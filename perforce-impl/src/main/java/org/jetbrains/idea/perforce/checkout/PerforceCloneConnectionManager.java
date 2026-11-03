// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.perforce.checkout;

import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.idea.perforce.perforce.P4File;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jetbrains.idea.perforce.perforce.connections.PerforceConnectionManagerI;
import org.jetbrains.idea.perforce.perforce.connections.PerforceMultipleConnections;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.Map;

final class PerforceCloneConnectionManager implements PerforceConnectionManagerI {
    private final P4Connection myConnection;

    PerforceCloneConnectionManager(P4Connection connection) {
        myConnection = connection;
    }

    @Override
    public @Nullable PerforceMultipleConnections getMultipleConnectionObject() {
        return null;
    }

    @Override
    public Map<VirtualFile, P4Connection> getAllConnections() {
        return Collections.emptyMap();
    }

    @Override
    public P4Connection getConnectionForFile(File file) {
        return myConnection;
    }

    @Override
    public P4Connection getConnectionForFile(P4File file) {
        return myConnection;
    }

    @Override
    public P4Connection getConnectionForFile(VirtualFile file) {
        return myConnection;
    }

    @Override
    public boolean isSingletonConnectionUsed() {
        return true;
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

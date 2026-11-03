// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.login;

import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;

import java.util.Collections;
import java.util.Set;

public abstract class LoginStateListener {
    private volatile boolean myIsInBatch;
    private final Set<P4Connection> myConnections = ConcurrentHashMap.newKeySet();

    public void reconnected(P4Connection connection) {
        if (myIsInBatch) {
            myConnections.add(connection);
        }
        else {
            notifyListeners(Collections.singleton(connection));
        }
    }

    public void startBatch() {
        myIsInBatch = true;
    }

    public void fireBatchFinished() {
        myIsInBatch = false;
        notifyListeners(myConnections);
        myConnections.clear();
    }

    protected abstract void notifyListeners(Set<P4Connection> connections);
}

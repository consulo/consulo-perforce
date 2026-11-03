// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce;

import consulo.project.Project;
import org.jspecify.annotations.Nullable;
import consulo.versionControlSystem.VcsException;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;

import java.io.IOException;

class PerforceContext {
    private static final int ourVeryLongServerTimeout = 1200 * 1000;
    public final P4Connection connection;
    public final boolean longTimeout;
    public final boolean justLogged;

    PerforceContext(P4Connection connection) {
        this(connection, false, false);
    }

    PerforceContext(P4Connection connection, boolean longTimeout, boolean justLogged) {
        this.connection = connection;
        this.longTimeout = longTimeout;
        this.justLogged = justLogged;
    }

    void runP4Command(PerforceSettings settings, String[] p4args, ExecResult retVal, @Nullable StringBuffer inputStream)
        throws VcsException, PerforceTimeoutException, IOException, InterruptedException {
        connection.runP4Command(longTimeout ? new LongTimeoutProxy(settings) : settings, p4args, retVal, inputStream);
    }

    private static class LongTimeoutProxy implements PerforcePhysicalConnectionParametersI {
        private final PerforcePhysicalConnectionParametersI myDelegate;

        LongTimeoutProxy(PerforcePhysicalConnectionParametersI delegate) {
            myDelegate = delegate;
        }

        @Override
        public int getServerTimeout() {
            return myDelegate.getServerTimeout() <= 0 ? ourVeryLongServerTimeout : Math.max(ourVeryLongServerTimeout, myDelegate.getServerTimeout());
        }

        @Override
        public String getCharsetName() {
            return myDelegate.getCharsetName();
        }

        @Override
        public String getPathToExec() {
            return myDelegate.getPathToExec();
        }

        @Override
        public @Nullable String getPathToIgnore() {
            return myDelegate.getPathToIgnore();
        }

        @Override
        public Project getProject() {
            return myDelegate.getProject();
        }

        @Override
        public void disable() {
            myDelegate.disable();
        }
    }
}

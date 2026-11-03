// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.perforce.checkout;

import org.jetbrains.idea.perforce.application.ConnectionKey;
import org.jetbrains.idea.perforce.perforce.ConnectionId;
import org.jetbrains.idea.perforce.perforce.ExecResult;
import org.jetbrains.idea.perforce.perforce.PerforcePhysicalConnectionParametersI;
import org.jetbrains.idea.perforce.perforce.connections.AbstractP4Connection;
import org.jspecify.annotations.Nullable;

import java.io.File;

/**
 * A connection used while syncing a new workspace: runs p4 in the target directory with explicit connection arguments.
 */
final class PerforceCloneConnection extends AbstractP4Connection {
    private final File myWorkDir;
    private final String[] myConnArgs;
    private final @Nullable String myPassword;
    private final ConnectionId myConnectionId;

    PerforceCloneConnection(File workDir, String[] connArgs, @Nullable String password) {
        myWorkDir = workDir;
        myConnArgs = connArgs;
        myPassword = password;
        myConnectionId = new ConnectionId(null, workDir.getAbsolutePath());
    }

    @Override
    public void runP4Command(PerforcePhysicalConnectionParametersI parameters,
                             String[] p4args,
                             ExecResult retVal,
                             @Nullable StringBuffer inputStream) {
        runP4CommandImpl(parameters, myConnArgs, p4args, retVal, inputStream, myPassword);
    }

    @Override
    public File getWorkingDirectory() {
        return myWorkDir;
    }

    @Override
    public ConnectionKey getConnectionKey() {
        return new ConnectionKey("", "", "");
    }

    @Override
    public ConnectionId getId() {
        return myConnectionId;
    }

    @Override
    public boolean handlesFile(File file) {
        return true;
    }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.connections;

import consulo.util.collection.ArrayUtil;
import org.jspecify.annotations.Nullable;
import org.jetbrains.idea.perforce.application.ConnectionKey;
import org.jetbrains.idea.perforce.perforce.ConnectionId;
import org.jetbrains.idea.perforce.perforce.ExecResult;
import org.jetbrains.idea.perforce.perforce.PerforcePhysicalConnectionParametersI;

import java.io.File;

public class PerforceLocalConnection extends AbstractP4Connection {
    // also singleton but not equal to those SingletonConnection
    private final ConnectionId myConnectionId;
    private final File myCwd;

    public PerforceLocalConnection(String workingDir) {
        myCwd = new File(workingDir);
        myConnectionId = new ConnectionId(null, workingDir);
    }

    @Override
    public void runP4Command(PerforcePhysicalConnectionParametersI parameters,
                             String[] p4args,
                             ExecResult retVal,
                             @Nullable StringBuffer inputStream) {
        runP4CommandImpl(parameters, ArrayUtil.EMPTY_STRING_ARRAY, p4args, retVal, inputStream);
    }

    @Override
    public File getWorkingDirectory() {
        return myCwd;
    }

    @Override
    public ConnectionKey getConnectionKey() {
        throw new UnsupportedOperationException();
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

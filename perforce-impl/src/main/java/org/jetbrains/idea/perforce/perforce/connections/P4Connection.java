// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.connections;

import consulo.versionControlSystem.VcsException;
import org.jspecify.annotations.Nullable;
import org.jetbrains.idea.perforce.application.ConnectionKey;
import org.jetbrains.idea.perforce.perforce.ConnectionId;
import org.jetbrains.idea.perforce.perforce.ExecResult;
import org.jetbrains.idea.perforce.perforce.PerforcePhysicalConnectionParametersI;
import org.jetbrains.idea.perforce.perforce.PerforceTimeoutException;

import java.io.File;
import java.io.IOException;

public interface P4Connection {

    void runP4Command(PerforcePhysicalConnectionParametersI parameters, String[] p4args, ExecResult retVal, @Nullable StringBuffer inputStream)
      throws VcsException, PerforceTimeoutException, IOException, InterruptedException;

    ExecResult runP4CommandLine(PerforcePhysicalConnectionParametersI settings, String[] strings, @Nullable StringBuffer stringBuffer)
      throws VcsException;

    ExecResult runP4CommandLine(PerforcePhysicalConnectionParametersI settings, String[] conArgs, String[] p4args, @Nullable StringBuffer stringBuffer)
            throws VcsException;

    ConnectionKey getConnectionKey();

    ConnectionId getId();

    default String getWorkingDir() {
        return getWorkingDirectory().getPath();
    }

    File getWorkingDirectory();

    boolean handlesFile(File file);

    boolean isConnected();
}

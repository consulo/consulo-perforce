// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.perforce.perforce.connections;

import consulo.util.collection.ArrayUtil;
import org.jetbrains.idea.perforce.perforce.ConnectionId;
import org.jetbrains.idea.perforce.perforce.ExecResult;
import org.jetbrains.idea.perforce.perforce.PerforcePhysicalConnectionParametersI;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * P4ParametersConnection extension that includes password in connection args.
 * Used for test connections with explicit credentials.
 */
public class TestParametersConnection extends P4ParametersConnection {
    public TestParametersConnection(P4ConnectionParameters parameters, ConnectionId connectionId) {
        super(parameters, connectionId);
    }

    @Override
    public void runP4Command(PerforcePhysicalConnectionParametersI parameters,
                             String[] p4args,
                             ExecResult retVal,
                             @Nullable StringBuffer inputStream) {
        String[] connArgs = buildConnectionArgs();
        runP4CommandImpl(parameters, connArgs, p4args, retVal, inputStream, myParameters.getPassword());
    }

    private String[] buildConnectionArgs() {
        List<String> args = new ArrayList<>();
        String server = myParameters.getServer();
        if (server != null && !server.isBlank()) {
            args.add("-p");
            args.add(server);
        }
        String user = myParameters.getUser();
        if (user != null && !user.isBlank()) {
            args.add("-u");
            args.add(user);
        }
        String client = myParameters.getClient();
        if (client != null && !client.isBlank()) {
            args.add("-c");
            args.add(client);
        }
        return ArrayUtil.toStringArray(args);
    }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.perforce.checkout;

import consulo.project.Project;
import org.jetbrains.idea.perforce.perforce.PerforceRunner;
import org.jetbrains.idea.perforce.perforce.PerforceSettings;
import org.jetbrains.idea.perforce.perforce.connections.TestLoginManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

final class PerforceCloneRunnerFactory {
    private PerforceCloneRunnerFactory() {
    }

    static PerforceRunner createRunner(Project project, PerforceCloneParams params, File workDir) {
        PerforceSettings settings = new PerforceSettings(project);
        settings.useP4CONFIG = false;
        settings.port = params.getServer();
        settings.user = params.getUser();
        settings.client = params.getClient();
        settings.USE_LOGIN = false;

        String[] connArgs = buildConnectArgs(params);
        String password = params.getPassword();
        PerforceCloneConnection connection = new PerforceCloneConnection(workDir, connArgs, password.isBlank() ? null : password);
        PerforceCloneConnectionManager connectionManager = new PerforceCloneConnectionManager(connection);
        TestLoginManager loginManager = new TestLoginManager(project, settings, connectionManager);

        return new PerforceRunner(connectionManager, settings, loginManager);
    }

    static String[] buildConnectArgs(PerforceCloneParams params) {
        List<String> args = new ArrayList<>();
        if (!params.getServer().isBlank()) {
            args.add("-p");
            args.add(params.getServer());
        }
        if (!params.getUser().isBlank()) {
            args.add("-u");
            args.add(params.getUser());
        }
        if (!params.getClient().isBlank()) {
            args.add("-c");
            args.add(params.getClient());
        }
        return args.toArray(String[]::new);
    }
}

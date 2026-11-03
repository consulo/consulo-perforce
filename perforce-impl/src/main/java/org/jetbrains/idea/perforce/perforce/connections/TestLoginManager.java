// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.perforce.perforce.connections;

import consulo.project.Project;
import consulo.versionControlSystem.VcsException;
import org.jetbrains.idea.perforce.perforce.PerforceSettings;
import org.jetbrains.idea.perforce.perforce.login.LoginPerformerImpl;
import org.jetbrains.idea.perforce.perforce.login.LoginSupport;

/**
 * LoginSupport implementation for test connections.
 * Prompts for password if needed.
 */
public final class TestLoginManager implements LoginSupport {
    private final Project myProject;
    private final PerforceSettings mySettings;
    private final PerforceConnectionManagerI myConnectionManager;

    public TestLoginManager(Project project, PerforceSettings settings, PerforceConnectionManagerI connectionManager) {
        myProject = project;
        mySettings = settings;
        myConnectionManager = connectionManager;
    }

    @Override
    public boolean silentLogin(P4Connection connection) throws VcsException {
        String password = connection instanceof P4ParametersConnection parametersConnection
            ? parametersConnection.getParameters().getPassword()
            : mySettings.getPasswd();

        LoginPerformerImpl loginPerformer = new LoginPerformerImpl(myProject, connection, myConnectionManager);
        if (password != null && loginPerformer.login(password).isSuccess()) {
            return true;
        }

        while (true) {
            String requested = mySettings.requestForPassword(mySettings.useP4CONFIG ? connection : null);
            if (requested == null) {
                return false;
            }
            if (loginPerformer.login(requested).isSuccess()) {
                PerforceConnectionManager.getInstance(myProject).updateConnections();
                return true;
            }
        }
    }

    @Override
    public void notLogged(P4Connection connection) {
    }
}

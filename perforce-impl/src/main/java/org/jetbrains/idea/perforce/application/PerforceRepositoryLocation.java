// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).

package org.jetbrains.idea.perforce.application;

import consulo.perforce.localize.PerforceLocalize;
import consulo.project.Project;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.RepositoryLocation;
import consulo.versionControlSystem.VcsException;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jetbrains.idea.perforce.perforce.connections.PerforceConnectionManager;
import org.jspecify.annotations.Nullable;

import java.io.File;

/**
 * Repository location of a Perforce root: the local root path as URL and {@code server://path} as location.
 */
public final class PerforceRepositoryLocation implements RepositoryLocation {
    private final String myURL;
    private final String myLocation;
    private final Project myProject;
    private @Nullable P4Connection myConnection;
    private @Nullable PerforceClient myClient;

    private PerforceRepositoryLocation(String URL, String location, Project project) {
        myURL = URL;
        myLocation = location;
        myProject = project;
    }

    public static PerforceRepositoryLocation create(VirtualFile file, Project project) {
        return new PerforceRepositoryLocation(file.getPath(), file.getPath(), project);
    }

    public static PerforceRepositoryLocation create(FilePath p, String location, Project project) {
        return new PerforceRepositoryLocation(p.getPath(), location, project);
    }

    public String getURL() {
        return myURL;
    }

    public String getLocation() {
        return myLocation;
    }

    @Override
    public String toPresentableString() {
        return myURL;
    }

    @Override
    public String getKey() {
        return myURL;
    }

    @Override
    public String toString() {
        return myLocation;
    }

    @Override
    public void onAfterBatch() {
        myConnection = null;
        myClient = null;
    }

    @Override
    public void onBeforeBatch() throws VcsException {
        myConnection = getConnection();
        myClient = getClient();
    }

    private static P4Connection getConnection(Project project, String url) throws VcsException {
        P4Connection connection = PerforceConnectionManager.getInstance(project).getConnectionForFile(new File(url));
        if (connection == null) {
            throw new VcsException(PerforceLocalize.errorCanNotGetP4Connection(url).get());
        }
        return connection;
    }

    public P4Connection getConnection() throws VcsException {
        P4Connection connection = myConnection;
        if (connection != null) {
            return connection;
        }
        return getConnection(myProject, getURL());
    }

    public PerforceClient getClient() throws VcsException {
        PerforceClient client = myClient;
        if (client == null) {
            return createClient(getConnection());
        }
        return client;
    }

    private PerforceClient createClient(P4Connection connection) {
        return PerforceManager.getInstance(myProject).getClient(connection);
    }
}

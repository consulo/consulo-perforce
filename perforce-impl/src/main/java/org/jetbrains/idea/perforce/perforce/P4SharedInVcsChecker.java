// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce;

import consulo.annotation.component.ExtensionImpl;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.VcsKey;
import consulo.versionControlSystem.VcsSharedChecker;
import org.jetbrains.idea.perforce.application.PerforceVcs;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jetbrains.idea.perforce.perforce.connections.PerforceConnectionManager;

import java.nio.file.Path;

@ExtensionImpl
public final class P4SharedInVcsChecker implements VcsSharedChecker {
    private static final Logger LOG = Logger.getInstance(P4SharedInVcsChecker.class);

    @Override
    public VcsKey getSupportedVcs() {
        return PerforceVcs.getKey();
    }

    @Override
    public boolean isPathSharedInVcs(Project project, Path path) throws VcsException {
        P4File dir = P4File.create(path.toFile());
        String[] p4args = {P4Command.changes.name(), dir.getRecursivePath()};
        P4Connection connection = PerforceConnectionManager.getInstance(project).getConnectionForFile(dir);
        if (connection == null) {
            return false;
        }

        ExecResult execResult = PerforceRunner.getInstance(project).executeP4Command(p4args, connection);
        if (execResult.getExitCode() != 0) {
            LOG.debug("Failed to check changes under " + dir + ", P4 command " + String.join(" ", p4args) + ", result " + execResult);
        }

        return !OutputMessageParser.readOutputLines(execResult.getStdout()).isEmpty();
    }
}

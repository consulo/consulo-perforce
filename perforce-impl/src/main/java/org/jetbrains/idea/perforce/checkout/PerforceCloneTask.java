// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.perforce.checkout;

import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.Task;
import consulo.logging.Logger;
import consulo.perforce.localize.PerforceLocalize;
import consulo.project.Project;
import consulo.ui.MessageBoxes;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.io.FileUtil;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.checkout.CheckoutCallback;
import consulo.virtualFileSystem.StandardFileSystems;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.idea.perforce.application.PerforceVcs;
import org.jetbrains.idea.perforce.perforce.ExecResult;
import org.jetbrains.idea.perforce.perforce.P4File;
import org.jetbrains.idea.perforce.perforce.PerforceRunner;

import java.io.File;
import java.io.IOException;

import static org.jetbrains.idea.perforce.perforce.connections.P4ConfigFields.P4CLIENT;
import static org.jetbrains.idea.perforce.perforce.connections.P4ConfigFields.P4IGNORE;
import static org.jetbrains.idea.perforce.perforce.connections.P4ConfigFields.P4PORT;
import static org.jetbrains.idea.perforce.perforce.connections.P4ConfigFields.P4USER;
import static org.jetbrains.idea.perforce.perforce.connections.PerforceWorkspaceConfigurator.GITIGNORE_NAME;
import static org.jetbrains.idea.perforce.perforce.connections.PerforceWorkspaceConfigurator.P4CONFIG_NAME;
import static org.jetbrains.idea.perforce.perforce.connections.PerforceWorkspaceConfigurator.P4IGNORE_NAME;

/**
 * Syncs a Perforce workspace into the target directory in background and reports the checked out directory.
 */
final class PerforceCloneTask extends Task.Backgroundable {
    private static final Logger LOG = Logger.getInstance(PerforceCloneTask.class);

    private final Project myCloneProject;
    private final PerforceCloneParams myParams;
    private final CheckoutCallback myListener;
    private volatile boolean mySuccess;

    PerforceCloneTask(Project project, PerforceCloneParams params, CheckoutCallback listener) {
        super(project, PerforceLocalize.checkoutProgressTitle(), true);
        myCloneProject = project;
        myParams = params;
        myListener = listener;
    }

    @Override
    public void run(ProgressIndicator indicator) {
        indicator.setText(PerforceLocalize.checkoutProgressSyncing(myParams.getDirectory()));

        File targetDir = new File(myParams.getDirectory());
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }

        try {
            PerforceRunner runner = PerforceCloneRunnerFactory.createRunner(myCloneProject, myParams, targetDir);
            P4File p4File = P4File.create(targetDir);

            ExecResult result = runner.sync(p4File, true);

            String stderr = result.getStderr();
            if (result.getExitCode() != 0 && !stderr.isBlank() && !stderr.contains(PerforceRunner.FILES_UP_TO_DATE)) {
                LOG.warn("Cannot sync " + targetDir + ": " + stderr);
                return;
            }

            VirtualFile dir = StandardFileSystems.local().refreshAndFindFileByPath(targetDir.getAbsolutePath());
            if (dir != null) {
                dir.refresh(true, true);
            }

            writeP4Config(targetDir);

            mySuccess = true;
        }
        catch (VcsException e) {
            String projectPath = FileUtil.toSystemIndependentName(targetDir.getAbsolutePath());
            LOG.warn("Cannot clone project " + projectPath, e);
        }
    }

    @Override
    @RequiredUIAccess
    public void onSuccess() {
        if (mySuccess) {
            myListener.directoryCheckedOut(new File(myParams.getDirectory()), PerforceVcs.getKey());
            myListener.checkoutCompleted();
        }
        else {
            MessageBoxes.okError(PerforceLocalize.checkoutTaskFailed()).title(PerforceLocalize.checkoutTaskTitle()).showAsync();
        }
    }

    @Override
    @RequiredUIAccess
    public void onCancel() {
        MessageBoxes.okInfo(PerforceLocalize.checkoutTaskCanceled()).title(PerforceLocalize.checkoutTaskTitle()).showAsync();
    }

    private void writeP4Config(File targetDir) {
        File configFile = new File(targetDir, P4CONFIG_NAME);
        if (configFile.exists()) {
            return;
        }

        String configContent = P4PORT.name() + "=" + myParams.getServer() + "\n" +
            P4USER.name() + "=" + myParams.getUser() + "\n" +
            P4CLIENT.name() + "=" + myParams.getClient() + "\n" +
            P4IGNORE.name() + "=" + P4IGNORE_NAME + ";" + GITIGNORE_NAME;

        try {
            FileUtil.writeToFile(configFile, configContent);
        }
        catch (IOException e) {
            LOG.warn("Cannot create p4config in " + targetDir, e);
        }
    }
}

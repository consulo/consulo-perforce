/*
 * Copyright 2000-2007 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.idea.perforce.actions;

import consulo.annotation.component.ActionImpl;
import consulo.application.util.SystemInfo;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.perforce.localize.PerforceLocalize;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.ProcessHandlerBuilder;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.event.ProcessEvent;
import consulo.process.event.ProcessListener;
import consulo.project.Project;
import consulo.project.ui.notification.NotificationService;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.ui.ex.awt.Messages;
import consulo.util.dataholder.Key;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.util.VcsUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.status.FileStatus;
import consulo.virtualFileSystem.status.FileStatusManager;
import org.jetbrains.idea.perforce.ClientVersion;
import org.jetbrains.idea.perforce.application.PerforceClient;
import org.jetbrains.idea.perforce.application.PerforceManager;
import org.jetbrains.idea.perforce.application.PerforceVcs;
import org.jetbrains.idea.perforce.perforce.FStat;
import org.jetbrains.idea.perforce.perforce.P4File;
import org.jetbrains.idea.perforce.perforce.PerforceSettings;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jetbrains.idea.perforce.perforce.connections.PerforceConnectionManager;
import org.jetbrains.idea.perforce.perforce.connections.PerforceNotificationGroupContributor;

@ActionImpl(id = "RevisionGraph")
public class RevisionGraphAction extends DumbAwareAction implements AnActionWithSyncUpdate {
    private static final Logger LOG = Logger.getInstance(RevisionGraphAction.class);

    /**
     * On Windows, run p4v.exe (next to the configured p4vc) instead of p4vc.
     */
    private static final boolean FORCE_P4V_EXE = true;

    public RevisionGraphAction() {
        super(PerforceLocalize.actionRevisiongraphText());
    }

    protected RevisionGraphAction(LocalizeValue text) {
        super(text);
    }

    private static void addCommandParameters(GeneralCommandLine cmd, PerforceClient client) throws VcsException {
        String port = client.getDeclaredServerPort();
        if (StringUtil.isEmptyOrSpaces(port)) {
            port = client.getServerPort();
        }
        String userName = client.getUserName();
        String clientName = client.getName();

        if (port == null || userName == null || clientName == null) {
            throw new VcsException(PerforceLocalize.errorFailedToRetrievePerforceClientSettings(
                String.valueOf(port), String.valueOf(userName), String.valueOf(clientName)));
        }

        //create the command
        cmd.addParameters("-p", port, "-u", userName, "-c", clientName);
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        VirtualFile virtualFile = e.getData(VirtualFile.KEY);
        if (project == null || virtualFile == null) {
            return;
        }

        P4Connection connection = PerforceConnectionManager.getInstance(project).getConnectionForFile(virtualFile);
        if (connection == null) {
            Messages.showInfoMessage(project, PerforceLocalize.connectionCannotDetermineSettings().get(),
                PerforceLocalize.connectionProblem().get());
            return;
        }

        PerforceManager manager = PerforceManager.getInstance(project);
        PerforceClient client = manager.getClient(connection);
        ClientVersion version = manager.getClientVersion();
        PerforceSettings settings = PerforceSettings.getSettings(project);
        FilePath p4vPath = VcsUtil.getFilePath(settings.PATH_TO_P4VC, false);

        GeneralCommandLine cmd;
        if (!SystemInfo.isWindows || !FORCE_P4V_EXE) {
            cmd = new GeneralCommandLine(settings.PATH_TO_P4VC);
        }
        else {
            String p4vExeName = "p4v.exe";
            FilePath parentPath = p4vPath.getParentPath();
            FilePath p4vExePath = p4vPath.getName().equals(p4vExeName)
                ? p4vPath
                : VcsUtil.getFilePath(parentPath != null ? parentPath.getPath() + "/" + p4vExeName : p4vExeName, false);
            cmd = new GeneralCommandLine(FileUtil.toSystemDependentName(p4vExePath.getPath()));
        }

        boolean supportsP4vcParam = version != null && version.supportsP4vcParam();
        if (supportsP4vcParam) {
            cmd.addParameters("-p4vc");
        }

        try {
            addCommandParameters(cmd, client);
        }
        catch (VcsException e1) {
            Messages.showErrorDialog(project, e1.getMessage(), PerforceLocalize.perforce().get());
            return;
        }

        cmd.addParameters(getCommandName());

        FStat fStat = null;
        if (settings.ENABLED) {
            P4File p4File = P4File.create(virtualFile);
            try {
                fStat = p4File.getFstat(project, false);
            }
            catch (VcsException ex) {
                Messages.showErrorDialog(project, PerforceLocalize.failedToRetrieveP4StatusInformation(
                        FileUtil.toSystemDependentName(virtualFile.getPath()),
                        StringUtil.notNullize(ex.getMessage())).get(),
                    PerforceLocalize.perforce().get());
                return;
            }
        }
        if (fStat != null && !StringUtil.isEmpty(fStat.depotFile)) {
            cmd.addParameters(fStat.depotFile);
        }
        else {
            cmd.addParameters(FileUtil.toSystemDependentName(virtualFile.getPath()));
        }

        LOG.debug("Invoking p4v with command line " + cmd.getCommandLineString());

        try {
            ProcessHandler handler = ProcessHandlerBuilder.create(cmd).build();
            StringBuilder output = new StringBuilder();
            handler.addProcessListener(new ProcessListener() {
                @Override
                public void onTextAvailable(ProcessEvent event, Key outputType) {
                    output.append(event.getText());
                }

                @Override
                public void processTerminated(ProcessEvent event) {
                    int exitCode = event.getExitCode();
                    if (exitCode != 0) {
                        String outputText = !output.isEmpty() ? PerforceLocalize.p4vcRunningProblemsOutput(output).get() : "";
                        NotificationService.getInstance()
                            .newError(PerforceNotificationGroupContributor.GROUP)
                            .title(PerforceLocalize.p4vcRunningProblems())
                            .content(PerforceLocalize.p4vcRunningProblemsMessage(cmd, exitCode, outputText))
                            .notify(project);
                    }
                }
            });
            handler.startNotify();
        }
        catch (ExecutionException ex) {
            Messages.showErrorDialog(project, PerforceLocalize.p4vcRunFailed(StringUtil.notNullize(ex.getMessage())).get(),
                PerforceLocalize.p4vc().get());
        }
    }

    protected String getCommandName() {
        return "revgraph";
    }

    @Override
    public void update(AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        VirtualFile virtualFile = e.getData(VirtualFile.KEY);
        if (project == null || virtualFile == null || !virtualFile.isInLocalFileSystem() || virtualFile.isDirectory() ||
            !PerforceSettings.getSettings(project).ENABLED ||
            !(VcsUtil.getVcsFor(project, virtualFile) instanceof PerforceVcs)) {
            e.getPresentation().setEnabled(false);
            return;
        }

        FileStatus fileStatus = FileStatusManager.getInstance(project).getStatus(virtualFile);
        e.getPresentation().setEnabled(fileStatus != FileStatus.ADDED && fileStatus != FileStatus.UNKNOWN
            && fileStatus != FileStatus.IGNORED);
    }
}

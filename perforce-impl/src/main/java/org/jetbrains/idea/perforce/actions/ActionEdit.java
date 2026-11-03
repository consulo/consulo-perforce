/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
import consulo.application.ReadAction;
import consulo.localize.LocalizeValue;
import consulo.perforce.localize.PerforceLocalize;
import consulo.project.Project;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.Presentation;
import consulo.ui.ex.awt.Messages;
import consulo.versionControlSystem.AbstractVcsHelper;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.change.ChangeListManager;
import consulo.versionControlSystem.util.ActionWithTempFile;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.status.FileStatus;
import consulo.virtualFileSystem.status.FileStatusManager;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import org.jetbrains.idea.perforce.CancelActionException;
import org.jetbrains.idea.perforce.application.PerforceVcs;
import org.jetbrains.idea.perforce.perforce.FStat;
import org.jetbrains.idea.perforce.perforce.P4File;
import org.jetbrains.idea.perforce.perforce.PerforceRunner;
import org.jetbrains.idea.perforce.perforce.PerforceRunnerI;
import org.jetbrains.idea.perforce.perforce.PerforceSettings;

import java.util.List;

@ActionImpl(id = "PerforceDirect.Edit")
public final class ActionEdit extends ActionBaseFile {
    public ActionEdit() {
        super(PerforceLocalize.actionPerforcedirectEditText(), PerforceLocalize.actionPerforcedirectEditDescription());
    }

    @Override
    public void update(AnActionEvent e) {
        super.update(e);
        Presentation presentation = e.getPresentation();
        if (presentation.isEnabledAndVisible()) {
            Project project = e.getRequiredData(Project.KEY);
            ChangeListManager clm = ChangeListManager.getInstance(project);
            VirtualFile[] files = e.getData(VirtualFile.KEY_OF_ARRAY);
            boolean hasVersioned = false;
            if (files != null) {
                for (VirtualFile file : files) {
                    if (!(clm.isIgnoredFile(file) || clm.isUnversioned(file))) {
                        hasVersioned = true;
                        break;
                    }
                }
            }
            if (!hasVersioned) {
                presentation.setEnabled(false);
            }
        }
    }

    @Override
    protected void performAction(VirtualFile vFile,
                                 Project project,
                                 boolean alone,
                                 List<VirtualFile> filesToPostProcess) throws CancelActionException, VcsException {
        if (ChangeListManager.getInstance(project).isIgnoredFile(vFile)) {
            return;
        }

        P4File p4File = P4File.create(vFile);
        PerforceRunnerI runner = PerforceRunner.getInstance(project).getProxy();

        log("Action edit on file: " + p4File.getLocalPath());

        if (vFile.isDirectory()) {
            VirtualFile[] children = ReadAction.compute(() -> vFile.getChildren());
            if (children == null) {
                return;
            }
            for (VirtualFile child : children) {
                performAction(child, project, alone && (children.length == 1), filesToPostProcess);
            }
        }
        else {
            FileStatus status = FileStatusManager.getInstance(project).getStatus(vFile);
            if (status == FileStatus.IGNORED || status == FileStatus.UNKNOWN) {
                return;
            }
            if (!PerforceSettings.getSettings(project).ENABLED) {
                filesToPostProcess.add(vFile);
                return;
            }

            // check whether it will be under any clientspec
            FStat p4FStat = p4File.getFstat(project, true);
            if (p4FStat.status == FStat.STATUS_NOT_IN_CLIENTSPEC ||
                p4FStat.status == FStat.STATUS_UNKNOWN) {
                if (vFile.isWritable()) {
                    return;
                }
                if (alone) {
                    String msg = PerforceLocalize.errorMessageFileNotUnderAnyClientspec(p4File.getLocalPath()).get();
                    MessageManager.showMessageDialog(project, msg, PerforceLocalize.messageTitleCannotEdit().get(),
                        Messages.getErrorIcon());
                }
                else {
                    String msg = PerforceLocalize.confirmationTextFileNotUnderAnyClientspecContinueCheckout(p4File.getLocalPath()).get();

                    int answer = MessageManager.showDialog(project,
                        msg,
                        PerforceLocalize.messageTitleCannotEdit().get(),
                        yesNoOptions(),
                        1,
                        Messages.getErrorIcon());
                    if (answer != 0) {
                        throw new CancelActionException();
                    }
                }
            }
            else {
                if (p4FStat.status == FStat.STATUS_NOT_ADDED ||
                    p4FStat.status == FStat.STATUS_ONLY_LOCAL) {
                    if (alone) {
                        MessageManager.showMessageDialog(project,
                            PerforceLocalize.messageTextFileNotOnServer(p4File.getLocalPath()).get(),
                            PerforceLocalize.messageTitleCannotEdit().get(),
                            Messages.getErrorIcon());
                    }
                }
                else if (p4FStat.status == FStat.STATUS_DELETED) {
                    if (alone) {
                        MessageManager.showMessageDialog(project,
                            PerforceLocalize.messageTextFileDeletedFromServer(p4File.getLocalPath()).get(),
                            PerforceLocalize.messageTitleCannotEdit().get(),
                            Messages.getErrorIcon());
                    }
                }
                else if (p4FStat.status == FStat.STATUS_ONLY_ON_SERVER) {
                    String msg = PerforceLocalize.confirmationTextFileRegisteredAsOnlyOnServerReplaceIt(p4File.getLocalPath(),
                        p4FStat.depotFile).get();

                    int answer = MessageManager.showDialog(project,
                        msg,
                        PerforceLocalize.confirmationTitleFileAlreadyInPerforce().get(),
                        yesNoCancelRestOptions(),
                        1,
                        Messages.getErrorIcon());
                    if (answer == 1) {
                        //return
                    }
                    else if (answer == 2 || answer == -1) {
                        throw new CancelActionException();
                    }
                    else {
                        new ActionWithTempFile(p4File.getLocalFile()) {
                            @Override
                            protected void executeInternal() throws VcsException {
                                runner.revert(p4File, false);
                                runner.sync(p4File, false);
                                runner.edit(p4File);
                            }
                        }.execute();
                    }
                }
                else if (p4FStat.local == FStat.LOCAL_DELETING) {
                    String msg = PerforceLocalize.confirmationTextFileMarkedForDeletionRevertAndReplace(p4File.getLocalPath(),
                        p4FStat.depotFile).get();

                    int answer = MessageManager.showDialog(project,
                        msg,
                        PerforceLocalize.confirmationTitleFileAlreadyInPerforce().get(),
                        yesNoCancelRestOptions(),
                        1,
                        Messages.getErrorIcon());
                    if (answer == 1) {
                        //return;
                    }
                    else if (answer == 2 || answer == -1) {
                        throw new CancelActionException();
                    }
                    else {
                        new ActionWithTempFile(p4File.getLocalFile()) {
                            @Override
                            protected void executeInternal() throws VcsException {
                                runner.revert(p4File, false);
                                runner.edit(p4File);
                            }
                        }.execute();
                    }
                }
                else if (p4FStat.local != FStat.LOCAL_CHECKED_IN && p4FStat.local != FStat.LOCAL_INTEGRATING) {
                    if (alone) {
                        String msg = PerforceLocalize.messageTextFileAlreadyBeingCheckedOutOrAdded(p4File.getLocalPath()).get();
                        MessageManager.showMessageDialog(project, msg, PerforceLocalize.messageTitleNoReasonToEdit().get(),
                            Messages.getInformationIcon());
                    }
                }
                else {
                    filesToPostProcess.add(vFile);
                }
            }
        }
    }

    @Override
    public void postProcessFiles(Project project, List<VirtualFile> filesToPostProcess) {
        VirtualFile[] fileArray = VirtualFileUtil.toVirtualFileArray(filesToPostProcess);
        try {
            PerforceVcs.getInstance(project).getEditFileProvider().editFiles(fileArray);
        }
        catch (VcsException e) {
            AbstractVcsHelper.getInstance(project).showError(e, LocalizeValue.empty());
        }
    }
}

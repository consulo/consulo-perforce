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

import consulo.application.Application;
import consulo.document.FileDocumentManager;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.perforce.localize.PerforceLocalize;
import consulo.platform.base.localize.CommonLocalize;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.ui.ex.action.Presentation;
import consulo.versionControlSystem.AbstractVcsHelper;
import consulo.versionControlSystem.ProjectLevelVcsManager;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.change.VcsDirtyScopeManager;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import org.jetbrains.idea.perforce.CancelActionException;
import org.jetbrains.idea.perforce.application.PerforceVcs;
import org.jetbrains.idea.perforce.perforce.P4File;

import java.util.ArrayList;
import java.util.List;

public abstract class ActionBaseFile extends DumbAwareAction implements AnActionWithSyncUpdate {
    private static final Logger LOG = Logger.getInstance(ActionBaseFile.class);

    protected ActionBaseFile() {
    }

    protected ActionBaseFile(LocalizeValue text, LocalizeValue description) {
        super(text, description);
    }

    protected static String[] yesNoOptions() {
        return new String[]{CommonLocalize.buttonYes().get(), CommonLocalize.buttonNo().get()};
    }

    protected static String[] yesNoCancelRestOptions() {
        return new String[]{
            CommonLocalize.buttonYes().get(),
            CommonLocalize.buttonNo().get(),
            PerforceLocalize.buttonTextCancelRest().get()
        };
    }

    protected static void log(String msg) {
        LOG.debug(msg);
    }

    @Override
    public void update(AnActionEvent e) {
        Presentation presentation = e.getPresentation();

        Project project = e.getData(Project.KEY);
        if (project == null) {
            presentation.setEnabled(false);
            return;
        }

        VirtualFile[] vFiles = e.getData(VirtualFile.KEY_OF_ARRAY);
        if (vFiles == null || vFiles.length == 0 ||
            !ProjectLevelVcsManager.getInstance(project).checkAllFilesAreUnder(PerforceVcs.getInstance(project), vFiles)) {
            presentation.setEnabled(false);
            return;
        }

        presentation.setEnabled(true);
    }

    protected abstract void performAction(VirtualFile vFile,
                                          Project project,
                                          boolean alone,
                                          List<VirtualFile> filesToPostProcess) throws CancelActionException, VcsException;

    @Override
    @RequiredUIAccess
    public void actionPerformed(AnActionEvent event) {
        Project project = event.getData(Project.KEY);
        if (project == null) {
            return;
        }
        VirtualFile[] vFiles = event.getData(VirtualFile.KEY_OF_ARRAY);
        if (vFiles == null || vFiles.length == 0) {
            return;
        }

        FileDocumentManager.getInstance().saveAllDocuments();

        processFiles(project, vFiles);
    }

    public void processFiles(Project project, VirtualFile... vFiles) {
        boolean containsDirectory = false;
        if (vFiles.length > 0) {
            try {
                List<VirtualFile> filesToPostProcess = new ArrayList<>();
                for (VirtualFile vFile : vFiles) {
                    boolean cancelled = false;
                    try {
                        try {
                            performAction(vFile, project, (vFiles.length == 1), filesToPostProcess);
                            if (vFile.isDirectory()) {
                                containsDirectory = true;
                            }
                        }
                        catch (VcsException e) {
                            AbstractVcsHelper.getInstance(project).showError(e, PerforceLocalize.dialogTitlePerforce());
                        }
                    }
                    catch (CancelActionException e) {
                        cancelled = true;
                    }
                    if (cancelled) {
                        break;
                    }
                }
                postProcessFiles(project, filesToPostProcess);
            }
            finally {
                if (containsDirectory) {
                    VirtualFileManager.getInstance().asyncRefresh(() -> {
                        P4File.invalidateFstat(project);
                        for (VirtualFile file : vFiles) {
                            if (file.isDirectory()) {
                                VcsDirtyScopeManager.getInstance(project).dirDirtyRecursively(file);
                            }
                            else {
                                VcsDirtyScopeManager.getInstance(project).fileDirty(file);
                            }
                        }
                    });
                }
                else {
                    for (VirtualFile vFile : vFiles) {
                        Application.get().runWriteAction(() -> vFile.refresh(false, false));
                        P4File.invalidateFstat(vFile);
                        VcsDirtyScopeManager.getInstance(project).fileDirty(vFile);
                    }
                }
            }
        }
    }

    public void postProcessFiles(Project project, List<VirtualFile> filesToPostProcess) {
    }
}

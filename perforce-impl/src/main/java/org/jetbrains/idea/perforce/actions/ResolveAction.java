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
import consulo.perforce.localize.PerforceLocalize;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.ui.ex.awt.Messages;
import consulo.util.collection.ContainerUtil;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.util.VcsUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.status.FileStatus;
import consulo.virtualFileSystem.status.FileStatusManager;
import org.jetbrains.idea.perforce.application.PerforceVcs;
import org.jetbrains.idea.perforce.merge.PerforceMergeProvider;
import org.jetbrains.idea.perforce.perforce.PerforceSettings;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ActionImpl(id = "Resolve")
public class ResolveAction extends DumbAwareAction implements AnActionWithSyncUpdate {
    public ResolveAction() {
        super(PerforceLocalize.actionResolveText());
    }

    @Override
    public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(isEnabled(e));
    }

    private static boolean isEnabled(AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        if (project == null) {
            return false;
        }

        VirtualFile[] vFiles = e.getData(VirtualFile.KEY_OF_ARRAY);
        if (vFiles == null) {
            return false;
        }

        List<VirtualFile> selectedFiles = getSelectedPerforceFiles(vFiles, project);
        if (selectedFiles.isEmpty() || ContainerUtil.exists(selectedFiles, VirtualFile::isDirectory)) {
            return false;
        }

        PerforceSettings perforceSettings = PerforceSettings.getSettings(project);
        if (!perforceSettings.ENABLED) {
            return false;
        }

        return !getMergedFiles(project, selectedFiles).isEmpty();
    }

    static boolean serverSupportsConflictsResolve(PerforceSettings perforceSettings,
                                                  Collection<? extends @Nullable P4Connection> connections) {
        try {
            for (P4Connection connection : connections) {
                if (connection == null) {
                    continue;
                }
                long serverVersion = perforceSettings.getServerVersionCached(connection);
                if (serverVersion < 2004) {
                    return false;
                }
            }
            return true;
        }
        catch (VcsException e) {
            return false;
        }
    }

    private static List<VirtualFile> getSelectedPerforceFiles(VirtualFile[] vFiles, Project project) {
        return ContainerUtil.filter(vFiles, file -> VcsUtil.getVcsFor(project, file) instanceof PerforceVcs);
    }

    private static List<VirtualFile> getMergedFiles(Project project, List<VirtualFile> selectedFiles) {
        List<VirtualFile> merged = new ArrayList<>();
        for (VirtualFile file : selectedFiles) {
            FileStatus status = FileStatusManager.getInstance(project).getStatus(file);
            if (status == FileStatus.MERGE || status == FileStatus.MERGED_WITH_CONFLICTS) {
                merged.add(file);
            }
        }
        return merged;
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        if (project == null) {
            return;
        }
        VirtualFile[] vFiles = e.getData(VirtualFile.KEY_OF_ARRAY);
        if (vFiles == null) {
            return;
        }

        List<VirtualFile> selectedFiles = getSelectedPerforceFiles(vFiles, project);

        PerforceSettings perforceSettings = PerforceSettings.getSettings(project);
        Set<@Nullable P4Connection> connections = new HashSet<>();
        for (VirtualFile file : selectedFiles) {
            connections.add(perforceSettings.getConnectionForFile(file));
        }
        if (!serverSupportsConflictsResolve(perforceSettings, connections)) {
            Messages.showErrorDialog(project, PerforceLocalize.messageServerCantResolveConflicts().get(),
                PerforceLocalize.messageTitleResolve().get());
            return;
        }

        new PerforceMergeProvider(project).showMergeDialog(getMergedFiles(project, selectedFiles));
    }
}

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
import consulo.application.Application;
import consulo.application.progress.ProgressManager;
import consulo.dataContext.DataContext;
import consulo.document.FileDocumentManager;
import consulo.perforce.localize.PerforceLocalize;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.ui.ex.action.Presentation;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.MultiMap;
import consulo.util.lang.Pair;
import consulo.versionControlSystem.AbstractVcsHelper;
import consulo.versionControlSystem.ProjectLevelVcsManager;
import consulo.versionControlSystem.VcsDataKeys;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.change.ChangeList;
import consulo.versionControlSystem.change.VcsDirtyScopeManager;
import consulo.versionControlSystem.checkin.CheckinProjectPanel;
import consulo.versionControlSystem.ui.Refreshable;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import org.jetbrains.idea.perforce.application.ConnectionKey;
import org.jetbrains.idea.perforce.application.FileGrouper;
import org.jetbrains.idea.perforce.application.PerforceManager;
import org.jetbrains.idea.perforce.application.PerforceNumberNameSynchronizer;
import org.jetbrains.idea.perforce.application.PerforceVcs;
import org.jetbrains.idea.perforce.perforce.P4File;
import org.jetbrains.idea.perforce.perforce.PerforceRunner;
import org.jetbrains.idea.perforce.perforce.PerforceSettings;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ActionImpl(id = "RevertUnchanged")
public class RevertAllUnchangedFilesAction extends DumbAwareAction implements AnActionWithSyncUpdate {
    public RevertAllUnchangedFilesAction() {
        super(PerforceLocalize.actionRevertunchangedText());
    }

    @Override
    public void update(AnActionEvent e) {
        Presentation presentation = e.getPresentation();

        Project project = e.getData(Project.KEY);
        if (project == null) {
            presentation.setVisible(false);
            return;
        }

        boolean visible;
        CheckinProjectPanel panel = getCheckinPanel(e);
        if (panel != null) {
            visible = panel.vcsIsAffected(PerforceVcs.NAME);
        }
        else {
            List<VirtualFile> files = selectedFiles(e.getDataContext());
            visible = !files.isEmpty() && hasFilesUnderPerforce(files, project);
        }

        presentation.setVisible(visible);
        presentation.setEnabled(PerforceSettings.getSettings(project).ENABLED);
    }

    private static @Nullable CheckinProjectPanel getCheckinPanel(AnActionEvent e) {
        return e.getData(Refreshable.PANEL_KEY) instanceof CheckinProjectPanel panel ? panel : null;
    }

    /**
     * The selected files of the context: the file array, or the single selected file.
     */
    private static List<VirtualFile> selectedFiles(DataContext context) {
        VirtualFile[] files = context.getData(VirtualFile.KEY_OF_ARRAY);
        if (files != null) {
            return Arrays.asList(files);
        }
        VirtualFile file = context.getData(VirtualFile.KEY);
        return file != null ? Collections.singletonList(file) : Collections.emptyList();
    }

    private static boolean hasFilesUnderPerforce(Collection<VirtualFile> roots, Project project) {
        return !findFilesUnderPerforce(roots, project).isEmpty();
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        if (project == null) {
            return;
        }
        ChangeList[] changeLists = e.getData(VcsDataKeys.CHANGE_LISTS);
        CheckinProjectPanel panel = getCheckinPanel(e);

        Collection<VirtualFile> roots;
        if (panel != null) {
            roots = panel.getRoots();
        }
        else {
            roots = selectedFiles(e.getDataContext());
        }

        Application.get().runWriteAction(() -> FileDocumentManager.getInstance().saveAllDocuments());

        revertUnchanged(project, roots, panel, changeLists);
    }

    public static void revertUnchanged(Project project,
                                       Collection<VirtualFile> roots,
                                       @Nullable CheckinProjectPanel panel,
                                       ChangeList @Nullable [] selection) {
        List<VcsException> exceptions = new ArrayList<>();

        ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
            if (selection != null && selection.length > 0 && revertChangeLists(project, selection, exceptions)) {
                return;
            }
            revertFiles(roots, project, exceptions);
        }, PerforceLocalize.messageTitleRevertUnchanged(), false, project);

        refreshAndDirty(project, roots);
        if (panel != null) {
            panel.restoreState();
        }

        if (!exceptions.isEmpty()) {
            AbstractVcsHelper.getInstance(project).showErrors(exceptions, PerforceLocalize.messageTitleRevertUnchangedFiles());
        }
    }

    private static void refreshAndDirty(Project project, Collection<VirtualFile> roots) {
        // otherwise events from file watcher may come too late
        VirtualFileUtil.markDirty(true, false, roots.toArray(VirtualFile.EMPTY_ARRAY));
        if (hasDirectories(roots)) {
            VirtualFileManager.getInstance().asyncRefresh(() -> {
                P4File.invalidateFstat(project);
                VcsDirtyScopeManager.getInstance(project).markEverythingDirty();
            });
        }
        else {
            for (VirtualFile vFile : findFilesUnderPerforce(roots, project)) {
                Application.get().runWriteAction(() -> vFile.refresh(false, false));
                P4File.invalidateFstat(vFile);
                if (vFile.isDirectory()) {
                    VcsDirtyScopeManager.getInstance(project).dirDirtyRecursively(vFile);
                }
                else {
                    VcsDirtyScopeManager.getInstance(project).fileDirty(vFile);
                }
            }
        }
    }

    private static boolean hasDirectories(Collection<VirtualFile> roots) {
        return ContainerUtil.find(roots, VirtualFile::isDirectory) != null;
    }

    private static void revertFiles(Collection<VirtualFile> roots, Project project, List<VcsException> exceptions) {
        List<VirtualFile> files = findFilesUnderPerforce(roots, project);
        MultiMap<P4Connection, VirtualFile> map = FileGrouper.distributeFilesByConnection(files, project);
        for (P4Connection connection : map.keySet()) {
            try {
                List<String> paths = ContainerUtil.map(map.get(connection), file -> P4File.create(file).getRecursivePath());
                PerforceRunner.getInstance(project).revertUnchanged(connection, paths);
            }
            catch (VcsException e1) {
                exceptions.add(e1);
            }
        }
    }

    private static List<VirtualFile> findFilesUnderPerforce(Collection<VirtualFile> roots, Project project) {
        PerforceVcs vcs = PerforceVcs.getInstance(project);
        ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
        return ContainerUtil.findAll(roots, file -> vcsManager.getVcsFor(file) == vcs);
    }

    private static boolean revertChangeLists(Project project, ChangeList[] selectedChangeLists, List<VcsException> exceptions) {
        boolean foundAll = true;
        PerforceNumberNameSynchronizer synchronizer = PerforceNumberNameSynchronizer.getInstance(project);

        Collection<P4Connection> connectionList = PerforceSettings.getSettings(project).getAllConnections();

        Set<Pair<Long, P4Connection>> numbers = new HashSet<>();
        for (P4Connection connection : connectionList) {
            try {
                PerforceManager.ensureValidClient(project, connection);
            }
            catch (VcsException e) {
                exceptions.add(e);
                continue;
            }

            ConnectionKey connectionKey = connection.getConnectionKey();
            for (ChangeList changeList : selectedChangeLists) {
                Long number = synchronizer.getNumber(connectionKey, changeList.getName());
                if (number != null) {
                    numbers.add(Pair.create(number, connection));
                }
                else {
                    foundAll = false;
                }
            }
        }
        for (Pair<Long, P4Connection> pair : numbers) {
            try {
                PerforceRunner.getInstance(project).revertUnchanged(pair.getSecond(), pair.getFirst().longValue());
            }
            catch (VcsException e1) {
                exceptions.add(e1);
            }
        }
        return foundAll;
    }
}

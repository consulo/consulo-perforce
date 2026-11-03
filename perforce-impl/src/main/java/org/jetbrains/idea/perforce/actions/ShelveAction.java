// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.actions;

import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.annotation.component.ActionRefAnchor;
import consulo.localize.LocalizeValue;
import consulo.perforce.localize.PerforceLocalize;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.ui.ex.action.Presentation;
import consulo.util.collection.MultiMap;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import consulo.versionControlSystem.AbstractVcsHelper;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.VcsDataKeys;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.change.Change;
import consulo.versionControlSystem.change.ChangeListManager;
import consulo.versionControlSystem.change.CommitContext;
import consulo.versionControlSystem.change.CommitExecutor;
import consulo.versionControlSystem.change.CommitSession;
import consulo.versionControlSystem.change.ContentRevision;
import consulo.versionControlSystem.change.LocalChangeList;
import consulo.versionControlSystem.change.VcsDirtyScopeManager;
import consulo.versionControlSystem.util.VcsUtil;
import org.jetbrains.idea.perforce.application.FileGrouper;
import org.jetbrains.idea.perforce.application.PerforceManager;
import org.jetbrains.idea.perforce.application.PerforceNumberNameSynchronizer;
import org.jetbrains.idea.perforce.application.PerforceVcs;
import org.jetbrains.idea.perforce.application.ShelvedChange;
import org.jetbrains.idea.perforce.operations.RefreshForVcs;
import org.jetbrains.idea.perforce.perforce.P4File;
import org.jetbrains.idea.perforce.perforce.PerforceRunner;
import org.jetbrains.idea.perforce.perforce.PerforceSettings;
import org.jetbrains.idea.perforce.ServerVersion;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shelves the selected changes on the Perforce server: opens the commit dialog with the shelve executor.
 */
@ActionImpl(
    id = "Perforce.Shelve",
    parents = @ActionParentRef(
        value = @ActionRef(id = "ChangesViewPopupMenu"),
        anchor = ActionRefAnchor.BEFORE,
        relatedToAction = @ActionRef(id = "ChangesView.Shelve")
    )
)
public class ShelveAction extends DumbAwareAction implements AnActionWithSyncUpdate {
    public static final String P4SHELF_EXECUTOR_ID = "Perforce.Shelf.Executor";

    public ShelveAction() {
        super(PerforceLocalize.actionPerforceShelveText());
    }

    @Override
    public void update(AnActionEvent e) {
        Change[] changes = e.getData(VcsDataKeys.CHANGES);
        Project project = e.getData(Project.KEY);
        Presentation presentation = e.getPresentation();
        if (changes == null || project == null) {
            presentation.setEnabledAndVisible(false);
        }
        else {
            List<Change> localChanges = new ArrayList<>();
            for (Change change : changes) {
                if (!(change instanceof ShelvedChange.IdeaChange)) {
                    localChanges.add(change);
                }
            }
            Set<P4Connection> connections = Handler.getConnections(project, localChanges).keySet();
            presentation.setEnabledAndVisible(!connections.isEmpty() && allSupportShelve(project, connections));
        }
    }

    static boolean allSupportShelve(Project project, Collection<P4Connection> connections) {
        for (P4Connection connection : connections) {
            if (!Handler.supportsShelve(project, connection)) {
                return false;
            }
        }
        return true;
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        Change[] selected = e.getData(VcsDataKeys.CHANGES);
        if (project == null || selected == null || selected.length == 0) {
            return;
        }

        List<Change> changes = Arrays.asList(selected);
        ChangeListManager changeListManager = ChangeListManager.getInstance(project);
        LocalChangeList initialList = changeListManager.getChangeList(changes.get(0));
        if (initialList == null) {
            initialList = changeListManager.getDefaultChangeList();
        }
        String comment = StringUtil.isEmptyOrSpaces(initialList.getComment()) ? initialList.getName() : initialList.getComment();

        AbstractVcsHelper.getInstance(project).commitChanges(
            project,
            changes,
            initialList,
            Collections.singletonList(new P4ShelveCommitExecutor(project)),
            false,
            StringUtil.notNullize(comment),
            null,
            false
        );
    }

    public static final class Handler {
        private Handler() {
        }

        public static void shelveChanges(Project project, @Nullable String commitMessage, Collection<Change> changes) {
            ChangeListManager changeListManager = ChangeListManager.getInstance(project);
            Map<@Nullable LocalChangeList, List<Change>> byList = new LinkedHashMap<>();
            for (Change change : changes) {
                byList.computeIfAbsent(changeListManager.getChangeList(change), k -> new ArrayList<>()).add(change);
            }
            for (Map.Entry<@Nullable LocalChangeList, List<Change>> entry : byList.entrySet()) {
                shelveChanges(project, commitMessage, entry.getValue(), entry.getKey());
            }
        }

        private static void shelveChanges(Project project,
                                          @Nullable String commitMessage,
                                          Collection<Change> changes,
                                          @Nullable LocalChangeList changeList) {
            PerforceRunner runner = PerforceRunner.getInstance(project);
            MultiMap<P4Connection, FilePath> connections = getConnections(project, changes);

            List<File> addedChanges = new ArrayList<>();
            for (Change change : changes) {
                ContentRevision afterRevision = change.getAfterRevision();
                if (change.getBeforeRevision() == null && afterRevision != null) {
                    addedChanges.add(afterRevision.getFile().getIOFile());
                }
            }

            RefreshForVcs refreshForVcs = new RefreshForVcs();

            for (Map.Entry<P4Connection, Collection<FilePath>> entry : connections.entrySet()) {
                P4Connection connection = entry.getKey();
                Collection<FilePath> filePaths = entry.getValue();
                try {
                    Long existingList = null;
                    if (changeList != null && changeList.getName().equals(commitMessage)) {
                        existingList = PerforceNumberNameSynchronizer.getInstance(project)
                            .getNumber(connection.getConnectionKey(), StringUtil.notNullize(commitMessage));
                    }
                    long list = existingList != null
                        ? existingList
                        : runner.createChangeList(StringUtil.notNullize(commitMessage), connection, null);

                    List<String> p4Paths = new ArrayList<>();
                    for (FilePath path : filePaths) {
                        p4Paths.add(P4File.create(path).getEscapedPath());
                    }

                    runner.reopen(connection, list, p4Paths);
                    runner.shelve(connection, list, p4Paths);
                    if (PerforceSettings.getSettings(project).REVERT_FILES_AFTER_SHELF) {
                        runner.revertAll(p4Paths, connection);
                    }

                    for (FilePath path : filePaths) {
                        File file = path.getIOFile();
                        if (addedChanges.contains(file)) {
                            FileUtil.delete(file);
                            refreshForVcs.addDeletedFile(file);
                        }
                        else {
                            refreshForVcs.refreshFile(file);
                        }
                    }
                }
                catch (VcsException e) {
                    AbstractVcsHelper.getInstance(project).showError(e, PerforceLocalize.shelve());
                }

                refreshForVcs.run(project);
                VcsDirtyScopeManager.getInstance(project).filePathsDirty(filePaths, Collections.emptyList());
            }
        }

        public static MultiMap<P4Connection, FilePath> getConnections(Project project, Collection<Change> changes) {
            Set<FilePath> paths = new LinkedHashSet<>();
            for (Change change : changes) {
                ContentRevision beforeRevision = change.getBeforeRevision();
                ContentRevision afterRevision = change.getAfterRevision();
                FilePath beforeFile = beforeRevision != null ? beforeRevision.getFile() : null;
                FilePath afterFile = afterRevision != null ? afterRevision.getFile() : null;
                FilePath file = afterFile != null ? afterFile : beforeFile;
                if (file != null && VcsUtil.getVcsFor(project, file) instanceof PerforceVcs) {
                    if (beforeFile != null) {
                        paths.add(beforeFile);
                    }
                    if (afterFile != null) {
                        paths.add(afterFile);
                    }
                }
            }
            return FileGrouper.distributePathsByConnection(paths, project);
        }

        public static boolean supportsShelve(Project project, P4Connection connection) {
            try {
                ServerVersion version = PerforceManager.getInstance(project).getServerVersion(connection);
                return version != null && version.supportsShelve();
            }
            catch (VcsException e) {
                return false;
            }
        }
    }

    public static final class P4ShelveCommitExecutor implements CommitExecutor {
        private final Project myProject;

        P4ShelveCommitExecutor(Project project) {
            myProject = project;
        }

        @Override
        public LocalizeValue getActionText() {
            return PerforceLocalize.shelve();
        }

        @Override
        public CommitSession createCommitSession(CommitContext commitContext) {
            return new P4ShelveCommitSession(myProject);
        }
    }

    private static final class P4ShelveCommitSession implements CommitSession {
        private final Project myProject;

        P4ShelveCommitSession(Project project) {
            myProject = project;
        }

        @Override
        public boolean canExecute(Collection<Change> changes, String commitMessage) {
            return true;
        }

        @Override
        public void execute(Collection<Change> changes, String commitMessage) {
            Handler.shelveChanges(myProject, commitMessage, changes);
        }

        @Override
        public void executionCanceled() {
        }

        @Override
        public @Nullable String getHelpId() {
            return null;
        }
    }
}

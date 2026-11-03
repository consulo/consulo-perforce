// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.actions;

import consulo.document.FileDocumentManager;
import consulo.logging.Logger;
import consulo.perforce.localize.PerforceLocalize;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.ui.ex.awt.DialogBuilder;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.util.collection.MultiMap;
import consulo.util.lang.Pair;
import consulo.versionControlSystem.AbstractVcsHelper;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.change.Change;
import consulo.versionControlSystem.change.VcsDirtyScopeManager;
import consulo.versionControlSystem.ui.VcsChangesBrowser;
import consulo.versionControlSystem.ui.VcsChangesDialogFactory;
import consulo.versionControlSystem.util.VcsUtil;
import consulo.virtualFileSystem.StandardFileSystems;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import org.jetbrains.idea.perforce.application.ShelvedChange;
import org.jetbrains.idea.perforce.merge.PerforceMergeProvider;
import org.jetbrains.idea.perforce.perforce.PerforceRunner;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jspecify.annotations.Nullable;

import javax.swing.JComponent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class ShelfUtils {
    private static final Logger LOG = Logger.getInstance(ShelfUtils.class);

    private ShelfUtils() {
    }

    /**
     * Shows the shelved changes. The actions work on the selected changes, or on all of them when nothing is selected.
     */
    @RequiredUIAccess
    public static void browseShelf(Project project, List<ShelvedChange> shelvedChanges) {
        List<Change> changes = new ArrayList<>();
        for (ShelvedChange shelvedChange : shelvedChanges) {
            changes.add(shelvedChange.toIdeaChange(project));
        }

        DialogBuilder dialogBuilder = new DialogBuilder(project);
        @Nullable VcsChangesBrowser[] browserRef = new VcsChangesBrowser[1];

        DumbAwareAction unshelveAction = new UnshelveFromBrowserAction(project, browserRef);

        VcsChangesBrowser browser = project.getApplication().getInstance(VcsChangesDialogFactory.class)
            .changesBrowser(project)
            .toolbarAction(unshelveAction)
            .build(dialogBuilder);
        browserRef[0] = browser;
        browser.setChanges(changes);

        dialogBuilder.setTitle(PerforceLocalize.shelfChanges());
        dialogBuilder.setActionDescriptors(new DialogBuilder.OkActionDescriptor(), new DialogBuilder.CloseDialogAction());
        dialogBuilder.getOkAction().setText(PerforceLocalize.actionPerforceUnshelveAndDeleteText());
        dialogBuilder.setOkOperation(() -> {
            FileDocumentManager.getInstance().saveAllDocuments(); // to ensure Perforce will see up-to-date file content
            List<Change> selected = getTargetChanges(browser);
            if (!selected.isEmpty()) {
                unshelveChanges(toShelvedChanges(selected), project, true);
                dialogBuilder.getDialogWrapper().close(DialogWrapper.OK_EXIT_CODE);
            }
        });
        dialogBuilder.setCenterPanel((JComponent) TargetAWT.to(browser.getComponent()));
        dialogBuilder.showNotModal();
    }

    private static List<Change> getTargetChanges(VcsChangesBrowser browser) {
        List<Change> selected = browser.getSelectedChanges();
        return selected.isEmpty() ? browser.getChanges() : selected;
    }

    private static List<ShelvedChange> toShelvedChanges(List<Change> changes) {
        List<ShelvedChange> result = new ArrayList<>();
        for (Change change : changes) {
            result.add(((ShelvedChange.IdeaChange) change).getOriginal());
        }
        return result;
    }

    private static final class UnshelveFromBrowserAction extends DumbAwareAction implements AnActionWithSyncUpdate {
        private final Project myProject;
        private final @Nullable VcsChangesBrowser[] myBrowserRef;

        UnshelveFromBrowserAction(Project project, @Nullable VcsChangesBrowser[] browserRef) {
            super(PerforceLocalize.actionPerforceUnshelveText(),
                PerforceLocalize.actionPerforceUnshelveDescription(),
                PlatformIconGroup.vcsUnshelve());
            myProject = project;
            myBrowserRef = browserRef;
        }

        @Override
        public void update(AnActionEvent e) {
            VcsChangesBrowser browser = myBrowserRef[0];
            e.getPresentation().setEnabled(browser != null && !getTargetChanges(browser).isEmpty());
        }

        @Override
        @RequiredUIAccess
        public void actionPerformed(AnActionEvent e) {
            VcsChangesBrowser browser = myBrowserRef[0];
            if (browser == null) {
                return;
            }
            FileDocumentManager.getInstance().saveAllDocuments(); // to ensure Perforce will see up-to-date file content

            unshelveChanges(toShelvedChanges(getTargetChanges(browser)), myProject, false);
        }
    }

    public static MultiMap<Pair<P4Connection, Long>, String> groupByChangeList(List<ShelvedChange> selected) {
        MultiMap<Pair<P4Connection, Long>, String> paths = MultiMap.create();
        for (ShelvedChange original : selected) {
            paths.putValue(Pair.create(original.getConnection(), original.getChangeList()), original.getDepotPath());
        }
        return paths;
    }

    public static void unshelveChanges(List<ShelvedChange> selected, Project project, boolean delete) {
        MultiMap<Pair<P4Connection, Long>, String> paths = groupByChangeList(selected);

        doUnshelve(paths, project);

        if (delete) {
            deleteFromShelf(paths, project);
        }

        List<FilePath> dirtyFiles = new ArrayList<>();
        for (ShelvedChange change : selected) {
            File file = change.getFile();
            if (file != null) {
                dirtyFiles.add(VcsUtil.getFilePath(file));
            }
        }

        for (FilePath dirtyFile : dirtyFiles) {
            VirtualFile file = StandardFileSystems.local().refreshAndFindFileByPath(dirtyFile.getPath());
            if (file != null) {
                VirtualFileUtil.markDirtyAndRefresh(false, false, false, file);
            }
        }

        VcsDirtyScopeManager.getInstance(project).filePathsDirty(dirtyFiles, Collections.emptyList());
    }

    private static void doUnshelve(MultiMap<Pair<P4Connection, Long>, String> paths, Project project) {
        for (Map.Entry<Pair<P4Connection, Long>, Collection<String>> entry : paths.entrySet()) {
            Pair<P4Connection, Long> pair = entry.getKey();
            try {
                PerforceRunner.getInstance(project).unshelve(pair.getFirst(), pair.getSecond(), entry.getValue());
            }
            catch (VcsException e) {
                AbstractVcsHelper.getInstance(project).showError(e, PerforceLocalize.actionPerforceUnshelveText());
            }
        }
    }

    private static void handleUnshelveException(P4Connection connection,
                                                VcsException unshelveException,
                                                Project project,
                                                Collection<String> specs) {
        String msg = unshelveException.getMessage();
        if (msg != null && msg.contains("needs resolve")) {
            LinkedHashSet<VirtualFile> toResolve;
            try {
                toResolve = PerforceRunner.getInstance(project).getResolvedWithConflicts(connection, specs);
            }
            catch (VcsException resolveException) {
                AbstractVcsHelper.getInstance(project).showError(unshelveException, PerforceLocalize.actionPerforceUnshelveText());
                AbstractVcsHelper.getInstance(project).showError(resolveException, PerforceLocalize.actionPerforceUnshelveText());
                return;
            }

            if (!toResolve.isEmpty()) {
                VirtualFileUtil.markDirtyAndRefresh(false, false, false, toResolve.toArray(VirtualFile.EMPTY_ARRAY));
                LOG.info(unshelveException);
                new PerforceMergeProvider(project).showMergeDialog(new ArrayList<>(toResolve));
                return;
            }
        }

        AbstractVcsHelper.getInstance(project).showError(unshelveException, PerforceLocalize.actionPerforceUnshelveText());
    }

    public static void deleteFromShelf(MultiMap<Pair<P4Connection, Long>, String> paths, Project project) {
        for (Map.Entry<Pair<P4Connection, Long>, Collection<String>> entry : paths.entrySet()) {
            Pair<P4Connection, Long> pair = entry.getKey();
            try {
                PerforceRunner.getInstance(project).deleteFromShelf(pair.getFirst(), pair.getSecond(), entry.getValue());
            }
            catch (VcsException e) {
                handleUnshelveException(pair.getFirst(), e, project, entry.getValue());
            }
        }
    }

    public static void deleteFromShelf(List<ShelvedChange> changes, Project project) {
        deleteFromShelf(groupByChangeList(changes), project);
    }
}

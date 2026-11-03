// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.actions;

import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.application.SaveAndSyncHandler;
import consulo.application.progress.PerformInBackgroundOption;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.progress.Task;
import consulo.perforce.localize.PerforceLocalize;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.versionControlSystem.AbstractVcsHelper;
import consulo.versionControlSystem.VcsDataKeys;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.change.ChangeList;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import org.jetbrains.idea.perforce.perforce.P4File;
import org.jetbrains.idea.perforce.perforce.PerforceChangeList;
import org.jetbrains.idea.perforce.perforce.PerforceRunner;
import org.jetbrains.idea.perforce.perforce.connections.PerforceConnectionManager;

import java.util.Set;

@ActionImpl(id = "Perforce.SyncToRevision", parents = @ActionParentRef(@ActionRef(id = "IncomingChangesToolbar")))
public class SyncToRevisionAction extends DumbAwareAction implements AnActionWithSyncUpdate {
    private static final String REFRESH_INCOMING_CHANGES_ACTION = "IncomingChanges.Refresh";

    public SyncToRevisionAction() {
        super(PerforceLocalize.actionPerforceSynctorevisionText());
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        if (project == null) {
            return;
        }
        String revision = String.valueOf(((PerforceChangeList) e.getRequiredData(VcsDataKeys.CHANGE_LISTS)[0]).getNumber());

        Set<VirtualFile> roots = PerforceConnectionManager.getInstance(project).getAllConnections().keySet();
        ProgressManager.getInstance().run(new Task.Backgroundable(project, PerforceLocalize.projectUpdating(), true,
            PerformInBackgroundOption.ALWAYS_BACKGROUND) {
            @Override
            public void run(ProgressIndicator indicator) {
                try {
                    for (VirtualFile root : roots) {
                        PerforceRunner.getInstance(project).sync(P4File.create(root), revision);
                    }
                }
                catch (VcsException error) {
                    AbstractVcsHelper.getInstance(project).showError(error, PerforceLocalize.syncError());
                }
            }

            @Override
            @RequiredUIAccess
            public void onSuccess() {
                refreshIncomingChanges();
                SaveAndSyncHandler.getInstance().refreshOpenFiles();
                VirtualFileUtil.markDirtyAndRefresh(true, true, true, roots.toArray(VirtualFile.EMPTY_ARRAY));
            }
        });
    }

    /**
     * Runs the platform "refresh incoming changes" action (its implementation is not part of the platform API).
     */
    @RequiredUIAccess
    private static void refreshIncomingChanges() {
        ActionManager actionManager = ActionManager.getInstance();
        AnAction action = actionManager.getAction(REFRESH_INCOMING_CHANGES_ACTION);
        if (action != null) {
            actionManager.tryToExecute(action, null, null, null, true);
        }
    }

    @Override
    public void update(AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        ChangeList[] lists = e.getData(VcsDataKeys.CHANGE_LISTS);
        e.getPresentation()
            .setEnabledAndVisible(project != null && lists != null && lists.length == 1 && lists[0] instanceof PerforceChangeList);
    }
}

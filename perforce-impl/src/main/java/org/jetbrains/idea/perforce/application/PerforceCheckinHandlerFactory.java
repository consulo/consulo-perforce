// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).

package org.jetbrains.idea.perforce.application;

import consulo.annotation.component.ExtensionImpl;
import consulo.disposer.Disposable;
import consulo.perforce.localize.PerforceLocalize;
import consulo.platform.base.localize.CommonLocalize;
import consulo.project.Project;
import consulo.ui.ex.awt.Messages;
import consulo.util.collection.MultiMap;
import consulo.versionControlSystem.ProjectLevelVcsManager;
import consulo.versionControlSystem.change.Change;
import consulo.versionControlSystem.change.ChangeListManager;
import consulo.versionControlSystem.change.CommitExecutor;
import consulo.versionControlSystem.change.LocalChangeList;
import consulo.versionControlSystem.change.LocalCommitExecutor;
import consulo.versionControlSystem.checkin.BeforeCheckinDialogHandler;
import consulo.versionControlSystem.checkin.CheckinHandler;
import consulo.versionControlSystem.checkin.CheckinProjectPanel;
import consulo.versionControlSystem.checkin.VcsCheckinHandlerFactory;
import consulo.versionControlSystem.ui.CheckBoxRefreshableOnComponent;
import consulo.versionControlSystem.ui.RefreshableOnComponent;
import org.jetbrains.idea.perforce.actions.RevertAllUnchangedFilesAction;
import org.jetbrains.idea.perforce.actions.ShelfUtils;
import org.jetbrains.idea.perforce.perforce.PerforceSettings;
import org.jetbrains.idea.perforce.perforce.login.PerforceLoginManager;
import org.jspecify.annotations.Nullable;

import java.util.List;

@ExtensionImpl
public class PerforceCheckinHandlerFactory extends VcsCheckinHandlerFactory {
    public PerforceCheckinHandlerFactory() {
        super(PerforceVcs.getKey());
    }

    @Override
    protected CheckinHandler createVcsHandler(CheckinProjectPanel panel) {
        Project project = panel.getProject();
        return new CheckinHandler() {
            @Override
            public @Nullable RefreshableOnComponent getBeforeCheckinConfigurationPanel() {
                return new CheckBoxRefreshableOnComponent(
                    PerforceLocalize.messageRevertUnchangedFiles(),
                    () -> PerforceSettings.getSettings(project).REVERT_UNCHANGED_FILES_CHECKIN,
                    value -> PerforceSettings.getSettings(project).REVERT_UNCHANGED_FILES_CHECKIN = value
                );
            }

            @Override
            public @Nullable RefreshableOnComponent getAfterCheckinConfigurationPanel(Disposable parentDisposable) {
                // the commit panel does not expose its executors, so the option is always offered
                return new CheckBoxRefreshableOnComponent(
                    PerforceLocalize.checkboxRevertFilesAfterShelf(),
                    () -> PerforceSettings.getSettings(project).REVERT_FILES_AFTER_SHELF,
                    value -> PerforceSettings.getSettings(project).REVERT_FILES_AFTER_SHELF = value
                );
            }

            @Override
            public ReturnResult beforeCheckin() {
                if (PerforceSettings.getSettings(project).REVERT_UNCHANGED_FILES_CHECKIN) {
                    RevertAllUnchangedFilesAction.revertUnchanged(project, panel.getVirtualFiles(), panel, null);
                }
                MultiMap<LocalChangeList, Change> map = MultiMap.create();
                for (Change change : panel.getSelectedChanges()) {
                    LocalChangeList list = ChangeListManager.getInstance(project).getChangeList(change);
                    if (list != null) {
                        map.putValue(list, change);
                    }
                }
                for (LocalChangeList list : map.keySet()) {
                    List<ShelvedChange> shelvedChanges = PerforceManager.getInstance(project).getShelf().getShelvedChanges(list);
                    if (!shelvedChanges.isEmpty()) {
                        String message = PerforceLocalize.changelistHasShelvedChanges(list.getName()).get();
                        int rc = Messages.showYesNoDialog(
                            project,
                            message,
                            PerforceLocalize.changelistShelvedChangesFound().get(),
                            PerforceLocalize.changelistRemoveShelvedChanges().get(),
                            CommonLocalize.buttonCancel().get(),
                            Messages.getQuestionIcon()
                        );
                        if (rc == Messages.NO) {
                            return ReturnResult.CANCEL;
                        }
                        ShelfUtils.deleteFromShelf(shelvedChanges, project);
                    }
                }

                return super.beforeCheckin();
            }
        };
    }

    @Override
    public BeforeCheckinDialogHandler createSystemReadyHandler(Project project) {
        return new MyBeforeCheckinDialogHandler();
    }

    public static boolean beforeRemoteOperationCheck(Project project, String operationName) {
        PerforceSettings settings = PerforceSettings.getSettings(project);
        if (!settings.ENABLED) {
            int result = Messages.showYesNoDialog(
                project,
                PerforceLocalize.connectionOfflineGoOnline(operationName).get(),
                PerforceLocalize.connectionOfflineTitle().get(),
                Messages.getWarningIcon()
            );
            if (Messages.YES == result) {
                settings.enable();
            }
        }
        boolean allOk = PerforceLoginManager.getInstance(project).checkAndRepairAll();
        return ProjectLevelVcsManager.getInstance(project).getAllActiveVcss().length > 1 || allOk && settings.ENABLED;
    }

    private static class MyBeforeCheckinDialogHandler extends BeforeCheckinDialogHandler {
        @Override
        public boolean beforeCommitDialogShown(
            Project project,
            List<Change> changes,
            Iterable<CommitExecutor> executors,
            boolean showVcsCommit
        ) {
            if (showVcsCommit) {
                return beforeRemoteOperationCheck(project, "Commit");
            }

            for (CommitExecutor executor : executors) {
                if (!(executor instanceof LocalCommitExecutor)) {
                    return beforeRemoteOperationCheck(project, "Commit");
                }
            }
            return true;
        }
    }
}

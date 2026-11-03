// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.actions;

import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.annotation.component.ActionRefAnchor;
import consulo.perforce.localize.PerforceLocalize;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.ex.awt.Messages;
import consulo.versionControlSystem.VcsDataKeys;
import consulo.versionControlSystem.change.ChangeList;
import consulo.versionControlSystem.change.LocalChangeList;
import org.jetbrains.idea.perforce.application.ConnectionKey;
import org.jetbrains.idea.perforce.application.PerforceNumberNameSynchronizer;
import org.jetbrains.idea.perforce.perforce.PerforceSettings;
import org.jetbrains.idea.perforce.perforce.jobs.EditChangelistJobsDialog;
import org.jetbrains.idea.perforce.perforce.jobs.JobDetailsLoader;
import org.jetbrains.idea.perforce.perforce.jobs.P4JobsLogicConn;
import org.jetbrains.idea.perforce.perforce.jobs.PerforceJob;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ActionImpl(
    id = "SetJobsForChangeList",
    parents = @ActionParentRef(
        value = @ActionRef(id = "ChangesViewPopupMenu"),
        anchor = ActionRefAnchor.AFTER,
        relatedToAction = @ActionRef(id = "EditSource")
    )
)
public class LinkChangeListToJobsAction extends AnAction implements AnActionWithSyncUpdate {
    public LinkChangeListToJobsAction() {
        super(PerforceLocalize.actionSetjobsforchangelistText());
    }

    @Override
    public void update(AnActionEvent e) {
        e.getPresentation().setText(PerforceLocalize.actionLinkChangelistToJobs());
        if (!enabled(e)) {
            e.getPresentation().setEnabledAndVisible(false);
            return;
        }
        e.getPresentation().setEnabledAndVisible(true);
    }

    private static boolean enabled(AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        if (project == null || project.isDefault()) {
            return false;
        }
        if (!PerforceSettings.getSettings(project).ENABLED) {
            return false;
        }
        if (!PerforceSettings.getSettings(project).USE_PERFORCE_JOBS) {
            return false;
        }
        ChangeList[] lists = e.getData(VcsDataKeys.CHANGE_LISTS);
        if (lists == null || (lists.length != 1)) {
            return false;
        }
        return !PerforceNumberNameSynchronizer.getInstance(project).getAllNumbers(lists[0].getName()).isEmpty();
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(AnActionEvent e) {
        if (!enabled(e)) {
            return;
        }
        Project project = e.getRequiredData(Project.KEY);
        ChangeList[] lists = e.getRequiredData(VcsDataKeys.CHANGE_LISTS);
        LocalChangeList list = (LocalChangeList) lists[0];

        JobDetailsLoader loader = new JobDetailsLoader(project);
        Map<ConnectionKey, P4JobsLogicConn> connMap = new HashMap<>();
        Map<ConnectionKey, List<PerforceJob>> perforceJobs = new HashMap<>();
        loader.loadJobsForList(list, connMap, perforceJobs);
        if (perforceJobs.isEmpty()) {
            Messages.showInfoMessage(PerforceLocalize.connectionNoValidConnections().get(), PerforceLocalize.perforce().get());
            return;
        }

        new EditChangelistJobsDialog(project, list, false, connMap, perforceJobs).show();
    }
}

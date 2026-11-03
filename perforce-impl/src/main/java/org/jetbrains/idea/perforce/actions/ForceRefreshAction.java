// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.actions;

import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.annotation.component.ActionRefAnchor;
import consulo.perforce.localize.PerforceLocalize;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.versionControlSystem.ProjectLevelVcsManager;
import org.jetbrains.idea.perforce.application.PerforceChangeProvider;
import org.jetbrains.idea.perforce.application.PerforceVcs;
import org.jetbrains.idea.perforce.perforce.PerforceSettings;

@ActionImpl(
    id = "Perforce.Force.Refresh",
    parents = @ActionParentRef(
        value = @ActionRef(id = "ChangesViewToolbar"),
        anchor = ActionRefAnchor.AFTER,
        relatedToAction = @ActionRef(id = "ChangesView.Refresh")
    )
)
public class ForceRefreshAction extends DumbAwareAction implements AnActionWithSyncUpdate {
    public ForceRefreshAction() {
        super(PerforceLocalize.actionPerforceForceRefreshText(),
            PerforceLocalize.refreshForceDescription(),
            PlatformIconGroup.actionsForcerefresh());
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        if (project == null) {
            return;
        }
        PerforceVcs vcs = PerforceVcs.getInstance(project);
        // mark everything dirty is inside
        ((PerforceChangeProvider) vcs.getChangeProvider()).discardCache();
    }

    @Override
    public void update(AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        e.getPresentation().setVisible(project != null &&
            ProjectLevelVcsManager.getInstance(project).checkVcsIsActive(PerforceVcs.NAME) &&
            PerforceSettings.getSettings(project).ENABLED);
    }
}

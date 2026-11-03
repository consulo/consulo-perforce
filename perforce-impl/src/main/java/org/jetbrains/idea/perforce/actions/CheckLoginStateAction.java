// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.actions;

import consulo.annotation.component.ActionImpl;
import consulo.perforce.localize.PerforceLocalize;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.versionControlSystem.ProjectLevelVcsManager;
import org.jetbrains.idea.perforce.application.PerforceVcs;
import org.jetbrains.idea.perforce.perforce.PerforceSettings;
import org.jetbrains.idea.perforce.perforce.login.PerforceLoginManager;

@ActionImpl(id = "PerforceCheckLoginStateAction")
public class CheckLoginStateAction extends DumbAwareAction implements AnActionWithSyncUpdate {
    public CheckLoginStateAction() {
        super(PerforceLocalize.actionPerforcecheckloginstateactionText());
    }

    @Override
    public void update(AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        e.getPresentation().setVisible(project != null &&
            ProjectLevelVcsManager.getInstance(project).checkVcsIsActive(PerforceVcs.NAME) &&
            PerforceSettings.getSettings(project).ENABLED);
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        if (project == null) {
            return;
        }
        PerforceLoginManager.getInstance(project).checkAndRepairAll();
    }
}

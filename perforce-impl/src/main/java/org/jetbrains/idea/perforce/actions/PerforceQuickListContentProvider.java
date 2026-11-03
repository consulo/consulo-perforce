// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.actions;

import consulo.annotation.component.ExtensionImpl;
import consulo.dataContext.DataContext;
import consulo.project.Project;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnSeparator;
import consulo.ui.ex.action.IdeActions;
import consulo.versionControlSystem.AbstractVcs;
import consulo.versionControlSystem.action.VcsQuickListContentProvider;
import org.jetbrains.idea.perforce.application.PerforceVcs;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Replaces the VCS quick list with the common VCS actions followed by the Perforce specific ones.
 *
 * @author Roman.Chernyatchik
 */
@ExtensionImpl
public class PerforceQuickListContentProvider implements VcsQuickListContentProvider {
    @Override
    public @Nullable List<AnAction> getVcsActions(@Nullable Project project,
                                                  @Nullable AbstractVcs activeVcs,
                                                  @Nullable DataContext dataContext) {
        if (activeVcs == null || !PerforceVcs.NAME.equals(activeVcs.getId())) {
            return null;
        }

        ActionManager manager = ActionManager.getInstance();
        List<AnAction> actions = new ArrayList<>();

        actions.add(AnSeparator.create(activeVcs.getDisplayName()));
        add("CheckinProject", manager, actions);
        add("CheckinFiles", manager, actions);
        add(IdeActions.CHANGES_VIEW_REVERT, manager, actions);

        actions.add(AnSeparator.create());
        add("Vcs.ShowTabbedFileHistory", manager, actions);
        add("Annotate", manager, actions);
        add("Compare.SameVersion", manager, actions);

        actions.add(AnSeparator.create());
        actions.addAll(collectVcsSpecificActions(manager));
        return actions;
    }

    @Override
    public @Nullable List<AnAction> getNotInVcsActions(@Nullable Project project, @Nullable DataContext dataContext) {
        return null;
    }

    @Override
    public boolean replaceVcsActionsFor(AbstractVcs activeVcs, @Nullable DataContext dataContext) {
        return PerforceVcs.NAME.equals(activeVcs.getId());
    }

    protected List<AnAction> collectVcsSpecificActions(ActionManager manager) {
        List<AnAction> actions = new ArrayList<>();
        add(IdeActions.MOVE_TO_ANOTHER_CHANGE_LIST, manager, actions);
        add("PerforceDirect.Edit", manager, actions);
        add("RevertUnchanged", manager, actions);
        add("PerforceEnableIntegration", manager, actions);
        return actions;
    }

    private static void add(String actionName, ActionManager manager, List<AnAction> actions) {
        AnAction action = manager.getAction(actionName);
        assert action != null : "Can not find action " + actionName;
        actions.add(action);
    }
}

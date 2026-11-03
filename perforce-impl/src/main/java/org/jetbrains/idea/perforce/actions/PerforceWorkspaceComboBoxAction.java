// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.actions;

import consulo.annotation.component.ActionImpl;
import consulo.application.dumb.DumbAware;
import consulo.dataContext.DataContext;
import consulo.localize.LocalizeValue;
import consulo.perforce.localize.PerforceLocalize;
import consulo.project.Project;
import consulo.ui.ex.action.ActionGroup;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.ComboBoxAction;
import consulo.ui.ex.action.DefaultActionGroup;
import consulo.ui.ex.action.Presentation;
import org.jetbrains.idea.perforce.application.PerforceManager;
import org.jetbrains.idea.perforce.perforce.PerforceSettings;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jetbrains.idea.perforce.perforce.connections.PerforceConnectionManager;

import org.jspecify.annotations.Nullable;

import java.util.Collection;

/**
 * Shows the current Perforce workspace; lists all workspaces when there are several, and goes online when clicked while offline.
 */
@ActionImpl(id = "Perforce.Toolbar.WorkspaceAction")
public class PerforceWorkspaceComboBoxAction extends ComboBoxAction implements DumbAware {
    private static final Runnable NO_POPUP = () -> {
    };

    public PerforceWorkspaceComboBoxAction() {
        getTemplatePresentation().setText(PerforceLocalize.actionPerforceToolbarWorkspaceactionText());
        getTemplatePresentation().setDescription(PerforceLocalize.actionPerforceToolbarWorkspaceactionDescription());
    }

    @Override
    public boolean shouldShowDisabledActions() {
        return true;
    }

    @Override
    public void update(AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        Presentation presentation = e.getPresentation();
        if (project == null || !PerforceManager.getInstance(project).isActive()) {
            presentation.setEnabledAndVisible(false);
            return;
        }

        PerforceConnectionManager perforceConnectionManager = (PerforceConnectionManager) PerforceConnectionManager.getInstance(project);
        if (!perforceConnectionManager.isInitialized()) {
            perforceConnectionManager.scheduleInitialization();
            presentation.setEnabledAndVisible(false);
            return;
        }

        PerforceSettings perforceSettings = PerforceSettings.getSettings(project);
        Collection<P4Connection> allConnections = perforceSettings.getAllConnections();

        P4Connection connection = PerforceToolbarWidgetHelper.getConnection(e, perforceSettings);
        String workspace = connection != null ? connection.getConnectionKey().getClient() : null;
        boolean isNoConnections = allConnections.isEmpty() || allConnections.size() == 1 && (workspace == null || workspace.isEmpty());

        presentation.setEnabledAndVisible(true);
        presentation.setIcon(PerforceToolbarWidgetHelper.getIcon(perforceSettings, isNoConnections));
        presentation.setDescription(getDescription(workspace, isNoConnections, perforceSettings.ENABLED));
        presentation.setText(PerforceToolbarWidgetHelper.getText(workspace, isNoConnections, perforceSettings.ENABLED));
        presentation.setDisabledMnemonic(true);

        boolean isMultipleWorkspaces = allConnections.size() > 1;
        boolean isOnline = connection != null ? connection.isConnected() : perforceSettings.ENABLED;
        @Nullable Runnable onClick;
        if (!isOnline) {
            onClick = () -> PerforceSettings.getSettings(project).enable();
        }
        else if (!isMultipleWorkspaces) {
            onClick = NO_POPUP;
        }
        else {
            onClick = null;
        }
        presentation.putClientProperty(LIKE_BUTTON, onClick);
    }

    private static LocalizeValue getDescription(@Nullable String workspace, boolean isNoConnections, boolean isOnline) {
        if (!isOnline && !isNoConnections) {
            return LocalizeValue.join(PerforceLocalize.connectionCannotConnect(),
                LocalizeValue.of("\n"),
                PerforceLocalize.connectionCannotConnectClickToGoOnline());
        }

        return PerforceToolbarWidgetHelper.getDescription(workspace, isNoConnections, isOnline);
    }

    @Override
    protected ActionGroup createPopupActionGroup(DataContext dataContext) {
        DefaultActionGroup actionGroup = new DefaultActionGroup();
        Project project = dataContext.getData(Project.KEY);
        if (project == null) {
            return actionGroup;
        }

        PerforceSettings perforceSettings = PerforceSettings.getSettings(project);
        for (P4Connection connection : perforceSettings.getAllConnections()) {
            actionGroup.add(new PerforceToolbarWidgetHelper.WorkspaceAction(connection.getConnectionKey().getClient(),
                connection.getWorkingDir()));
        }

        return actionGroup;
    }
}

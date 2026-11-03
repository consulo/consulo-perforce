// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.actions;

import consulo.application.dumb.DumbAware;
import consulo.fileEditor.FileEditor;
import consulo.localize.LocalizeValue;
import consulo.perforce.localize.PerforceLocalize;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.image.Image;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.idea.perforce.perforce.PerforceSettings;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jspecify.annotations.Nullable;

import java.util.Collection;

public final class PerforceToolbarWidgetHelper {
    private PerforceToolbarWidgetHelper() {
    }

    public static @Nullable P4Connection getConnection(AnActionEvent e, PerforceSettings settings) {
        Collection<P4Connection> allConnections = settings.getAllConnections();
        if (allConnections.size() == 1) {
            return allConnections.iterator().next();
        }

        FileEditor editor = e.getData(FileEditor.KEY);
        VirtualFile file = editor != null ? editor.getFile() : null;
        if (file == null) {
            file = e.getData(VirtualFile.KEY);
        }
        if (file == null) {
            file = e.getData(Project.PROJECT_FILE_DIRECTORY);
        }
        if (file == null) {
            return allConnections.isEmpty() ? null : allConnections.iterator().next();
        }

        return settings.getConnectionForFile(file);
    }

    /**
     * The widget text: the workspace name, followed by the offline state when the integration works offline.
     */
    public static LocalizeValue getText(@Nullable String workspace, boolean isNoConnections, boolean isOnline) {
        if (isNoConnections) {
            return PerforceLocalize.connectionNoValidConnectionsShort();
        }

        LocalizeValue text = workspace != null ? LocalizeValue.of(workspace) : PerforceLocalize.actionPerforceToolbarMultipleWorkspaces();

        if (isOnline) {
            return text;
        }

        return LocalizeValue.join(text, LocalizeValue.space(), PerforceLocalize.connectionStatusOffline());
    }

    public static LocalizeValue getDescription(@Nullable String workspace, boolean isNoConnections, boolean isOnline) {
        if (!isOnline) {
            return PerforceLocalize.connectionCannotConnect();
        }
        if (isNoConnections) {
            return PerforceLocalize.connectionNoValidConnections();
        }

        if (workspace != null) {
            return PerforceLocalize.actionPerforceToolbarWorkspaceactionDescriptionTemplate(workspace);
        }

        return PerforceLocalize.actionPerforceToolbarMultipleWorkspacesDescription();
    }

    public static Image getIcon(PerforceSettings settings, boolean isNoConnections) {
        if (isNoConnections || !settings.ENABLED) {
            return PlatformIconGroup.generalWarning();
        }

        return PlatformIconGroup.vcsBranch();
    }

    /**
     * A disabled item showing a workspace and its directory.
     */
    public static class WorkspaceAction extends AnAction implements DumbAware, AnActionWithSyncUpdate {
        private final LocalizeValue myWorkspaceLabel;

        public WorkspaceAction(String workspace, String workspaceDir) {
            myWorkspaceLabel = LocalizeValue.of(workspace + " (" + workspaceDir + ")");
        }

        @Override
        public void update(AnActionEvent e) {
            e.getPresentation().setText(myWorkspaceLabel);
            e.getPresentation().setDisabledMnemonic(true);
            e.getPresentation().setEnabled(false);
        }

        @Override
        public void actionPerformed(AnActionEvent e) {
        }
    }
}

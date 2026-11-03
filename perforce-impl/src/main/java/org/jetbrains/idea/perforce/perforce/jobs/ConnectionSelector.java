// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.jobs;

import consulo.perforce.localize.PerforceLocalize;
import consulo.project.Project;
import consulo.ui.ex.popup.BaseListPopupStep;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.ui.ex.popup.PopupStep;
import consulo.versionControlSystem.change.ChangesUtil;
import consulo.versionControlSystem.change.LocalChangeList;
import org.jetbrains.idea.perforce.application.ConnectionKey;
import org.jetbrains.idea.perforce.application.PerforceNumberNameSynchronizer;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jetbrains.idea.perforce.perforce.connections.PerforceConnectionManager;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class ConnectionSelector {
    private ConnectionSelector() {
    }

    public static Map<ConnectionKey, P4Connection> getConnections(Project project, LocalChangeList list) {
        Map<ConnectionKey, P4Connection> result = new HashMap<>();
        List<File> files = ChangesUtil.getIoFilesFromChanges(list.getChanges());
        for (P4Connection connection : PerforceConnectionManager.getInstance(project).getAllConnections().values()) {
            Long number = PerforceNumberNameSynchronizer.getInstance(project).getNumber(connection.getConnectionKey(), list.getName());
            if (number != null || files.stream().anyMatch(connection::handlesFile)) {
                result.put(connection.getConnectionKey(), connection);
            }
        }

        return result;
    }

    public static void selectConnection(Map<ConnectionKey, P4Connection> map, Consumer<@Nullable ConnectionKey> consumer) {
        List<ConnectionKey> keys = new ArrayList<>(map.keySet());
        BaseListPopupStep<ConnectionKey> popup = new BaseListPopupStep<>(PerforceLocalize.connectionSelectTitle().get(), keys) {
            @Override
            public @Nullable PopupStep onChosen(@Nullable ConnectionKey selectedValue, boolean finalChoice) {
                consumer.accept(selectedValue);
                return FINAL_CHOICE;
            }
        };
        JBPopupFactory.getInstance().createListPopup(popup).showInFocusCenter();
    }
}

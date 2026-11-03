// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.actions;

import consulo.document.FileDocumentManager;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.ui.image.Image;
import consulo.versionControlSystem.VcsDataKeys;
import consulo.versionControlSystem.change.Change;
import org.jetbrains.idea.perforce.application.ShelvedChange;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class UnshelveActionBase extends DumbAwareAction implements AnActionWithSyncUpdate {
    private final boolean myDelete;

    public UnshelveActionBase(boolean delete, LocalizeValue text, LocalizeValue description, @Nullable Image icon) {
        super(text, description, icon);
        myDelete = delete;
    }

    public boolean getDelete() {
        return myDelete;
    }

    @Override
    public void update(AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        Change[] changes = e.getData(VcsDataKeys.CHANGES);
        if (changes == null || project == null) {
            e.getPresentation().setEnabledAndVisible(false);
            return;
        }

        List<Change> shelved = new ArrayList<>();
        for (Change change : changes) {
            if (change instanceof ShelvedChange.IdeaChange) {
                shelved.add(change);
            }
        }
        Set<P4Connection> connections = ShelveAction.Handler.getConnections(project, shelved).keySet();
        e.getPresentation().setEnabledAndVisible(!connections.isEmpty() && ShelveAction.allSupportShelve(project, connections));
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(AnActionEvent e) {
        FileDocumentManager.getInstance().saveAllDocuments();
        Change[] changes = e.getData(VcsDataKeys.CHANGES);
        Project project = e.getData(Project.KEY);
        if (changes == null || project == null) {
            return;
        }

        List<ShelvedChange> shelvedChanges = new ArrayList<>();
        for (Change change : changes) {
            shelvedChanges.add(((ShelvedChange.IdeaChange) change).getOriginal());
        }
        ShelfUtils.unshelveChanges(shelvedChanges, project, myDelete);
    }
}

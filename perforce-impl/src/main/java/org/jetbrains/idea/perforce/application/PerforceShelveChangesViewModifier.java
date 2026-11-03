// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.application;

import consulo.annotation.component.ExtensionImpl;
import consulo.perforce.localize.PerforceLocalize;
import consulo.project.Project;
import consulo.versionControlSystem.ProjectLevelVcsManager;
import consulo.versionControlSystem.change.Change;
import consulo.versionControlSystem.change.ChangeListManager;
import consulo.versionControlSystem.change.ChangesViewModelBuilder;
import consulo.versionControlSystem.change.ChangesViewModifier;
import consulo.versionControlSystem.change.LocalChangeList;
import jakarta.inject.Inject;

import java.util.List;

/**
 * Shows the changes shelved on the server under their change list in the Local Changes view.
 */
@ExtensionImpl
public class PerforceShelveChangesViewModifier implements ChangesViewModifier {
    private final Project myProject;

    @Inject
    public PerforceShelveChangesViewModifier(Project project) {
        myProject = project;
    }

    @Override
    public void modifyTreeModelBuilder(ChangesViewModelBuilder builder) {
        if (!ProjectLevelVcsManager.getInstance(myProject).checkVcsIsActive(PerforceVcs.NAME)) {
            return;
        }
        PerforceShelf shelf = PerforceManager.getInstance(myProject).getShelf();
        for (LocalChangeList changeList : ChangeListManager.getInstance(myProject).getChangeLists()) {
            List<Change> shelvedChanges = shelf.getShelvedChanges(changeList).stream()
                .map(change -> change.toIdeaChange(myProject))
                .toList();
            if (!shelvedChanges.isEmpty()) {
                builder.insertChangesGroup(changeList, PerforceLocalize.shelfChangesNodeTitle(), shelvedChanges);
            }
        }
    }
}

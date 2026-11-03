// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.application;

import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import consulo.perforce.localize.PerforceLocalize;
import consulo.project.Project;
import consulo.ui.TextAttribute;
import consulo.ui.TextItemPresentation;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;
import consulo.versionControlSystem.change.ChangeListDecorator;
import consulo.versionControlSystem.change.LocalChangeList;
import jakarta.inject.Inject;

import java.util.Collection;

@ExtensionImpl
public final class PerforceLocalChangeListDecorator implements ChangeListDecorator {
  private final Project myProject;

  @Inject
  public PerforceLocalChangeListDecorator(Project project) {
    myProject = project;
  }

  @Override
  public void decorateChangeList(LocalChangeList changeList,
                                 TextItemPresentation presentation,
                                 boolean selected,
                                 boolean expanded,
                                 boolean hasFocus) {
    if (changeList.hasDefaultName()) {
      return;
    }

    Collection<? extends Long> collNumbers = PerforceNumberNameSynchronizer.getInstance(myProject).getAllNumbers(changeList.getName()).values();
    if (collNumbers.isEmpty()) {
      return;
    }

    LocalizeValue text = PerforceLocalize.changeListDecoration(StringUtil.join(ContainerUtil.sorted(collNumbers), ", "));
    int shelved = PerforceManager.getInstance(myProject).getShelf().getShelvedChanges(changeList).size();
    if (shelved > 0) {
      text = LocalizeValue.join(text, PerforceLocalize.changeListDecorationSuffix(shelved));
    }

    presentation.append(text, TextAttribute.GRAYED);
  }
}

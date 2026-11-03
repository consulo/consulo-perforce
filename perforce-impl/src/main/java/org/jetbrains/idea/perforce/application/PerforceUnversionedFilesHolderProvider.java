// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.application;

import consulo.annotation.component.ExtensionImpl;
import consulo.project.Project;
import consulo.versionControlSystem.change.VcsManagedFilesHolder;
import consulo.versionControlSystem.change.VcsManagedUnversionedFilesHolderProvider;
import jakarta.inject.Inject;

@ExtensionImpl
public class PerforceUnversionedFilesHolderProvider implements VcsManagedUnversionedFilesHolderProvider {
  private final Project myProject;

  @Inject
  public PerforceUnversionedFilesHolderProvider(Project project) {
    myProject = project;
  }

  @Override
  public PerforceVcs getVcs() {
    return PerforceVcs.getInstance(myProject);
  }

  @Override
  public VcsManagedFilesHolder createHolder() {
    return new PerforceUnversionedFilesHolder(getVcs().getOnlineChangeProvider().getUnversionedTracker());
  }
}

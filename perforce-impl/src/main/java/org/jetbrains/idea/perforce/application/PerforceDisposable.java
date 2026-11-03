// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.application;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.disposer.Disposable;
import consulo.project.Project;
import jakarta.inject.Singleton;

/**
 * Project-level parent disposable of the plugin: disposed together with the project.
 */
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
@Singleton
public final class PerforceDisposable implements Disposable {
  public static PerforceDisposable getInstance(Project project) {
    return project.getInstance(PerforceDisposable.class);
  }

  @Override
  public void dispose() {
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.application;

import consulo.project.Project;
import consulo.versionControlSystem.VcsException;
import org.jetbrains.idea.perforce.perforce.View;
import org.jspecify.annotations.Nullable;

import java.util.List;

public interface PerforceClient {

  /**
   * @return the project, may be {@code null} in tests
   */
  @Nullable Project getProject();

  @Nullable String getName() throws VcsException;

  /**
   * @return a list of the main root and all AltRoots
   */
  List<String> getRoots() throws VcsException;

  List<View> getViews() throws VcsException;

  @Nullable List<String> getCachedOptions();

  @Nullable String getUserName() throws VcsException;

  /**
   * @return server+port taken from 'p4 info' output. Doesn't contain prefixes like 'ssl:', that can be retrieved from {@link #getDeclaredServerPort()}
   */
  @Nullable String getServerPort() throws VcsException;

  /**
   * @return server+port taken from connection
   */
  @Nullable String getDeclaredServerPort();
}

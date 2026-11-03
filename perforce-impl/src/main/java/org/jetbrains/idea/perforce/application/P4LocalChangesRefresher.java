// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.application;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.TopicImpl;
import consulo.application.util.registry.Registry;
import consulo.project.Project;
import consulo.project.ui.wm.IdeFrame;
import consulo.project.ui.wm.event.ApplicationActivationListener;
import consulo.versionControlSystem.change.VcsDirtyScopeManager;
import org.jetbrains.idea.perforce.perforce.connections.PerforceConnectionManager;
import org.jetbrains.idea.perforce.perforce.connections.PerforceConnectionManagerI;

/**
 * Refreshes the roots of mapped connections when the application frame is activated, so that changelists
 * created outside the IDE show up in Local Changes. Controlled by the {@code p4.refresh.local.changes.on.frame.activation}
 * registry key (disabled by default).
 */
@TopicImpl(ComponentScope.APPLICATION)
public class P4LocalChangesRefresher implements ApplicationActivationListener {
  @Override
  public void applicationActivated(IdeFrame ideFrame) {
    if (!Registry.is("p4.refresh.local.changes.on.frame.activation", false)) {
      return;
    }
    Project project = ideFrame.getProject();
    if (project == null) {
      return;
    }

    PerforceConnectionManagerI connectionManager = PerforceConnectionManager.getInstance(project);
    VcsDirtyScopeManager dirtyScopeManager = VcsDirtyScopeManager.getInstance(project);
    connectionManager.getAllConnections().forEach((root, connection) -> dirtyScopeManager.dirDirtyRecursively(root));
  }
}

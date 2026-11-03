// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.application;

import org.jetbrains.idea.perforce.perforce.connections.PerforceMultipleConnections;

public interface ConnectionDiagnoseRefresher {
  void refresh();
  PerforceMultipleConnections getMultipleConnections();
  P4RootsInformation getP4RootsInformation();
}

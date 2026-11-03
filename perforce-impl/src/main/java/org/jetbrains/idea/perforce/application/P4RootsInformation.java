// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.application;

import consulo.util.collection.MultiMap;
import consulo.versionControlSystem.VcsException;
import org.jetbrains.idea.perforce.perforce.PerforceAuthenticationException;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;

import java.util.Map;

public interface P4RootsInformation {
  MultiMap<P4Connection, VcsException> getErrors();
  boolean hasAnyErrors();
  boolean hasNotAuthorized();
  boolean hasNoConnections();
  Map<P4Connection, PerforceAuthenticationException> getNotAuthorized();
  Map<P4Connection, PerforceClientRootsChecker.WrongRoots> getMap();
}

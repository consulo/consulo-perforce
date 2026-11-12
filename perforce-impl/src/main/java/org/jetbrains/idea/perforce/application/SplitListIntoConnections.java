// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.idea.perforce.application;

import consulo.project.Project;
import consulo.util.collection.MultiMap;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.change.Change;
import consulo.versionControlSystem.change.ContentRevision;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jetbrains.idea.perforce.perforce.connections.PerforceConnectionManager;
import org.jetbrains.idea.perforce.perforce.connections.PerforceConnectionManagerI;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class SplitListIntoConnections<T> {
  private final MultiMap<ConnectionKey, FilePath> myPaths = MultiMap.createSet();
  private final PerforceConnectionManagerI myConnectionManager;
  private final Function<? super P4Connection, ? extends T> myFactory;
  private final Map<ConnectionKey, T> myByConnectionMap = new HashMap<>();

  public SplitListIntoConnections(Project project, Function<? super P4Connection, ? extends T> factory) {
    myFactory = factory;
    myConnectionManager = PerforceConnectionManager.getInstance(project);
  }

  private void processRevision(ContentRevision contentRevision) throws VcsException {
    FilePath filePath = contentRevision.getFile();
    P4Connection connection = myConnectionManager.getConnectionForFile(filePath);
    if (connection == null) {
      return;
    }
    ConnectionKey key = connection.getConnectionKey();
    myPaths.putValue(key, filePath);
    myByConnectionMap.put(key, myFactory.apply(connection));
  }

  public void execute(Collection<? extends Change> incomingChanges) throws VcsException {
    for (Change incomingChange : incomingChanges) {
      ContentRevision beforeRevision = incomingChange.getBeforeRevision();
      if (beforeRevision != null) {
        processRevision(beforeRevision);
      }
      ContentRevision afterRevision = incomingChange.getAfterRevision();
      if (afterRevision != null) {
        processRevision(afterRevision);
      }
    }
  }

  public MultiMap<ConnectionKey, FilePath> getPaths() {
    return myPaths;
  }

  public Map<ConnectionKey, T> getByConnectionMap() {
    return myByConnectionMap;
  }
}

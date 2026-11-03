// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.application;

import consulo.project.Project;
import consulo.util.collection.HashingStrategy;
import consulo.util.collection.Maps;
import consulo.util.collection.MultiMap;
import consulo.util.lang.Comparing;
import consulo.versionControlSystem.FilePath;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.idea.perforce.perforce.P4File;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jetbrains.idea.perforce.perforce.connections.PerforceConnectionManager;
import org.jetbrains.idea.perforce.perforce.connections.PerforceConnectionManagerI;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.function.Function;

public final class FileGrouper {
  private static <T> MultiMap<P4Connection, T> distributeByConnection(Collection<? extends T> files, Function<? super T, ? extends @Nullable P4Connection> fun) {
    //todo P4ParametersConnection.equals should be based on client+server+user
    MultiMap<P4Connection, T> sortedFiles = new MultiMap<>(Maps.newHashMap(new HashingStrategy<P4Connection>() {
      @Override
      public int hashCode(@Nullable P4Connection object) {
        return object == null ? 0 : object.getConnectionKey().hashCode();
      }

      @Override
      public boolean equals(@Nullable P4Connection o1, @Nullable P4Connection o2) {
        return o1 == o2 || (o1 != null && o2 != null && Comparing.equal(o1.getConnectionKey(), o2.getConnectionKey()));
      }
    }));
    for (T dir : files) {
      P4Connection connection = fun.apply(dir);
      if (connection != null) {
        sortedFiles.putValue(connection, dir);
      }
    }
    return sortedFiles;
  }

  public static MultiMap<P4Connection, VirtualFile> distributeFilesByConnection(Collection<? extends VirtualFile> files, Project project) {
    PerforceConnectionManagerI mgr = PerforceConnectionManager.getInstance(project);
    return distributeByConnection(files, file -> mgr.getConnectionForFile(file));
  }

  public static MultiMap<P4Connection, FilePath> distributePathsByConnection(Collection<? extends FilePath> files, Project project) {
    PerforceConnectionManagerI connectionManager = PerforceConnectionManager.getInstance(project);
    return distributeByConnection(files, path -> connectionManager.getConnectionForFile(P4File.create(path)));
  }

  public static MultiMap<P4Connection, File> distributeIoFilesByConnection(Collection<? extends File> files, Project project) {
    PerforceConnectionManagerI connectionManager = PerforceConnectionManager.getInstance(project);
    return distributeByConnection(files, file -> connectionManager.getConnectionForFile(file));
  }
}

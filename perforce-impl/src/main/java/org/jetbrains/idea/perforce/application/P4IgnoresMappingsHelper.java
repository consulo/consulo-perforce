// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.application;

import consulo.project.Project;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.idea.perforce.perforce.ExecResult;
import org.jetbrains.idea.perforce.perforce.PerforceRunner;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;

public final class P4IgnoresMappingsHelper {
  private final List<MappingEntry> myMappings;

  private P4IgnoresMappingsHelper(List<MappingEntry> mappings) {
    myMappings = mappings;
  }

  public static P4IgnoresMappingsHelper create(Project project, P4Connection connection) {
    PerforceRunner perforceRunner = PerforceRunner.getInstance(project);
    ExecResult result = perforceRunner.ignores(connection);
    List<MappingEntry> mappingsList = new ArrayList<>();
    for (String line : result.getStdout().split("\n")) {
      if (line.isEmpty()) {
        continue;
      }
      boolean excluded = line.charAt(0) == '!';
      String trimmedLine = StringUtil.trimStart(line, "!");

      mappingsList.add(new MappingEntry(trimmedLine.replace("...", "**"), excluded));
    }

    return new P4IgnoresMappingsHelper(mappingsList);
  }

  public boolean isIgnored(VirtualFile file) {
    Path path = file.toNioPath();

    for (MappingEntry rule : myMappings) {
      if (rule.pathMatcher().matches(path)) {
        return !rule.isExcluded();
      }
    }

    return false;
  }

  private record MappingEntry(String pattern, boolean isExcluded, PathMatcher pathMatcher) {
    MappingEntry(String pattern, boolean isExcluded) {
      this(pattern, isExcluded, FileSystems.getDefault().getPathMatcher("glob:" + pattern));
    }
  }
}

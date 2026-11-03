// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.application;

import consulo.application.util.SystemInfo;
import consulo.util.lang.StringUtil;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class LocalPathsSet {
  private final Set<String> mySet = new HashSet<>();

  public LocalPathsSet(Collection<String> paths) {
    for (String path : paths) {
      mySet.add(convert(path));
    }
  }

  private static String convert(String value) {
    return (SystemInfo.isFileSystemCaseSensitive ? value : StringUtil.toLowerCase(value)).replace("\\", "/");
  }

  public boolean contains(File file) {
    String modified = convert(file.getAbsolutePath());
    return mySet.contains(modified);
  }

}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.application;

import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;
import org.jetbrains.idea.perforce.perforce.PerforceRunner;
import org.jetbrains.idea.perforce.perforce.View;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

class ClientData {
  private final Map<String, List<String>> myData;
  private volatile @Nullable List<View> myViews;
  private volatile @Nullable List<String> myOptions;

  ClientData(Map<String, List<String>> data) {
    myData = Collections.unmodifiableMap(data);
  }

  List<String> getAllRoots() {
    return ContainerUtil.concat(myData.getOrDefault(PerforceRunner.CLIENTSPEC_ROOT, Collections.emptyList()),
                                myData.getOrDefault(PerforceRunner.CLIENTSPEC_ALTROOTS, Collections.emptyList()));
  }

  @Override
  public String toString() {
    return myData.toString();
  }

  List<String> getOptions() {
    List<String> options = myOptions;
    if (options == null) {
      List<String> list = myData.getOrDefault(PerforceRunner.CLIENT_OPTIONS, Collections.emptyList());
      myOptions = options = !list.isEmpty() ? StringUtil.split(list.get(0), " ") : Collections.emptyList();
    }
    return options;
  }

  List<View> getViews() {
    List<View> views = myViews;
    if (views == null) {
      myViews = views = ContainerUtil.mapNotNull(myData.getOrDefault(PerforceRunner.VIEW, Collections.emptyList()), View::create);
    }
    return views;
  }
}

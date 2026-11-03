// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.application;

import consulo.util.collection.BidirectionalMap;
import consulo.util.collection.ContainerUtil;
import consulo.util.xml.serializer.annotation.MapAnnotation;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PerforceNumberNameMap {
  private final BidirectionalMap<Long, String> myNumberToName = new BidirectionalMap<>();

  @Nullable String getName(Long number) {
    return myNumberToName.get(number);
  }

  List<String> getAllNames() {
    return new ArrayList<>(myNumberToName.values());
  }

  void put(String name, Long nativeNumber) {
    myNumberToName.put(nativeNumber, name);
  }

  @Nullable Long getNumber(String name) {
    return ContainerUtil.getFirstItem(myNumberToName.getKeysByValue(name));
  }

  @Nullable String removeList(long nativeNumber) {
    return myNumberToName.remove(nativeNumber);
  }

  void rename(String from, String to) {
    Long associated = getNumber(from);
    if (associated != null) {
      myNumberToName.put(associated, to);
    }
  }


  /** @return the set of change lists not present anymore in p4, that have to be removed from the IDE */
  Set<String> updateMapping(PerforceNumberNameMap newMap) {
    List<String> removed = ContainerUtil.filter(getAllNames(), name -> newMap.getNumber(name) == null);
    myNumberToName.clear();
    myNumberToName.putAll(newMap.myNumberToName);
    return new HashSet<>(removed);
  }

  @MapAnnotation(surroundWithTag = false, surroundKeyWithTag = false, surroundValueWithTag = false)
  public Map<Long, String> getMapping() {
    return new HashMap<>(myNumberToName);
  }

  @MapAnnotation(surroundWithTag = false, surroundKeyWithTag = false, surroundValueWithTag = false)
  public void setMapping(Map<Long, String> map) {
    myNumberToName.clear();
    myNumberToName.putAll(map);
  }
}

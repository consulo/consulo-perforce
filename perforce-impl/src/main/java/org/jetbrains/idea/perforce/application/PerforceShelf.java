// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).

package org.jetbrains.idea.perforce.application;

import consulo.project.Project;
import consulo.util.collection.MultiMap;
import consulo.util.lang.Pair;
import consulo.versionControlSystem.change.LocalChangeList;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class PerforceShelf {
    private final Project myProject;
    private final MultiMap<Pair<ConnectionKey, Long>, ShelvedChange> myCache = MultiMap.create();

    public PerforceShelf(Project project) {
        myProject = project;
    }

    public List<ShelvedChange> getShelvedChanges(LocalChangeList changeList) {
        List<ShelvedChange> result = new ArrayList<>();
        MultiMap<ConnectionKey, Long> map = PerforceNumberNameSynchronizer.getInstance(myProject).getAllNumbers(changeList.getName());
        synchronized (myCache) {
            for (Map.Entry<ConnectionKey, Collection<Long>> entry : map.entrySet()) {
                for (Long number : entry.getValue()) {
                    result.addAll(myCache.get(Pair.create(entry.getKey(), number)));
                }
            }
        }
        return result;
    }

    public void clearShelf() {
        synchronized (myCache) {
            myCache.clear();
        }
    }

    public void addShelvedChange(P4Connection connection, long changeList, ShelvedChange change) {
        synchronized (myCache) {
            myCache.putValue(Pair.create(connection.getConnectionKey(), changeList), change);
        }
    }

    public boolean hasLocalChanges(ConnectionKey key, long number) {
        Collection<ShelvedChange> changes;
        synchronized (myCache) {
            changes = new ArrayList<>(myCache.get(Pair.create(key, number)));
        }
        for (ShelvedChange change : changes) {
            if (change.getFile() != null) {
                return true;
            }
        }
        return false;
    }
}

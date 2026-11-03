// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).

package org.jetbrains.idea.perforce.application;

import consulo.project.Project;
import consulo.versionControlSystem.change.Change;
import consulo.versionControlSystem.change.ContentRevision;
import org.jetbrains.idea.perforce.perforce.PerforceAbstractChange;
import org.jetbrains.idea.perforce.perforce.PerforceContentRevision;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jspecify.annotations.Nullable;

import java.io.File;

public class ShelvedChange extends PerforceAbstractChange {
    private final String myDepotPath;
    private final long myRevision;
    private final long myChangeList;
    private final P4Connection myConnection;

    public ShelvedChange(int type, String depotPath, long revision, P4Connection connection, @Nullable File localFile, long changeList) {
        myDepotPath = depotPath;
        myRevision = revision;
        myConnection = connection;
        myChangeList = changeList;
        myType = type;
        setFile(localFile);
    }

    public String getDepotPath() {
        return myDepotPath;
    }

    @Override
    public String toString() {
        return "ShelvedChange{" +
            "myRevision=" + myRevision +
            ", myDepotPath='" + myDepotPath + '\'' +
            '}';
    }

    public Change toIdeaChange(Project project) {
        PerforceContentRevision beforeRevision = myType == PerforceAbstractChange.ADD || myType == PerforceAbstractChange.MOVE_ADD
            ? null
            : PerforceContentRevision.create(project, myConnection, myDepotPath, myRevision, -1);
        PerforceContentRevision afterRevision = myType == PerforceAbstractChange.DELETE || myType == PerforceAbstractChange.MOVE_DELETE
            ? null
            : PerforceContentRevision.create(project, myConnection, myDepotPath, -1, myChangeList);
        return new IdeaChange(beforeRevision, afterRevision, this);
    }

    public long getChangeList() {
        return myChangeList;
    }

    public P4Connection getConnection() {
        return myConnection;
    }

    public static class IdeaChange extends Change {
        private final ShelvedChange myOriginal;

        public IdeaChange(@Nullable ContentRevision beforeRevision, @Nullable ContentRevision afterRevision, ShelvedChange original) {
            super(beforeRevision, afterRevision);
            myOriginal = original;
        }

        public ShelvedChange getOriginal() {
            return myOriginal;
        }
    }
}

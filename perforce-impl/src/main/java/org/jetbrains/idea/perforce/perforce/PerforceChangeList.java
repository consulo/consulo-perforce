/*
 * Copyright 2000-2006 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.idea.perforce.perforce;

import consulo.logging.Logger;
import consulo.project.Project;
import consulo.versionControlSystem.AbstractVcs;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.action.VcsContextFactory;
import consulo.versionControlSystem.change.Change;
import consulo.versionControlSystem.versionBrowser.CommittedChangeList;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;
import org.jetbrains.idea.perforce.ChangeListData;
import org.jetbrains.idea.perforce.application.PerforceClient;
import org.jetbrains.idea.perforce.application.PerforceManager;
import org.jetbrains.idea.perforce.application.PerforceVcs;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Objects;

public class PerforceChangeList implements CommittedChangeList {
    private static final Logger LOG = Logger.getInstance(PerforceChangeList.class);

    private Date myDate = new Date();
    private long myNumber;
    private String myDescription = "";
    private String myUser = "";
    private String myClient = "";
    private final Project myProject;
    private final P4Connection myConnection;
    private @Nullable List<Change> myIdeaChanges;
    private final PerforceChangeCache myChangeCache;

    public PerforceChangeList(ChangeListData data, Project project, P4Connection connection, PerforceChangeCache changeCache) {
        myChangeCache = changeCache;
        myUser = data.USER;
        try {
            // Perforce before 2003.1 did not include date in 'p4 changes' output
            if (data.DATE.indexOf(':') >= 0) {
                myDate = Date.from(Instant.from(ChangeListData.DATE_FORMAT.parse(data.DATE)));
            }
            else {
                myDate = Date.from(Instant.from(ChangeListData.DATE_ONLY_FORMAT.parse(data.DATE)));
            }
        }
        catch (DateTimeParseException e) {
            LOG.error(e);
            myDate = new Date();
        }
        myNumber = data.NUMBER;
        myDescription = data.DESCRIPTION;
        myClient = data.CLIENT;

        myProject = project;
        myConnection = connection;
    }

    public PerforceChangeList(Project project,
                                                        DataInput stream,
                                                        P4Connection connection,
                                                        PerforceClient perforceClient,
                                                        PerforceChangeCache changeCache) throws IOException {
        myProject = project;
        myChangeCache = changeCache;
        myConnection = connection;
        readFromStream(stream, perforceClient);
    }

    @Override
    public String getCommitterName() {
        return myUser;
    }

    @Override
    public Date getCommitDate() {
        return myDate;
    }

    @Override
    public Collection<Change> getChanges() {
        List<Change> changes = myIdeaChanges;
        if (changes == null) {
            changes = getChangesUnder(null);
            myIdeaChanges = changes;
        }
        return changes;
    }

    @Override
    public Collection<Change> getChangesWithMovedTrees() {
        return getChanges();
    }

    public List<Change> getChangesUnder(@Nullable VirtualFile root) {
        List<Change> ideaChanges = new ArrayList<>();
        for(PerforceChange path: myChangeCache.getChanges(myConnection, myNumber, root)) {
            int type = path.getType();
            PerforceContentRevision beforeRevision = ((type == PerforceAbstractChange.ADD) || (type == PerforceAbstractChange.MOVE_ADD))
                                                                                              ? null
                                                                                              : createRevision(path.getDepotPath(), path.getRevision() - 1);

            PerforceContentRevision afterRevision = ((type == PerforceAbstractChange.DELETE) || (type == PerforceAbstractChange.MOVE_DELETE))
                                                                                            ? null
                                                                                            : createRevision(path.getDepotPath(), path.getRevision());

            ideaChanges.add(new Change(beforeRevision, afterRevision));
        }
        return ideaChanges;
    }

    private PerforceContentRevision createRevision(String depotPath, long revision) {
        return PerforceContentRevision.create(myProject, myConnection, depotPath, revision, -1);
    }

    @Override
    public String getName() {
        return myDescription;
    }

    @Override
    public String getComment() {
        return myDescription;
    }

    @Override
    public long getNumber() {
        return myNumber;
    }

    @Override
    public @Nullable String getBranch() {
        return null;
    }

    @Override
    public AbstractVcs getVcs() {
        return PerforceVcs.getInstance(myProject);
    }

    @Override
    public boolean isModifiable() {
        return true;
    }

    @Override
    public void setDescription(String newMessage) {
        myDescription = newMessage;
    }

    public String getClient() {
        return myClient;
    }

    @Override
    public String toString() {
        return myDescription;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        PerforceChangeList that = (PerforceChangeList)o;

        if (myNumber != that.myNumber) {
            return false;
        }
        if (myClient != null ? !myClient.equals(that.myClient) : that.myClient != null) {
            return false;
        }
        if (myDate != null ? !myDate.equals(that.myDate) : that.myDate != null) {
            return false;
        }
        if (myDescription != null ? !myDescription.equals(that.myDescription) : that.myDescription != null) {
            return false;
        }
        if (myUser != null ? !myUser.equals(that.myUser) : that.myUser != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result;
        result = (myDate != null ? myDate.hashCode() : 0);
        result = 31 * result + Long.hashCode(myNumber);
        result = 31 * result + (myDescription != null ? myDescription.hashCode() : 0);
        result = 31 * result + (myUser != null ? myUser.hashCode() : 0);
        result = 31 * result + (myClient != null ? myClient.hashCode() : 0);
        return result;
    }

    public void writeToStream(DataOutput stream) throws IOException {
        stream.writeLong(myNumber);
        stream.writeLong(myDate.getTime());
        stream.writeUTF(myUser);
        stream.writeUTF(myClient);
        writeUTFTruncated(stream, myDescription);
        myConnection.getId().writeToStream(stream);
        Collection<Change> changes = getChanges();
        stream.writeInt(changes.size());
        for (Change change : changes) {
            PerforceContentRevision revision = (PerforceContentRevision)change.getAfterRevision();
            if (revision == null) {
                stream.writeByte(0);
                revision = (PerforceContentRevision) change.getBeforeRevision();
                assert revision != null;
            }
            else {
                stream.writeByte(change.getBeforeRevision() != null ? 1 : 2);
            }
            stream.writeLong(revision.getRevision());
            stream.writeUTF(Objects.requireNonNull(revision.getDepotPath()));
        }
    }

    private void readFromStream(DataInput stream, PerforceClient perforceClient) throws IOException {
        myNumber = stream.readLong();
        myDate = new Date(stream.readLong());
        myUser = stream.readUTF();
        myClient = stream.readUTF();
        myDescription = stream.readUTF();
        // to don't break the serialization contract we keep it here
        ConnectionId.readFromStream(stream);
        int count = stream.readInt();
        List<Change> ideaChanges = new ArrayList<>(count);
        myIdeaChanges = ideaChanges;

        VcsContextFactory pathService = VcsContextFactory.getInstance();
        for(int i=0; i<count; i++) {
            byte type = stream.readByte();
            long revision = stream.readLong();
            String depotPath = stream.readUTF();
            FilePath filePath = createFilePath(depotPath, perforceClient, pathService);
            Change change = switch (type) {
                case 0 -> new Change(PerforceContentRevision.create(myProject, depotPath, filePath, revision), null);
                case 1 -> new Change(
                    PerforceContentRevision.create(myProject, depotPath, filePath, revision - 1),
                    PerforceContentRevision.create(myProject, depotPath, filePath, revision));
                case 2 -> new Change(null, PerforceContentRevision.create(myProject, depotPath, filePath, revision));
                default -> throw new IllegalStateException("Unknown p4 change type " + type);
            };
            ideaChanges.add(change);
        }
    }

    private static void writeUTFTruncated(DataOutput stream, String text) throws IOException {
        // writeUTF() is limited to 65535 bytes; up to 4 bytes per char is a safe estimation
        if (text.length() > 16383) {
            stream.writeUTF(text.substring(0, 16383));
        }
        else {
            stream.writeUTF(text);
        }
    }

    private static FilePath createFilePath(String depotPath, PerforceClient client, VcsContextFactory pathService) {
        try {
            File file = PerforceManager.getFileByDepotName(depotPath, client);
            if (file != null) {
                return pathService.createFilePathOn(file, false);
            }
        }
        catch (VcsException ignore) {
        }
        return pathService.createFilePathOnNonLocal(depotPath, false);
    }

    // The method is used in "Upsource Integration" plugin
    public P4Connection getConnection() {
        return myConnection;
    }
}

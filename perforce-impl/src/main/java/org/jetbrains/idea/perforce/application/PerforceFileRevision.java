/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package org.jetbrains.idea.perforce.application;

import consulo.project.Project;
import consulo.versionControlSystem.RepositoryLocation;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.action.VcsContextFactory;
import consulo.versionControlSystem.change.ContentRevisionCache;
import consulo.versionControlSystem.history.VcsFileRevision;
import consulo.versionControlSystem.history.VcsRevisionNumber;
import org.jetbrains.idea.perforce.perforce.P4Revision;
import org.jetbrains.idea.perforce.perforce.PerforceRunner;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Date;

public class PerforceFileRevision implements VcsFileRevision {
    private final P4Revision myP4Revision;
    private final PerforceVcsRevisionNumber myNumber;
    private final Project myProject;
    private final P4Connection myConnection;

    public PerforceFileRevision(P4Revision p4Revision, P4Connection connection, Project project) {
        myP4Revision = p4Revision;
        myProject = project;
        myNumber = new PerforceVcsRevisionNumber(myP4Revision);
        myConnection = connection;
    }

    @Override
    public VcsRevisionNumber getRevisionNumber() {
        return myNumber;
    }

    @Override
    public Date getRevisionDate() {
        return myP4Revision.getDate();
    }

    @Override
    public @Nullable String getAuthor() {
        return myP4Revision.getUser();
    }

    @Override
    public @Nullable String getCommitMessage() {
        return myP4Revision.getSubmitMessage();
    }

    private byte[] loadRevisionContent() throws VcsException {
        return PerforceRunner.getInstance(myProject).getByteContent(
            myP4Revision.getDepotPath(),
            "#" + myP4Revision.getRevisionNumber(),
            myConnection
        );
    }

    @Override
    public byte[] loadContent() throws IOException, VcsException {
        return ContentRevisionCache.getOrLoadAsBytes(
            myProject,
            VcsContextFactory.getInstance().createFilePathOnNonLocal(myP4Revision.getDepotPath(), false),
            myNumber,
            PerforceVcs.getKey(),
            ContentRevisionCache.UniqueType.REMOTE_CONTENT,
            this::loadRevisionContent
        );
    }

    @Override
    public byte[] getContent() throws IOException, VcsException {
        return loadContent();
    }

    @Override
    public @Nullable RepositoryLocation getChangedRepositoryPath() {
        return null;
    }

    public long getVersionNumber() {
        return myP4Revision.getRevisionNumber();
    }

    public String getAction() {
        return myP4Revision.getAction();
    }

    public String getClient() {
        return myP4Revision.getClient();
    }

    @Override
    public @Nullable String getBranchName() {
        return null;
    }

    public P4Connection getConnection() {
        return myConnection;
    }

    public @Nullable Charset getDefaultCharset() {
        if ("text".equals(myP4Revision.getType())) {
            return Charset.defaultCharset();
        }
        return null;
    }
}

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

import consulo.virtualFileSystem.fileType.FileType;
import consulo.language.file.FileTypeManager;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.action.VcsContextFactory;
import consulo.versionControlSystem.change.ByteBackedContentRevision;
import consulo.versionControlSystem.history.VcsRevisionNumber;
import consulo.versionControlSystem.change.ContentRevisionCache;
import org.jspecify.annotations.Nullable;
import org.jetbrains.idea.perforce.application.PerforceBinaryContentRevision;
import org.jetbrains.idea.perforce.application.PerforceClient;
import org.jetbrains.idea.perforce.application.PerforceManager;
import org.jetbrains.idea.perforce.application.PerforceOnlyRevisionNumber;
import org.jetbrains.idea.perforce.application.PerforceVcs;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

public class PerforceContentRevision implements ByteBackedContentRevision {
    protected final Project myProject;
    private final @Nullable P4Connection myConnection;
    private final @Nullable String myDepotPath;
    protected final long myRevision;
    protected final String myStringRevision;
    protected @Nullable FilePath myFilePath;

    public PerforceContentRevision(Project project, FilePath path, long revision) {
        this(project, null, null, path, revision, "#" + revision);
    }

    protected PerforceContentRevision(Project project,
                                                                        @Nullable P4Connection connection,
                                                                        @Nullable String depotPath,
                                                                        @Nullable FilePath filePath,
                                                                        long revision,
                                                                        String stringRevision) {
        myFilePath = filePath;
        myStringRevision = stringRevision;
        myRevision = revision;
        myDepotPath = depotPath;
        myConnection = connection;
        myProject = project;
    }

    @Override
    public @Nullable String getContent() throws VcsException {
        String content = ContentRevisionCache.getAsString(getContentAsBytes(), getFile(), null);
        // todo whether we need to convert them?
        return content == null ? null : StringUtil.convertLineSeparators(content, System.lineSeparator());
    }

    @Override
    public byte @Nullable [] getContentAsBytes() throws VcsException {
        try {
            return ContentRevisionCache.getOrLoadAsBytes(myProject, getFile(), getRevisionNumber(), PerforceVcs.getKey(),
                                                                                                      ContentRevisionCache.UniqueType.REPOSITORY_CONTENT,
                                                                                                      () -> loadContent());
        }
        catch (IOException e) {
            throw new VcsException(e);
        }
    }

    protected byte [] loadContent() throws VcsException {
        PerforceRunner runner = PerforceRunner.getInstance(myProject);
        if (myDepotPath != null && myConnection != null) {
            return runner.getByteContent(myDepotPath, myStringRevision, myConnection);
        }
        return runner.getContent(getFile(), myStringRevision);
    }

    @Override
    public FilePath getFile() {
        FilePath filePath = myFilePath;
        if (filePath == null) {
            String depotPath = Objects.requireNonNull(myDepotPath);
            P4Connection connection = myConnection;
            if (connection != null && (! myProject.isDisposed()) && PerforceSettings.getSettings(myProject).ENABLED) {
                PerforceClient client = PerforceManager.getInstance(myProject).getClient(connection);
                File file;
                try {
                    file = PerforceManager.getFileByDepotName(depotPath, client);
                }
                catch (VcsException e) {
                    file = null;
                }
                if (file != null) {
                    filePath = VcsContextFactory.getInstance().createFilePathOn(file);
                }
            }
            if (filePath == null) {
                filePath = VcsContextFactory.getInstance().createFilePathOnNonLocal(depotPath, false);
            }
            myFilePath = filePath;
        }
        return filePath;
    }

    @Override
    public VcsRevisionNumber getRevisionNumber() {
        return new PerforceOnlyRevisionNumber(myRevision);
    }

    public @Nullable String getDepotPath() {
        return myDepotPath;
    }

    public long getRevision() {
        return myRevision;
    }

    public static PerforceContentRevision create(Project project, String depotPath, FilePath localPath, long revision) {
        FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(localPath.getName());
        if (fileType.isBinary()) {
            return new PerforceBinaryContentRevision(project, null, depotPath, localPath, revision, "#" + revision);
        }

        return new PerforceContentRevision(project, null, depotPath, localPath, revision, "#" + revision);
    }

    public static PerforceContentRevision create(Project project, @Nullable P4Connection connection, String depotPath, long revision, long shelveChangeList) {
        String stringRevision = shelveChangeList > 0 ? "@=" + shelveChangeList : "#" + revision;

        int fileNamePos = depotPath.lastIndexOf('/');
        if (fileNamePos >= 0) {
            String fileName = depotPath.substring(fileNamePos);
            FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName);
            if (fileType.isBinary()) {
                return new PerforceBinaryContentRevision(project, connection, depotPath, null, revision, stringRevision);
            }
        }

        return new PerforceContentRevision(project, connection, depotPath, null, revision, stringRevision);
    }

    @Override
    public String toString() {
        return myFilePath == null ? String.valueOf(myDepotPath) : myFilePath.getPath();
    }
}

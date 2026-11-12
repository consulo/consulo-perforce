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

import consulo.perforce.localize.PerforceLocalize;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.action.VcsContextFactory;
import consulo.versionControlSystem.annotate.AnnotationProviderEx;
import consulo.versionControlSystem.annotate.FileAnnotation;
import consulo.versionControlSystem.change.ChangesUtil;
import consulo.versionControlSystem.history.VcsFileRevision;
import consulo.versionControlSystem.history.VcsRevisionNumber;
import consulo.versionControlSystem.virtualFileSystem.VcsFileSystem;
import consulo.versionControlSystem.virtualFileSystem.VcsVirtualFile;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.idea.perforce.application.annotation.AnnotationInfo;
import org.jetbrains.idea.perforce.application.annotation.PerforceFileAnnotation;
import org.jetbrains.idea.perforce.perforce.P4File;
import org.jetbrains.idea.perforce.perforce.P4Revision;
import org.jetbrains.idea.perforce.perforce.PerforceRunner;
import org.jetbrains.idea.perforce.perforce.PerforceSettings;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jspecify.annotations.Nullable;

public class PerforceAnnotationProvider implements AnnotationProviderEx {
    private final Project myProject;
    private final PerforceRunner myRunner;

    public PerforceAnnotationProvider(Project project) {
        myProject = project;
        myRunner = PerforceRunner.getInstance(project);
    }

    @Override
    public FileAnnotation annotate(VirtualFile file) throws VcsException {
        FilePath filePath = VcsContextFactory.getInstance().createFilePathOn(file);
        return doAnnotate(file, ChangesUtil.getCommittedPath(myProject, filePath), -1);
    }

    private FileAnnotation doAnnotate(VirtualFile vFile, FilePath file, long changeNumber) throws VcsException {
        P4File p4File = P4File.create(file);
        P4Connection connection = PerforceSettings.getSettings(myProject).getConnectionForFile(p4File);
        if (connection == null) {
            throw new VcsException(PerforceLocalize.errorInvalidPerforceSettings().get());
        }

        return doAnnotate(changeNumber, connection, p4File.getEscapedPath()).createAnnotation(vFile);
    }

    private AnnotationPrecursor doAnnotate(long changeNumber, P4Connection connection, String path) throws VcsException {
        P4Revision[] fileLog = myRunner.filelog(connection, path, true);
        P4Revision p4Revision = ContainerUtil.find(fileLog, p4Revision1 -> p4Revision1.getChangeNumber() == changeNumber);
        String pathAtRevision = p4Revision == null ? path : p4Revision.getDepotPath();
        long revision = p4Revision == null ? -1 : p4Revision.getRevisionNumber();

        AnnotationInfo annotationInfo = myRunner.annotate(connection, pathAtRevision, revision);
        return new AnnotationPrecursor(annotationInfo, fileLog, revision, p4Revision, connection);
    }

    private class AnnotationPrecursor {
        final AnnotationInfo info;
        final P4Revision[] fileLog;
        final long changeNumber;
        final @Nullable P4Revision p4Revision;
        final P4Connection connection;

        AnnotationPrecursor(
            AnnotationInfo info,
            P4Revision[] fileLog,
            long changeNumber,
            @Nullable P4Revision p4Revision,
            P4Connection connection
        ) {
            this.info = info;
            this.fileLog = fileLog;
            this.changeNumber = changeNumber;
            this.p4Revision = p4Revision;
            this.connection = connection;
        }

        FileAnnotation createAnnotation(VirtualFile file) {
            return new PerforceFileAnnotation(info, file, fileLog, myProject, connection, changeNumber);
        }
    }

    @Override
    public FileAnnotation annotate(VirtualFile file, VcsFileRevision revision) throws VcsException {
        PerforceVcsRevisionNumber number = (PerforceVcsRevisionNumber) revision.getRevisionNumber();
        FilePath filePath = VcsContextFactory.getInstance().createFilePathOn(file);
        return doAnnotate(file, filePath, number.getChangeNumber());
    }

    @Override
    public FileAnnotation annotate(FilePath path, VcsRevisionNumber number) throws VcsException {
        PerforceSettings settings = PerforceSettings.getSettings(myProject);
        P4Connection connection = settings.getConnectionForFile(path);
        if (connection == null) {
            throw new VcsException(PerforceLocalize.errorCannotFindPerforceRoot(path).get());
        }

        long changeNumber = ((PerforceVcsRevisionNumber) number).getChangeNumber();
        AnnotationPrecursor precursor = doAnnotate(changeNumber, connection, ((PerforceVcsRevisionNumber) number).getDepotPath());
        P4Revision p4Revision = precursor.p4Revision;
        if (p4Revision == null) {
            throw new VcsException(PerforceLocalize.errorCannotFindPerforceRevision(changeNumber, path).get());
        }

        PerforceFileRevision revision = new PerforceFileRevision(p4Revision, connection, myProject);
        return precursor.createAnnotation(new VcsVirtualFile(path.getPath(), revision, VcsFileSystem.getInstance()));
    }
}

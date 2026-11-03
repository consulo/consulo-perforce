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
package org.jetbrains.idea.perforce.application.annotation;

import consulo.localize.LocalizeValue;
import consulo.perforce.localize.PerforceLocalize;
import consulo.project.Project;
import consulo.versionControlSystem.VcsKey;
import consulo.versionControlSystem.annotate.FileAnnotation;
import consulo.versionControlSystem.annotate.LineAnnotationAspect;
import consulo.versionControlSystem.annotate.LineAnnotationAspectAdapter;
import consulo.versionControlSystem.history.VcsFileRevision;
import consulo.versionControlSystem.history.VcsRevisionNumber;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.idea.perforce.actions.ShowAllSubmittedFilesAction;
import org.jetbrains.idea.perforce.application.PerforceFileRevision;
import org.jetbrains.idea.perforce.application.PerforceOnlyRevisionNumber;
import org.jetbrains.idea.perforce.application.PerforceVcs;
import org.jetbrains.idea.perforce.application.PerforceVcsRevisionNumber;
import org.jetbrains.idea.perforce.perforce.P4Revision;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jetbrains.idea.perforce.perforce.connections.PerforceConnectionManager;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class PerforceFileAnnotation extends FileAnnotation {
    private final AnnotationInfo myAnnotationInfo;
    private final P4Revision[] myRevisions;
    private final VirtualFile myFile;
    private final long myRevision;

    private final Project myProject;
    private final List<VcsFileRevision> myPerforceRevisions;

    public PerforceFileAnnotation(
        AnnotationInfo annotationInfo,
        VirtualFile file,
        P4Revision[] revisions,
        Project project,
        P4Connection connection,
        long revision
    ) {
        super(project);
        myAnnotationInfo = annotationInfo;
        myRevisions = revisions;
        myProject = project;
        myFile = file;
        myRevision = revision;

        myPerforceRevisions = new ArrayList<>();
        for (P4Revision p4Revision : myRevisions) {
            myPerforceRevisions.add(new PerforceFileRevision(p4Revision, connection, project));
        }
        myPerforceRevisions.sort((o1, o2) -> -1 * o1.getRevisionNumber().compareTo(o2.getRevisionNumber()));
    }

    private final LineAnnotationAspect REVISION = new PerforceAnnotationAspect(LineAnnotationAspect.REVISION, false) {
        @Override
        public String getValue(int lineNumber) {
            P4Revision p4Revision = findRevisionForLine(lineNumber);
            if (p4Revision != null) {
                return String.valueOf(p4Revision.getChangeNumber());
            }
            else {
                return "";
            }
        }
    };

    private final LineAnnotationAspect CLIENT = new PerforceAnnotationAspect(LineAnnotationAspect.AUTHOR, true) {
        @Override
        public String getValue(int lineNumber) {
            P4Revision p4Revision = findRevisionForLine(lineNumber);
            if (p4Revision != null) {
                return p4Revision.getUser();
            }
            else {
                return "";
            }
        }
    };

    private final LineAnnotationAspect DATE = new PerforceAnnotationAspect(LineAnnotationAspect.DATE, true) {
        @Override
        public String getValue(int lineNumber) {
            P4Revision p4Revision = findRevisionForLine(lineNumber);
            if (p4Revision != null) {
                return FileAnnotation.formatDate(p4Revision.getDate());
            }
            else {
                return "";
            }
        }
    };

    @Override
    public void dispose() {
    }

    @Override
    public LineAnnotationAspect[] getAspects() {
        return new LineAnnotationAspect[]{REVISION, DATE, CLIENT};
    }

    @Override
    public LocalizeValue getToolTipValue(int lineNumber) {
        P4Revision revision = findRevisionForLine(lineNumber);
        if (revision == null) {
            return LocalizeValue.empty();
        }

        StringBuilder builder = new StringBuilder();
        builder.append(PerforceLocalize.fileRevision().get()).append(' ').append(revision.getChangeNumber());
        String submitMessage = revision.getSubmitMessage();
        if (submitMessage != null) {
            builder.append(": ").append(submitMessage);
        }
        return LocalizeValue.of(builder.toString());
    }

    @Override
    public String getAnnotatedContent() {
        return myAnnotationInfo.getContent();
    }

    public @Nullable P4Revision findRevisionForLine(int lineNumber) {
        long revision = myAnnotationInfo.getRevision(lineNumber);
        if (revision == -1) {
            return null;
        }
        for (P4Revision p4Revision : myRevisions) {
            if (myAnnotationInfo.isUseChangelistNumbers()) {
                if (p4Revision.getChangeNumber() == revision) {
                    return p4Revision;
                }
            }
            else {
                if (p4Revision.getRevisionNumber() == revision) {
                    return p4Revision;
                }
            }
        }
        return null;
    }

    @Override
    public @Nullable VcsRevisionNumber getLineRevisionNumber(int lineNumber) {
        P4Revision p4Revision = findRevisionForLine(lineNumber);
        if (p4Revision != null) {
            return new PerforceVcsRevisionNumber(p4Revision);
        }
        return null;
    }

    @Override
    public @Nullable Date getLineDate(int lineNumber) {
        P4Revision p4Revision = findRevisionForLine(lineNumber);
        return p4Revision == null ? null : p4Revision.getDate();
    }

    @Override
    public VcsRevisionNumber getCurrentRevision() {
        return new PerforceOnlyRevisionNumber(myRevision);
    }

    @Override
    public List<VcsFileRevision> getRevisions() {
        return myPerforceRevisions;
    }

    @Override
    public int getLineCount() {
        return myAnnotationInfo.getLineCount();
    }

    private abstract class PerforceAnnotationAspect extends LineAnnotationAspectAdapter {
        PerforceAnnotationAspect(String id, boolean showByDefault) {
            super(id, showByDefault);
        }

        @Override
        protected void showAffectedPaths(int lineNum) {
            P4Revision p4Revision = findRevisionForLine(lineNum);
            P4Connection connection = PerforceConnectionManager.getInstance(myProject).getConnectionForFile(myFile);
            if (p4Revision != null && connection != null) {
                long changeNumber = p4Revision.getChangeNumber();
                ShowAllSubmittedFilesAction.showAllSubmittedFiles(
                    myProject,
                    changeNumber,
                    p4Revision.getSubmitMessage(),
                    p4Revision.getDate(),
                    p4Revision.getUser(),
                    connection
                );
            }
        }
    }

    @Override
    public VcsKey getVcsKey() {
        return PerforceVcs.getKey();
    }

    @Override
    public boolean isBaseRevisionChanged(VcsRevisionNumber number) {
        return PerforceVcs.revisionsSame(getCurrentRevision(), number);
    }

    @Override
    public VirtualFile getFile() {
        return myFile;
    }
}

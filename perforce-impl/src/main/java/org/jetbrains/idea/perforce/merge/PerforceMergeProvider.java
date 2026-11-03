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
package org.jetbrains.idea.perforce.merge;

import consulo.perforce.localize.PerforceLocalize;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.versionControlSystem.AbstractVcsHelper;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.history.TextRevisionNumber;
import consulo.versionControlSystem.history.VcsRevisionNumber;
import consulo.versionControlSystem.localize.VcsLocalize;
import consulo.versionControlSystem.merge.MergeData;
import consulo.versionControlSystem.merge.MergeDialogCustomizer;
import consulo.versionControlSystem.merge.MergeProvider;
import consulo.versionControlSystem.util.VcsRunnable;
import consulo.versionControlSystem.util.VcsUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import org.jetbrains.idea.perforce.application.FileGrouper;
import org.jetbrains.idea.perforce.perforce.FStat;
import org.jetbrains.idea.perforce.perforce.P4File;
import org.jetbrains.idea.perforce.perforce.PerforceRunner;
import org.jetbrains.idea.perforce.perforce.PerforceSettings;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class PerforceMergeProvider implements MergeProvider {
    private final Project myProject;
    private final PerforceRunner myRunner;

    public PerforceMergeProvider(Project project) {
        myProject = project;
        myRunner = PerforceRunner.getInstance(myProject);
    }

    @Override
    public MergeData loadRevisions(VirtualFile file) throws VcsException {
        MergeData mergeData = new MergeData();
        VcsRunnable runnable = new VcsRunnable() {
            @Override
            public void run() throws VcsException {
                PerforceSettings settings = PerforceSettings.getSettings(myProject);
                P4File p4File = P4File.create(file);
                BaseRevision baseRevision = myRunner.getBaseRevision(p4File);
                if (baseRevision == null) {
                    throw new VcsException(PerforceLocalize.messageTextCannotFindMergeInfoForFile(file.getPresentableUrl()));
                }

                P4Connection connection = settings.getConnectionForFile(file);
                if (connection == null) {
                    throw new VcsException(PerforceLocalize.connectionCannotDetermineSettings());
                }
                mergeData.ORIGINAL = myRunner.getByteContent(baseRevision, connection);
                String sourceRevision = baseRevision.getSourceRevision();
                if (sourceRevision == null) {
                    FStat fStat = p4File.getFstat(myProject, true);
                    mergeData.LAST_REVISION_NUMBER = new VcsRevisionNumber.Int(Integer.parseInt(fStat.haveRev));

                    mergeData.LAST = myRunner.getByteContent(file.getPath(), null, connection);
                }
                else {
                    mergeData.LAST = myRunner.getByteContent(baseRevision.getDepotPath(), sourceRevision, connection);
                    mergeData.LAST_REVISION_NUMBER = new TextRevisionNumber(sourceRevision);
                }

                try {
                    mergeData.CURRENT = file.contentsToByteArray();
                }
                catch (IOException e) {
                    throw new VcsException(e);
                }
            }
        };
        VcsUtil.runVcsProcessWithProgress(runnable, VcsLocalize.multipleFileMergeLoadingProgressTitle().get(), false, myProject);
        return mergeData;
    }

    @Override
    public void conflictResolvedForFile(VirtualFile file) {
        try {
            VcsUtil.runVcsProcessWithProgress(
                () -> myRunner.resolveToYours(P4File.create(file)),
                PerforceLocalize.progressMarkingFileAsResolved().get(),
                false,
                myProject
            );
        }
        catch (VcsException ignore) {
        }
    }

    @Override
    public boolean isBinary(VirtualFile file) {
        return false;
    }

    /**
     * Opens the files for edit and shows the multiple file merge dialog.
     * The platform merge dialog has no hook to run code before a resolution, so the files are opened for edit up-front.
     */
    public List<VirtualFile> showMergeDialog(List<VirtualFile> files) {
        if (files.isEmpty()) {
            return Collections.emptyList();
        }

        if (!beforeResolve(files)) {
            return Collections.emptyList();
        }

        return AbstractVcsHelper.getInstance(myProject).showMergeDialog(files, this, new MergeDialogCustomizer());
    }

    private boolean beforeResolve(Collection<VirtualFile> files) {
        for (Map.Entry<P4Connection, Collection<VirtualFile>> entry : FileGrouper.distributeFilesByConnection(files, myProject)
            .entrySet()) {
            if (!openForEdit(entry.getKey(), entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    private boolean openForEdit(P4Connection connection, Collection<VirtualFile> files) {
        List<P4File> p4Files = ContainerUtil.map(files, P4File::create);

        try {
            myRunner.editAll(p4Files, -1, false, connection);
        }
        catch (VcsException e) {
            AbstractVcsHelper.getInstance(myProject).showError(e, PerforceLocalize.messageTitleResolve());
            return false;
        }
        VirtualFileUtil.markDirtyAndRefresh(false, false, false, files.toArray(VirtualFile.EMPTY_ARRAY));

        return true;
    }
}

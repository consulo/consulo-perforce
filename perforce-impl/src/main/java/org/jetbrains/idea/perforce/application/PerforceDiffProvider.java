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
import consulo.util.lang.Pair;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.change.ChangesUtil;
import consulo.versionControlSystem.change.ContentRevision;
import consulo.versionControlSystem.diff.DiffProviderEx;
import consulo.versionControlSystem.diff.ItemLatestState;
import consulo.versionControlSystem.history.VcsRevisionDescription;
import consulo.versionControlSystem.history.VcsRevisionDescriptionImpl;
import consulo.versionControlSystem.history.VcsRevisionNumber;
import consulo.versionControlSystem.util.VcsUtil;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.idea.perforce.perforce.FStat;
import org.jetbrains.idea.perforce.perforce.P4File;
import org.jetbrains.idea.perforce.perforce.PerforceChangeList;
import org.jetbrains.idea.perforce.perforce.PerforceContentRevision;
import org.jetbrains.idea.perforce.perforce.PerforceRunner;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class PerforceDiffProvider extends DiffProviderEx {
    private final Project myProject;
    private static final String REVISION_NONE = "none";

    public PerforceDiffProvider(Project project) {
        myProject = project;
    }

    @Override
    public Map<VirtualFile, VcsRevisionNumber> getCurrentRevisions(Iterable<VirtualFile> files) {
        Map<VirtualFile, P4File> p4Files = new LinkedHashMap<>();
        for (VirtualFile file : files) {
            p4Files.put(file, P4File.create(getOriginalIfMoved(file)));
        }
        try {
            Map<VirtualFile, VcsRevisionNumber> result = new LinkedHashMap<>();
            Map<P4File, FStat> fStatMap = PerforceRunner.getInstance(myProject).fstatBulk(new ArrayList<>(p4Files.values()));
            for (VirtualFile file : files) {
                FStat fStat = fStatMap.get(p4Files.get(file));
                PerforceVcsRevisionNumber number = PerforceVcsRevisionNumber.createFromFStat(fStat);
                if (number != null) {
                    result.put(file, number);
                }
            }
            return result;
        }
        catch (VcsException e) {
            return Collections.emptyMap();
        }
    }

    @Override
    public @Nullable VcsRevisionNumber getCurrentRevision(VirtualFile file) {
        FilePath path = getOriginalIfMoved(file);
        return PerforceRunner.getInstance(myProject).getCurrentRevision(P4File.create(path));
    }

    private FilePath getOriginalIfMoved(VirtualFile file) {
        return ChangesUtil.getCommittedPath(myProject, VcsUtil.getFilePath(file));
    }

    @Override
    public @Nullable VcsRevisionDescription getCurrentRevisionDescription(VirtualFile file) {
        try {
            VcsRevisionNumber number = getCurrentRevision(file);
            if (number == null) {
                return null;
            }
            PerforceCommittedChangesProvider provider = PerforceVcs.getInstance(myProject).getCommittedChangesProvider();
            Pair<PerforceChangeList, FilePath> oneList = provider.getOneList(file, number);
            if (oneList != null) {
                return new VcsRevisionDescriptionImpl(
                    number,
                    oneList.getFirst().getCommitDate(),
                    oneList.getFirst().getCommitterName(),
                    oneList.getFirst().getComment()
                );
            }
        }
        catch (VcsException e) {
            return null;
        }
        return null;
    }

    private static boolean isInvalidRevision(@Nullable String revision) {
        return revision == null || revision.isEmpty() || revision.equals(REVISION_NONE);
    }

    @Override
    public @Nullable ItemLatestState getLastRevision(VirtualFile file) {
        try {
            FStat fstat = P4File.create(getOriginalIfMoved(file)).getFstat(myProject, false);
            String headRev = fstat.headRev;
            if (isInvalidRevision(headRev)) {
                return null;
            }
            return new ItemLatestState(new PerforceOnlyRevisionNumber(Long.parseLong(headRev)), true, false);
        }
        catch (VcsException e) {
            return null;
        }
    }

    @Override
    public @Nullable ContentRevision createFileContent(VcsRevisionNumber revisionNumber, VirtualFile selectedFile) {
        long revNumber;
        if (revisionNumber instanceof PerforceVcsRevisionNumber) {
            revNumber = ((PerforceVcsRevisionNumber) revisionNumber).getRevisionNumber();
        }
        else if (revisionNumber instanceof PerforceOnlyRevisionNumber) {
            revNumber = ((PerforceOnlyRevisionNumber) revisionNumber).getNumber();
        }
        else {
            revNumber = ((VcsRevisionNumber.Long) revisionNumber).getLongValue();
        }
        FilePath filePath = getOriginalIfMoved(selectedFile);
        if (selectedFile.getFileType().isBinary()) {
            return new PerforceBinaryContentRevision(myProject, filePath, revNumber);
        }
        return new PerforceContentRevision(myProject, filePath, revNumber);
    }

    @Override
    public @Nullable ItemLatestState getLastRevision(FilePath filePath) {
        try {
            FStat fstat = P4File.create(ChangesUtil.getCommittedPath(myProject, filePath)).getFstat(myProject, false);
            String headRev = fstat.headRev;
            if (isInvalidRevision(headRev)) {
                return null;
            }
            return new ItemLatestState(new PerforceOnlyRevisionNumber(Long.parseLong(headRev)), true, false);
        }
        catch (VcsException e) {
            return null;
        }
    }

    @Override
    public @Nullable VcsRevisionNumber getLatestCommittedRevision(VirtualFile vcsRoot) {
        // todo
        return null;
    }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).

package org.jetbrains.idea.perforce.application;

import consulo.logging.Logger;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.RepositoryLocation;
import consulo.versionControlSystem.TreeDiffProvider;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.change.FilePathsHelper;
import consulo.versionControlSystem.util.VcsUtil;
import consulo.virtualFileSystem.VirtualFile;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class PerforceTreeDiffProvider implements TreeDiffProvider {
    private static final Logger LOG = Logger.getInstance(PerforceTreeDiffProvider.class);
    private final PerforceVcs myVcs;

    public PerforceTreeDiffProvider(PerforceVcs vcs) {
        myVcs = vcs;
    }

    @Override
    public Collection<String> getRemotelyChanged(VirtualFile vcsRoot, Collection<String> paths) {
        PerforceCommittedChangesProvider committedChangesProvider = myVcs.getCommittedChangesProvider();
        // the committed changes location cache is not available, the location is computed directly
        RepositoryLocation location = committedChangesProvider.getLocationFor(VcsUtil.getFilePath(vcsRoot));
        try {
            Collection<FilePath> files = committedChangesProvider.getIncomingFiles(location);
            if (files == null) {
                return Collections.emptyList();
            }
            Set<String> helper = new HashSet<>();
            for (FilePath file : files) {
                String convertedPath = FilePathsHelper.convertPath(file);
                helper.add(convertedPath);
            }

            Collection<String> result = new HashSet<>();
            for (String path : paths) {
                String convertedPath = FilePathsHelper.convertPath(path);
                if (helper.contains(convertedPath)) {
                    result.add(path);
                }
            }
            return result;
        }
        catch (VcsException e) {
            LOG.info(e);
            return Collections.emptyList();
        }
    }
}

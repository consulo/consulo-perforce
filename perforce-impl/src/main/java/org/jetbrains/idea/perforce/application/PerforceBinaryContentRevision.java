/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.change.BinaryContentRevision;
import org.jetbrains.idea.perforce.perforce.P4File;
import org.jetbrains.idea.perforce.perforce.PerforceContentRevision;
import org.jetbrains.idea.perforce.perforce.PerforceRunner;
import org.jetbrains.idea.perforce.perforce.PerforceSettings;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jspecify.annotations.Nullable;

public class PerforceBinaryContentRevision extends PerforceContentRevision implements BinaryContentRevision {
    private byte @Nullable [] myContent = null;

    public PerforceBinaryContentRevision(Project project, FilePath path, long revision) {
        super(project, path, revision);
    }

    public PerforceBinaryContentRevision(
        Project project,
        @Nullable P4Connection connection,
        @Nullable String depotPath,
        @Nullable FilePath filePath,
        long revision,
        String stringRevision
    ) {
        super(project, connection, depotPath, filePath, revision, stringRevision);
    }

    @Override
    public byte @Nullable [] getContentAsBytes() throws VcsException {
        byte[] content = myContent;
        if (content != null) {
            return content;
        }

        P4File p4File = P4File.create(getFile());
        PerforceSettings settings = PerforceSettings.getSettings(myProject);
        if (!settings.ENABLED) {
            return null;
        }
        String rev = myStringRevision.startsWith("@=")
            ? myStringRevision
            : "#" + (myRevision > 0 ? myRevision : p4File.getFstat(myProject, false).haveRev);
        content = PerforceRunner.getInstance(myProject).getByteContent(p4File, rev);
        myContent = content;
        return content;
    }

    @Override
    public byte @Nullable [] getBinaryContent() throws VcsException {
        return getContentAsBytes();
    }
}

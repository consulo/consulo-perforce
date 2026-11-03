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

package org.jetbrains.idea.perforce.perforce;


import org.jspecify.annotations.Nullable;

import java.io.File;


public class ResolvedFile {
    private final File myLocalFile;
    private final @Nullable String myOperation;
    private final String myDepotPath;
    private final long myRevision1;
    private final long myRevision2;
    public static final String OPERATION_BRANCH = "branch from";
    public static final String OPERATION_IGNORE = "ignored";
    public static final String OPERATION_MOVE = "moved from";

    public ResolvedFile(File localFile, @Nullable String operation, String depotPath, long revision1, long revision2) {
        myLocalFile = localFile;
        myOperation = operation;
        myDepotPath = depotPath;
        myRevision1 = revision1;
        myRevision2 = revision2;
    }

    public File getLocalFile() {
        return myLocalFile;
    }

    public @Nullable String getOperation() {
        return myOperation;
    }

    public String getDepotPath() {
        return myDepotPath;
    }

    public long getRevision1() {
        return myRevision1;
    }

    public long getRevision2() {
        return myRevision2;
    }
}

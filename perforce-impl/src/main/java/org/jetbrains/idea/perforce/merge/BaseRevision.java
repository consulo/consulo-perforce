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

import org.jspecify.annotations.Nullable;

public class BaseRevision {
    private final @Nullable String myRevisionNum;
    private final @Nullable String mySourceRevision;
    private final String myDepotPath;

    public BaseRevision(@Nullable String revisionNum, @Nullable String sourceRevision, String depotPath) {
        myDepotPath = depotPath;
        mySourceRevision = sourceRevision;
        myRevisionNum = revisionNum;
    }

    public @Nullable String getRevisionNum() {
        return myRevisionNum;
    }

    public @Nullable String getSourceRevision() {
        return mySourceRevision;
    }

    public String getDepotPath() {
        return myDepotPath;
    }
}

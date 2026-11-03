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

import consulo.logging.Logger;
import consulo.perforce.localize.PerforceLocalize;
import consulo.util.collection.primitive.longs.LongList;
import consulo.util.collection.primitive.longs.LongLists;
import consulo.versionControlSystem.VcsException;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.StringReader;

public final class AnnotationInfo {
    private final String myContent;
    private final long[] myRevisions;
    private final boolean myUseChangelistNumbers;
    private static final Logger LOG = Logger.getInstance(AnnotationInfo.class);

    public AnnotationInfo(String output, boolean useChangelistNumbers) throws IOException, VcsException {
        myUseChangelistNumbers = useChangelistNumbers;
        //noinspection IOResourceOpenedButNotSafelyClosed
        LineNumberReader reader = new LineNumberReader(new StringReader(output));
        String line;
        StringBuilder content = new StringBuilder();
        LongList revisions = LongLists.newArrayList();
        try {
            while ((line = reader.readLine()) != null) {
                int endOfRevisionIndex = line.indexOf(":");
                if (endOfRevisionIndex > 0) {
                    String revision = line.substring(0, endOfRevisionIndex);
                    String contentLine = line.substring(endOfRevisionIndex + 2);
                    content.append(contentLine);
                    content.append("\n");
                    revisions.add(Long.parseLong(revision));
                }
            }
        }
        catch (NumberFormatException e) {
            LOG.info("Can not parse annotation output: \n'" + output + "'", e);
            throw new VcsException(PerforceLocalize.errorCanNotParseAnnotationOutput().get(), e);
        }

        myContent = content.toString();
        myRevisions = revisions.toArray();
    }

    public String getContent() {
        return myContent;
    }

    public long getRevision(int lineNumber) {
        if (lineNumber < 0 || lineNumber >= myRevisions.length) {
            return -1;
        }
        return myRevisions[lineNumber];
    }

    public boolean isUseChangelistNumbers() {
        return myUseChangelistNumbers;
    }

    public int getLineCount() {
        return myRevisions.length;
    }
}

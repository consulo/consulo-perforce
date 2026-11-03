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

import consulo.logging.Logger;
import consulo.versionControlSystem.VcsException;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import consulo.perforce.localize.PerforceLocalize;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public final class PerforceOutputMessageParser extends OutputMessageParser {
    private static final Logger LOG = Logger.getInstance(PerforceOutputMessageParser.class);
    private static final String CHANGE_PREFIX = "change";

    private PerforceOutputMessageParser(String output) throws IOException {
        super(output);
    }

    public static List<PerforceChange> processOpenedOutput(String opened) throws IOException {
        ArrayList<PerforceChange> result = new ArrayList<>();
        PerforceOutputMessageParser parser = new PerforceOutputMessageParser(opened);
        PerforceChange change;
        while((change = parser.readNextOpened()) != null) {
            result.add(change);
        }

        return result;
    }

    private @Nullable PerforceChange readNextOpened() {
        if (myLines.isEmpty()) {
            return null;
        }
        myCurrentLine = myLines.remove(0);
        String originalLine = myCurrentLine;

        if (myCurrentLine != null) {
            String depotPath = readTo("#");
            skip("#");
            String revisionNumber = readTo(" - ");
            if (revisionNumber == null) {
                throw new RuntimeException("Invalid 'p4 opened' line format: " + originalLine);
            }
            skip("-");
            String changeType = readTo(" ");
            if (depotPath == null || changeType == null) {
                throw new RuntimeException("Invalid 'p4 opened' line format: " + originalLine);
            }

            long changeListNumber = -1;
            if (myCurrentLine.startsWith(CHANGE_PREFIX)) {
                skip(CHANGE_PREFIX);
                changeListNumber = parseLong(readTo(" "));
            }

            return new PerforceChange(PerforceAbstractChange.convertToType(changeType), null, depotPath, Long.parseLong(revisionNumber),
                                                                changeListNumber, null);
        }
        return null;
    }

    public static List<ResolvedFile> processResolvedOutput(String resolved,
                                                                                                                  Function<String, String> pathConvertor)
        throws IOException, VcsException {
        ArrayList<ResolvedFile> result = new ArrayList<>();
        PerforceOutputMessageParser parser = new PerforceOutputMessageParser(resolved);
        ResolvedFile file;
        while((file = parser.readNextResolved(pathConvertor)) != null) {
            result.add(file);
        }

        return result;
    }

    private @Nullable ResolvedFile readNextResolved(Function<String, String> pathConvertor) throws IOException, VcsException {
        if (myLines.isEmpty()) {
            return null;
        }
        myCurrentLine = myLines.remove(0);

        if (myCurrentLine != null) {
            String wholeLine = myCurrentLine;
            String localPath = readTo(" - ");
            if (localPath == null) {
                throw unexpectedFormat(wholeLine);
            }

            File localFile = new File(pathConvertor.apply(localPath));
            skip("-");
            String operation = readTo("//");
            String depotPath = readTo("#");
            if (depotPath == null) {
                depotPath = readTo("@=");
                if (depotPath != null) {
                    return new ResolvedFile(localFile, operation, depotPath, -1, -1);
                }
                if (operation == null || operation.startsWith("resolved ")) {
                    return null;
                }
            }

            skip("#");
            long revision1;
            long revision2 = -1;
            try {
                if (myCurrentLine.indexOf(',') >= 0) {
                    revision1 = parseLong(readTo(","));
                    skip(",#");
                    revision2 = Long.parseLong(myCurrentLine);
                }
                else {
                    revision1 = Long.parseLong(myCurrentLine);
                }
            }
            catch (NumberFormatException e) {
                throw unexpectedFormat(wholeLine);
            }

            if (depotPath == null) {
                throw unexpectedFormat(wholeLine);
            }
            return new ResolvedFile(localFile, operation, depotPath, revision1, revision2);
        }
        return null;
    }

    private VcsException unexpectedFormat(String wholeLine) {
        LOG.debug("Cannot parse p4 resolved output:\n" + myOutput + "\n\n current=" + wholeLine);
        return new VcsException(PerforceLocalize.errorCannotParseP4ResolvedOutput());
    }

    private static long parseLong(@Nullable String s) {
        if (s == null) {
            throw new NumberFormatException("Cannot parse null string");
        }
        return Long.parseLong(s);
    }
}

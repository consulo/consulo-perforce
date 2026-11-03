// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce;

import consulo.perforce.localize.PerforceLocalize;
import consulo.versionControlSystem.VcsException;

import java.util.Map;

class P4OpenedParser extends P4Parser {
    private static final String OPENED_DELIMITER = " - ";

    P4OpenedParser(Map<String, Long> revisions) {
        super(P4Command.opened, revisions);
    }

    @Override
    protected ParsedLine consumeLine(String outputLine) throws VcsException {
        int hashIndex = outputLine.indexOf('#');
        if (hashIndex < 0) {
            throw new VcsException(PerforceLocalize.errorUnexpectedP4OpenedOutputFormat(outputLine));
        }
        int idx = outputLine.indexOf(OPENED_DELIMITER, hashIndex);
        if (idx < 0) {
            throw new VcsException(PerforceLocalize.errorUnexpectedP4OpenedOutputFormat(outputLine));
        }
        String depotPath = outputLine.substring(0, hashIndex);
        long revision = Long.parseLong(outputLine.substring(hashIndex + 1, idx));

        return new ParsedLine(depotPath, revision);
    }
}

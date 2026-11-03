// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce;

import consulo.perforce.localize.PerforceLocalize;
import consulo.util.io.FileUtil;
import consulo.versionControlSystem.VcsException;
import org.jetbrains.idea.perforce.application.PerforceManager;

import java.util.HashMap;
import java.util.Map;

public class P4HaveParser extends P4Parser {
    private static final String HAVE_DELIMITER = " - ";

    private final PerforceManager myPerforceManager;

    public P4HaveParser(PerforceManager perforceManager, Map<String, Long> revisions) {
        super(P4Command.have, revisions);
        myPerforceManager = perforceManager;
    }

    public P4HaveParser(PerforceManager perforceManager) {
        this(perforceManager, new HashMap<>());
    }

    @Override
    protected ParsedLine consumeLine(String outputLine) throws VcsException {
        int hashIndex = outputLine.indexOf('#');
        if (hashIndex < 0) {
            throw new VcsException(PerforceLocalize.errorUnexpectedP4HaveOutputFormat(outputLine));
        }
        int idx = outputLine.indexOf(HAVE_DELIMITER, hashIndex);
        if (idx < 0) {
            throw new VcsException(PerforceLocalize.errorUnexpectedP4HaveOutputFormat(outputLine));
        }
        String localPath = myPerforceManager.convertP4ParsedPath(null, outputLine.substring(idx + HAVE_DELIMITER.length()));
        long revision = Long.parseLong(outputLine.substring(hashIndex + 1, idx));

        return new ParsedLine(FileUtil.toSystemDependentName(localPath), revision);
    }
}

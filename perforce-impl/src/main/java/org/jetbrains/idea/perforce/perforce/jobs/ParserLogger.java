// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.jobs;

import consulo.logging.Logger;
import consulo.versionControlSystem.VcsException;

public class ParserLogger {
    private final Logger myLogger;
    private final String myPrefix;

    public ParserLogger(String category, String prefix) {
        myPrefix = prefix + " ";
        myLogger = Logger.getInstance(category);
    }

    public void generateParseException(String detailsText) throws VcsException {
        String message = myPrefix + detailsText;
        myLogger.info(message);
        throw new VcsException(message);
    }
}

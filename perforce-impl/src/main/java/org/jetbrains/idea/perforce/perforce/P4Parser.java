// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce;

import consulo.versionControlSystem.VcsException;
import org.jspecify.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public abstract class P4Parser {
    private final RevisionCollector myRevisionCollector;
    private final P4Command myCommand;

    public P4Parser(P4Command command, Map<String, Long> revisions) {
        myRevisionCollector = new RevisionCollector(revisions);
        myCommand = command;
    }

    public P4Command getCommand() {
        return myCommand;
    }

    protected abstract @Nullable ParsedLine consumeLine(String outputLine) throws VcsException;

    protected void consumeRevision(String path, long revision) {
        myRevisionCollector.consumeRevision(path, revision);
    }

    void readOutput(InputStream inputStream) throws IOException, VcsException {
        @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        do {
            String line = reader.readLine();
            if (line == null || line.isEmpty()) {
                break;
            }
            ParsedLine parsedLine = consumeLine(line);
            if (parsedLine != null) {
                consumeRevision(parsedLine.path, parsedLine.revision);
            }
        }
        while (true);
    }

    static final class RevisionCollector {
        private final Map<String, Long> myRevisions;

        RevisionCollector(Map<String, Long> revisions) {
            myRevisions = revisions;
        }

        void consumeRevision(String path, long revision) {
            myRevisions.put(path, revision);
        }
    }

    protected record ParsedLine(String path, long revision) {
    }
}


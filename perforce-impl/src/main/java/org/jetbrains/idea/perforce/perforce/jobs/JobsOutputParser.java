// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.jobs;

import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.SimpleReference;
import consulo.versionControlSystem.VcsException;
import org.jetbrains.idea.perforce.application.ConnectionKey;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class JobsOutputParser {
    private static final ParserLogger LOG =
        new ParserLogger("#org.jetbrains.idea.perforce.perforce.jobs.JobsOutputParser", "'p4 jobs' output parsing error.");
    private final PerforceJobSpecification mySpecification;
    private final List<String> myLines;
    private final P4Connection myConnection;
    private final ConnectionKey myKey;

    public JobsOutputParser(PerforceJobSpecification specification, List<String> lines, P4Connection connection, ConnectionKey key) {
        mySpecification = specification;
        myLines = lines;
        myConnection = connection;
        myKey = key;
    }

    // without details (custom fields)
    public List<PerforceJob> parse() throws VcsException {
        List<PerforceJob> result = new ArrayList<>();
        List<FieldParser> fieldParsers = createStandardParsersBySpec();

        for (String line : myLines) {
            if (line.startsWith("\t")) {
                if (result.isEmpty()) {
                    LOG.generateParseException("Cannot parse line: " + line);
                }
                PerforceJob lastJob = result.get(result.size() - 1);
                PerforceJobFieldValue field = lastJob.getValueForStandardField(StandardJobFields.description);
                if (field != null) {
                    field.setValue(field.getValue() + " " + line.trim());
                }
                else {
                    LOG.generateParseException("Cannot append description line: " + line);
                }
            }
            else {
                result.add(parseLine(line, fieldParsers));
            }
        }
        return result;
    }

    private PerforceJob parseLine(String line, List<FieldParser> fieldParsers) throws VcsException {
        List<PerforceJobFieldValue> values = new ArrayList<>(fieldParsers.size());
        SimpleReference<Integer> startAt = SimpleReference.create(0);
        for (FieldParser parser : fieldParsers) {
            parser.parse(line, startAt, values);
        }
        return new PerforceJob(values, myConnection, myKey);
    }

    private static class FieldParser {
        private final PerforceJobField myField;
        private final Parser myParser;

        protected FieldParser(PerforceJobField field, Parser parser) {
            myField = field;
            myParser = parser;
        }

        protected void parse(String line, SimpleReference<Integer> startAt, List<PerforceJobFieldValue> fieldValues) throws VcsException {
            String s = myParser.parse(line, startAt);
            if (s != null) {
                fieldValues.add(new PerforceJobFieldValue(myField, s));
            }
        }
    }

    private interface Parser {
        String BY = " by ";
        String ON = " on ";
        String STAR = "*";

        StandardJobFields getField();

        @Nullable String parse(String s, SimpleReference<Integer> startFrom) throws VcsException;
    }

    private List<FieldParser> createStandardParsersBySpec() {
        List<FieldParser> result = new ArrayList<>();
        for (Parser parser : ourParsers) {
            PerforceJobField field = mySpecification.getFieldByCode(parser.getField().getFixedCode());
            if (field != null) {
                result.add(new FieldParser(field, parser));
            }
        }

        return result;
    }

    //jobname on date by user *status* description
    private static final Parser[] ourParsers = new Parser[]{
        new Parser() {
            @Override
            public StandardJobFields getField() {
                return StandardJobFields.name;
            }

            @Override
            public String parse(String s, SimpleReference<Integer> startFrom) throws VcsException {
                int idx = s.indexOf(" ");
                idx = (idx == -1) ? s.length() : idx;
                String result = s.substring(0, idx);
                startFrom.set(result.length());
                return result;
            }
        },
        new Parser() {
            @Override
            public StandardJobFields getField() {
                return StandardJobFields.date;
            }

            @Override
            public @Nullable String parse(String s, SimpleReference<Integer> startFrom) throws VcsException {
                int idx = s.indexOf(ON, startFrom.requiredGet());
                if (idx == -1) {
                    return null;
                }
                int idxEnd = s.indexOf(" ", idx + ON.length());
                idxEnd = (idxEnd == -1) ? s.length() : idxEnd;
                String result = s.substring(idx + ON.length(), idxEnd);
                startFrom.set(idxEnd);
                return result.trim();
            }
        },
        new Parser() {
            @Override
            public StandardJobFields getField() {
                return StandardJobFields.user;
            }

            @Override
            public @Nullable String parse(String s, SimpleReference<Integer> startFrom) throws VcsException {
                int idx = s.indexOf(BY, startFrom.requiredGet());
                if (idx == -1) {
                    return null;
                }
                int idxEnd = s.indexOf(" ", idx + BY.length());
                idxEnd = (idxEnd == -1) ? s.length() : idxEnd;
                startFrom.set(idxEnd);
                return s.substring(idx + BY.length(), idxEnd).trim();
            }
        },
        new Parser() {
            @Override
            public StandardJobFields getField() {
                return StandardJobFields.status;
            }

            @Override
            public @Nullable String parse(String s, SimpleReference<Integer> startFrom) throws VcsException {
                int idx1 = s.indexOf(STAR, startFrom.requiredGet());
                if (idx1 == -1) {
                    return null;
                }
                int idx2 = s.indexOf(STAR, idx1 + STAR.length());
                if (idx2 == -1) {
                    LOG.generateParseException("Cannot find status in '" + s + "'");
                }
                startFrom.set(idx2 + 1);
                return s.substring(idx1 + STAR.length(), idx2).trim();
            }
        },
        new Parser() {
            @Override
            public StandardJobFields getField() {
                return StandardJobFields.description;
            }

            @Override
            public String parse(String s, SimpleReference<Integer> startFrom) {
                String removeWrappers = s.substring(startFrom.requiredGet()).trim();
                removeWrappers = StringUtil.trimStart(removeWrappers, "'");
                if (removeWrappers.endsWith("'")) {
                    removeWrappers = removeWrappers.substring(0, removeWrappers.length() - 2);
                }
                startFrom.set(s.length());
                return removeWrappers.trim();
            }
        }
    };
}

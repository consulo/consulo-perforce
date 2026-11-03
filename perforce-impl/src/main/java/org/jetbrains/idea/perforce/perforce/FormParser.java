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
package org.jetbrains.idea.perforce.perforce;

import consulo.application.util.LineTokenizer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FormParser {
    private static final String COMMENT_PREFIX = "#";
    private final List<String> myAvailableFields;
    private final List<String> myLines;
    private final Map<String, List<String>> myResult = new LinkedHashMap<>();
    private @Nullable String myCurrentField;
    private final @Nullable Set<String> myFieldsTakeEvenEmpty;

    private FormParser(List<String> availableFields, List<String> lines, @Nullable Set<String> fieldsTakeEvenEmpty) {
        myAvailableFields = availableFields;
        myLines = lines;
        myFieldsTakeEvenEmpty = fieldsTakeEvenEmpty;
    }

    public static Map<String, List<String>> execute(String source, String[] fields) {
        return execute(source, fields, null);
    }

    public static Map<String, List<String>> execute(String source, String[] fields, @Nullable String doNotIgnoreEmpty) {
        FormParser formParser = new FormParser(new ArrayList<>(Arrays.asList(fields)), splitIntoStringsExceptComments(source),
                                                                                      doNotIgnoreEmpty == null ? null : Collections.singleton(doNotIgnoreEmpty));
        formParser.executeInt();
        return formParser.myResult;
    }

    private void executeInt() {
        for (String line : myLines) {
            String field = isBeginOfNewField(line);
            String value = line;
            if (field != null) {
                myAvailableFields.remove(field);
                value = line.substring(field.length());
                value = value.trim();
                myCurrentField = field;
            }

            if (myCurrentField != null &&
                    ((myFieldsTakeEvenEmpty != null && myFieldsTakeEvenEmpty.contains(myCurrentField)) || !value.isEmpty())) {
                myResult.computeIfAbsent(myCurrentField, k -> new ArrayList<>()).add(value);
            }
        }
    }

    private @Nullable String isBeginOfNewField(String line) {
        for (String field : myAvailableFields) {
            if (line.startsWith(field)) {
                return field;
            }
        }
        return null;
    }

    private static List<String> splitIntoStringsExceptComments(@Nullable String source) {
        ArrayList<String> result = new ArrayList<>();
        if (source != null) {
            String[] lines = LineTokenizer.tokenize(source, false);
            for (String rawLine : lines) {
                String line = rawLine.trim();
                if (!line.isEmpty() && !line.startsWith(COMMENT_PREFIX)) {
                    result.add(line);
                }
            }
        }
        return result;
    }

}

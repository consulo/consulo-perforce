// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce;

import consulo.util.lang.StringUtil;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.change.ChangeList;
import consulo.versionControlSystem.change.ChangeListManagerGate;
import consulo.versionControlSystem.change.LocalChangeList;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PerforceChangeListHelper {
    private static final String DEFAULT_DESCRIPTION = "<none>";

    public static String createSpecification(String description,
                                                                                      long changeListNumber,
                                                                                      @Nullable List<String> files,
                                                                                      @Nullable String clientName,
                                                                                      @Nullable String userName,
                                                                                      boolean forUpdate, boolean restricted) throws VcsException {
        StringBuilder result = new StringBuilder();
        result.append("Change:\t");
        if (changeListNumber == -1) {
            result.append("new");
        }
        else {
            result.append(changeListNumber);
        }
        if (clientName != null && (! forUpdate)) {
            result.append("\n\nClient:\t");
            result.append(clientName);
        }
        if (userName != null && (! forUpdate)) {
            result.append("\n\nUser:\t");
            result.append(userName);
        }
        if (! forUpdate) {
            result.append("\n\nStatus:\t");
            if (changeListNumber == -1) {
                result.append("new");
            }
            else {
                result.append("pending");
            }
        }
        if (restricted) {
            result.append("\n\nType:\trestricted");
        }
        result.append("\n\nDescription:");
        String[] lines = StringUtil.convertLineSeparators(description).split("\n");
        if (lines.length == 0) {
            lines = new String[] { DEFAULT_DESCRIPTION };
        }
        for(String line: lines) {
            result.append("\n\t").append(line);
        }

        if (changeListNumber != -1 || files != null) {
            result.append("\n\nFiles:\n");
            if (files != null) {
                for(String file: files) {
                    result.append("\t").append(file).append("\n");
                }
            }
        }

        return result.toString();
    }

    public static long parseCreatedListNumber(String output) {
        String prefix = "Change";
        String copy = output;
        if (copy.startsWith(prefix)) {
            copy = copy.substring(prefix.length()).trim();
        }
        int wsPos = copy.indexOf(' ');
        if (wsPos >= 0) {
            copy = copy.substring(0, wsPos).trim();
        }
        try {
            return Long.parseLong(copy);
        }
        catch (NumberFormatException e) {
            return -1;
        }
    }

    public static ChangeList findOrCreateDefaultList(ChangeListManagerGate addGate) {
        for (String name : getAllDefaultNames()) {
            LocalChangeList list = addGate.findChangeList(name);
            if (list != null) {
                return list;
            }
        }
        return addGate.addChangeList(LocalChangeList.DEFAULT_NAME, "");
    }

    /**
     * Names the default change list may have: the current localized one and the historical ones.
     */
    private static Set<String> getAllDefaultNames() {
        Set<String> names = new LinkedHashSet<>();
        names.add(LocalChangeList.DEFAULT_NAME);
        names.add("Changes");
        names.add("Default Changelist");
        names.add("Default");
        return names;
    }
}

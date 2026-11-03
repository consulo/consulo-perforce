// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.jobs;

import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ByNamesConstraint implements JobsSearchSpecificator {
    private final List<String> myNames;

    public ByNamesConstraint(List<String> names) {
        myNames = names;
    }

    @Override
    public String[] addParams(String[] s) {
        if (myNames.isEmpty()) {
            return s;
        }
        List<String> list = new ArrayList<>(s.length + 2);
        ContainerUtil.addAll(list, s);
        list.add("-e");
        list.add(getNamesConstraint());
        return ArrayUtil.toStringArray(list);
    }

    @Override
    public int getMaxCount() {
        return -1;
    }

    private String getNamesConstraint() {
        return "Job=" + StringUtil.join(
            myNames,
            s -> StringUtil.replace(
                s,
                Arrays.asList("\\", "&", "|", "=", ">", "<", "^"),
                Arrays.asList("\\\\", "\\&", "\\|", "\\=", "\\>", "\\<", "\\^")
            ),
            "|"
        );
    }
}

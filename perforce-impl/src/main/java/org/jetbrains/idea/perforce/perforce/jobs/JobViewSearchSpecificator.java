// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.jobs;

import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class JobViewSearchSpecificator implements JobsSearchSpecificator {
    private final @Nullable String myJobView;
    private final @Nullable String myCustomFilter;

    public JobViewSearchSpecificator(@Nullable String jobView, @Nullable String customFilter) {
        myJobView = jobView;
        myCustomFilter = customFilter;
    }

    @Override
    public String[] addParams(String[] s) {
        List<String> list = new ArrayList<>(s.length + 2);
        ContainerUtil.addAll(list, s);
        list.add("-m");
        list.add("" + (FullSearchSpecificator.ourMaxLines + 1));

        boolean notEmptyJobView = !StringUtil.isEmptyOrSpaces(myJobView);
        boolean notEmptyCustomFilter = !StringUtil.isEmptyOrSpaces(myCustomFilter);
        if (notEmptyJobView || notEmptyCustomFilter) {
            list.add("-e");
            StringBuilder sb = new StringBuilder();
            if (notEmptyJobView) {
                sb.append("(").append(myJobView).append(")");
            }
            if (notEmptyCustomFilter) {
                if (notEmptyJobView) {
                    sb.append(" & ");
                }
                sb.append("(").append(myCustomFilter).append(")");
            }
            list.add(sb.toString());
        }
        return ArrayUtil.toStringArray(list);
    }

    @Override
    public int getMaxCount() {
        return 50;
    }
}

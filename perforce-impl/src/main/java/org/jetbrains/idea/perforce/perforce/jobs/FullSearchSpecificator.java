// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.jobs;

import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Pair;

import java.util.ArrayList;
import java.util.List;

public class FullSearchSpecificator implements JobsSearchSpecificator {
    // todo setting
    public static final int ourMaxLines = 500;
    private final List<Pair<Parts, String>> myStandard = new ArrayList<>();

    public void addStandardConstraint(Parts part, String pattern) {
        myStandard.add(Pair.create(part, pattern));
    }

    @Override
    public String[] addParams(String[] s) {
        List<String> list = new ArrayList<>(s.length + 2);
        ContainerUtil.addAll(list, s);
        list.add("-m");
        list.add("" + (ourMaxLines + 1));
        if (!(myStandard.isEmpty())) {
            list.add("-e");
            list.add(createPatterns());
        }
        return ArrayUtil.toStringArray(list);
    }

    @Override
    public int getMaxCount() {
        return ourMaxLines;
    }

    private String createPatterns() {
        StringBuilder sb = new StringBuilder();
        for (Pair<Parts, String> pair : myStandard) {
            sb.append("(");
            pair.getFirst().add(sb, pair.getSecond());
            sb.append(") ");
        }
        return sb.toString();
    }

    public enum Parts {
        jobname("Job") {
            @Override
            protected void addImpl(StringBuilder sb, String pattern) {
                sb.append("=").append(pattern);
            }
        },
        status("Status") {
            @Override
            protected void addImpl(StringBuilder sb, String pattern) {
                sb.append("=").append(pattern);
            }
        },
        user("User") {
            @Override
            protected void addImpl(StringBuilder sb, String pattern) {
                sb.append("=").append(pattern);
            }
        },
        dateBefore("Date") {
            @Override
            protected void addImpl(StringBuilder sb, String pattern) {
                sb.append("<=").append(pattern);
            }
        },
        dateAfter("Date") {
            @Override
            protected void addImpl(StringBuilder sb, String pattern) {
                sb.append(">=").append(pattern);
            }
        },
        description("Description") {
            @Override
            protected void addImpl(StringBuilder sb, String pattern) {
                sb.append("=").append(pattern);
            }
        };

        private final String myName;

        Parts(String pattern) {
            myName = pattern;
        }

        public void add(StringBuilder sb, String pattern) {
            sb.append(myName);
            addImpl(sb, pattern);
        }

        protected abstract void addImpl(StringBuilder sb, String pattern);
    }
}

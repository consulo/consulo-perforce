// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.jobs;

import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

public enum PerforceJobPersistenceType {
    optional("optional"),
    _default("default"),
    required("required"),
    once("once"),
    always("always");

    private final String myName;

    PerforceJobPersistenceType(String name) {
        myName = name;
    }

    public static @Nullable PerforceJobPersistenceType parse(String s) {
        String l = StringUtil.toLowerCase(s);
        if (optional.myName.equals(l)) {
            return optional;
        }
        if (_default.myName.equals(l)) {
            return _default;
        }
        if (required.myName.equals(l)) {
            return required;
        }
        if (once.myName.equals(l)) {
            return once;
        }
        if (always.myName.equals(l)) {
            return always;
        }
        return null;
    }
}

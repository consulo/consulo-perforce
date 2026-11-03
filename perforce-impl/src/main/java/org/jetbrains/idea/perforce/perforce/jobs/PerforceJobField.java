// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.jobs;

import org.jspecify.annotations.Nullable;

public class PerforceJobField {
    private int myCode;
    private @Nullable PerforceJobFieldType myType;
    private @Nullable String myName;
    private @Nullable PerforceJobPersistenceType myPersistence;

    public int getCode() {
        return myCode;
    }

    public @Nullable String getName() {
        return myName;
    }

    public void setCode(int code) {
        myCode = code;
    }

    public void setType(PerforceJobFieldType type) {
        myType = type;
    }

    public void setName(String name) {
        myName = name;
    }

    public void setPersistence(@Nullable PerforceJobPersistenceType persistence) {
        myPersistence = persistence;
    }

    public boolean filled() {
        return ((myCode > 100) && (myCode < 200)) && myName != null && myPersistence != null && myType != null;
    }
}

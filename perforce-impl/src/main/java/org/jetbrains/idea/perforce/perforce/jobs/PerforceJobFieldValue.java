// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.jobs;

public class PerforceJobFieldValue {
    private final PerforceJobField myField;
    private String myValue;

    public PerforceJobFieldValue(PerforceJobField field, String value) {
        myField = field;
        myValue = value;
    }

    public PerforceJobField getField() {
        return myField;
    }

    public String getValue() {
        return myValue;
    }

    public void setValue(String value) {
        myValue = value;
    }
}

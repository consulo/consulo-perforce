// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.jobs;

import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PerforceJobSpecification {
    private final Map<Integer, PerforceJobField> myFields;

    public PerforceJobSpecification(List<PerforceJobField> fields) {
        myFields = new HashMap<>();
        for (PerforceJobField field : fields) {
            myFields.put(field.getCode(), field);
        }
    }

    public Collection<PerforceJobField> getFields() {
        return myFields.values();
    }

    public @Nullable PerforceJobField getFieldByCode(int code) {
        return myFields.get(code);
    }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.jobs;

import org.jetbrains.idea.perforce.application.ConnectionKey;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class PerforceJob {
    private final P4Connection myConnection;
    private final ConnectionKey myKey;

    private final Map<Integer, PerforceJobFieldValue> myStandardFields;

    public PerforceJob(List<PerforceJobFieldValue> standardFields, P4Connection connection, ConnectionKey key) {
        myKey = key;
        myStandardFields = new HashMap<>(standardFields.size(), 1);
        myConnection = connection;

        for (PerforceJobFieldValue field : standardFields) {
            myStandardFields.put(field.getField().getCode(), field);
        }
    }

    public @Nullable PerforceJobFieldValue getValueForStandardField(StandardJobFields id) {
        return myStandardFields.get(id.getFixedCode());
    }

    public String getName() {
        // the name field is always present: the job name is the first token of every 'p4 jobs' output line
        return Objects.requireNonNull(getNameValue()).getValue();
    }

    public @Nullable PerforceJobFieldValue getNameValue() {
        return getValueForStandardField(StandardJobFields.name);
    }

    public P4Connection getConnection() {
        return myConnection;
    }

    public ConnectionKey getConnectionKey() {
        return myKey;
    }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.jobs;

import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jspecify.annotations.Nullable;

public class P4JobsLogicConn {
    private final P4Connection myConnection;
    private final PerforceJobSpecification mySpec;
    private final @Nullable String myJobView;

    public P4JobsLogicConn(P4Connection connection, PerforceJobSpecification spec, @Nullable String jobView) {
        myConnection = connection;
        mySpec = spec;
        myJobView = jobView;
    }

    public P4Connection getConnection() {
        return myConnection;
    }

    public PerforceJobSpecification getSpec() {
        return mySpec;
    }

    public @Nullable String getJobView() {
        return myJobView;
    }
}

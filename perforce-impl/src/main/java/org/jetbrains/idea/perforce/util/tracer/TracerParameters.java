// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.util.tracer;

public class TracerParameters {
    private final long myIntervalInMillis;
    private final int myHistoryQueueSize;

    public TracerParameters(long intervalInMillis, int historyQueueSize) {
        myIntervalInMillis = intervalInMillis;
        myHistoryQueueSize = historyQueueSize;
    }

    public long getIntervalInMillis() {
        return myIntervalInMillis;
    }

    public int getHistoryQueueSize() {
        return myHistoryQueueSize;
    }
}

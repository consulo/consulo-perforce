// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.jobs;

public interface JobsSearchSpecificator {
    String[] addParams(String[] s);

    /**
     * negative for not defined
     */
    int getMaxCount();
}

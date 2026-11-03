// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce;

import consulo.util.xml.serializer.annotation.Tag;
import org.jspecify.annotations.Nullable;

@Tag("CHANGE_LIST")
public class ParticularConnectionSettings  {
    public String INTEGRATED_CHANGE_LIST_NUMBER = "";
    public boolean INTEGRATE_CHANGE_LIST = false;
    public boolean INTEGRATE_REVERSE= false;
    public @Nullable String INTEGRATE_BRANCH_NAME = null;
    public long INTEGRATE_TO_CHANGELIST_NUM = -1;
}

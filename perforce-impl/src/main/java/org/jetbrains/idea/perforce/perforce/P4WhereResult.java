// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce;

import consulo.util.lang.StringUtil;

public class P4WhereResult {
    // "C:\depot\..."
    private final String myLocal;
    // "//unit-206/..."
    private final String myLocalRootDependent;
    // "\\depot\..."
    private final String myDepot;

    public P4WhereResult(String local, String localRootDependent, String depot) {
        myLocal = local;
        myLocalRootDependent = localRootDependent;
        myDepot = StringUtil.trimStart(depot, "+");
    }

    public String getLocal() {
        return myLocal;
    }

    public String getLocalRootDependent() {
        return myLocalRootDependent;
    }

    public String getDepot() {
        return myDepot;
    }
}

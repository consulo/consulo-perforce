// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.util.tracer;

interface StatisticsPresentation<T extends Tracer> {
    void putSelf(StringBuilder sb, T statistics);
}

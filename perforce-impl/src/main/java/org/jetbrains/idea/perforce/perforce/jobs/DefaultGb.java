// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.jobs;

import java.awt.GridBagConstraints;
import java.awt.Insets;

final class DefaultGb {
    private DefaultGb() {
    }

    public static GridBagConstraints create() {
        return new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
            new Insets(2, 2, 2, 2), 0, 0);
    }
}

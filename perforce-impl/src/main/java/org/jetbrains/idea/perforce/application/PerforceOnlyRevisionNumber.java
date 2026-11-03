// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).

package org.jetbrains.idea.perforce.application;

import consulo.versionControlSystem.history.VcsRevisionNumber;
import org.jspecify.annotations.Nullable;

public class PerforceOnlyRevisionNumber implements VcsRevisionNumber {
    private final long myNumber;

    public PerforceOnlyRevisionNumber(long number) {
        myNumber = number;
    }

    @Override
    public String asString() {
        return String.valueOf(myNumber);
    }

    @Override
    public int compareTo(VcsRevisionNumber o) {
        if (o instanceof PerforceOnlyRevisionNumber) {
            return java.lang.Long.compare(myNumber, ((PerforceOnlyRevisionNumber) o).myNumber);
        }
        if (o instanceof PerforceVcsRevisionNumber) {
            return java.lang.Long.compare(myNumber, ((PerforceVcsRevisionNumber) o).getChangeNumber());
        }
        return 0;
    }

    public long getNumber() {
        return myNumber;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        PerforceOnlyRevisionNumber number = (PerforceOnlyRevisionNumber) o;

        return myNumber == number.myNumber;
    }

    @Override
    public int hashCode() {
        return java.lang.Long.hashCode(myNumber);
    }
}

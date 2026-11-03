// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce;

import consulo.versionControlSystem.VcsException;

public interface PerforceRunnerI {
    FStat fstat(P4File p4File) throws VcsException;
    void edit(P4File file) throws VcsException;
    void revert(P4File p4File, boolean justTry) throws VcsException;
    ExecResult sync(P4File p4File, boolean forceSync) throws VcsException;
}

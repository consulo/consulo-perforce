// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.login;

import consulo.versionControlSystem.VcsException;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;

public interface LoginSupport {
    boolean silentLogin(P4Connection connection) throws VcsException;
    void notLogged(P4Connection connection);

}

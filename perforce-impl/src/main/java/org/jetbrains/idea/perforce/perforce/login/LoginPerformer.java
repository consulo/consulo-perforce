// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.login;

import org.jetbrains.idea.perforce.perforce.connections.P4Connection;

public interface LoginPerformer {
    LoginState getLoginState();

    boolean isCredentialsChanged();
    LoginState login(String password);
    LoginState loginWithStoredPassword();
    P4Connection getMyConnection();
    LoginState changePassword(String oldPass, String password);
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.login;

import org.jspecify.annotations.Nullable;
import org.jetbrains.idea.perforce.perforce.PerforceRunner;

public class LoginState {
    public static final LoginState SUCCESS = new LoginState(true, -1, null);
    private final boolean mySuccess;
    private final long myTimeLeft;
    private final @Nullable String myError;

    public LoginState(boolean success, long timeLeft, @Nullable String error) {
        mySuccess = success;
        myTimeLeft = timeLeft;
        myError = error;
    }

    public boolean isSuccess() {
        return mySuccess;
    }

    public long getTimeLeft() {
        return myTimeLeft;
    }

    public @Nullable String getError() {
        return myError;
    }

    public boolean isPasswordInvalid() {
        return myError != null && (myError.contains(PerforceRunner.PASSWORD_INVALID_MESSAGE2) || myError.contains(PerforceRunner.AUTHENTICATION_FAILED_MESSAGE));
    }

    @Override
    public String toString() {
        return "LoginState{" +
               "mySuccess=" + mySuccess +
               ", myTimeLeft=" + myTimeLeft +
               ", myError='" + myError + '\'' +
               '}';
    }
}

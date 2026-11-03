// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.perforce.checkout;

import java.util.Objects;

final class PerforceCloneParams {
    private final String myServer;
    private final String myUser;
    private final String myPassword;
    private final String myClient;
    private final String myDirectory;

    PerforceCloneParams(String server, String user, String password, String client, String directory) {
        myServer = server;
        myUser = user;
        myPassword = password;
        myClient = client;
        myDirectory = directory;
    }

    public String getServer() {
        return myServer;
    }

    public String getUser() {
        return myUser;
    }

    public String getPassword() {
        return myPassword;
    }

    public String getClient() {
        return myClient;
    }

    public String getDirectory() {
        return myDirectory;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PerforceCloneParams that)) {
            return false;
        }
        return myServer.equals(that.myServer) &&
            myUser.equals(that.myUser) &&
            myPassword.equals(that.myPassword) &&
            myClient.equals(that.myClient) &&
            myDirectory.equals(that.myDirectory);
    }

    @Override
    public int hashCode() {
        return Objects.hash(myServer, myUser, myPassword, myClient, myDirectory);
    }

    @Override
    public String toString() {
        return "PerforceCloneParams(server=" + myServer + ", user=" + myUser + ", client=" + myClient + ", directory=" + myDirectory + ")";
    }
}

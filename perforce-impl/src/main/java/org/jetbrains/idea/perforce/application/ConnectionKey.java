// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.application;

import org.jspecify.annotations.Nullable;


/**
 * There are two main kinds of p4 connections:
 *   1. singleton, where p4 settings are given in IDE settings
 *   2. parameterized, where P4CONFIG environment is used, or a special file on disk with server/client/user parameters in it
 *
 * In the second case, different VCS roots have different {@link org.jetbrains.idea.perforce.perforce.connections.P4ParametersConnection}s.
 * But quite often those connections still have the same server, client and user.
 * So it makes sense to treat those connections as one when querying the server, to execute just one p4 command instead of as many as there are VCS roots
 * Hence {@link org.jetbrains.idea.perforce.perforce.connections.P4Connection#getConnectionKey()} which returns an instance of ConnectionKey.
 * These instances are equal for connections where it's safe to replace many p4 commands with one.
 */
public class ConnectionKey {
  private final String myServer;
  private final String myClient;
  private final String myUser;

  public ConnectionKey(String server, String client, String user) {
    myServer = server;
    myClient = client;
    myUser = user;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ConnectionKey that = (ConnectionKey)o;

    if (!myClient.equals(that.myClient)) {
      return false;
    }
    if (!myServer.equals(that.myServer)) {
      return false;
    }
    if (!myUser.equals(that.myUser)) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result;
    result = myServer.hashCode();
    result = 31 * result + myClient.hashCode();
    result = 31 * result + myUser.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return myServer + ", " + myUser + "@" + myClient;
  }

  public String getServer() {
    return myServer;
  }

  public String getClient() {
    return myClient;
  }

  public String getUser() {
    return myUser;
  }
}

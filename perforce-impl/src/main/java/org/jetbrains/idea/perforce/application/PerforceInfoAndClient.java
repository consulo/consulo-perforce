// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.application;

import com.google.common.base.MoreObjects;
import consulo.logging.Logger;
import consulo.perforce.localize.PerforceLocalize;
import consulo.versionControlSystem.VcsException;
import org.jetbrains.idea.perforce.perforce.PerforceAuthenticationException;
import org.jetbrains.idea.perforce.perforce.PerforceRunner;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PerforceInfoAndClient {
  private static final Logger LOG = Logger.getInstance(PerforceInfoAndClient.class);

  static ConnectionInfo calcInfo(P4Connection connection,
                                          PerforceRunner runner,
                                          ClientRootsCache clientRootsCache) {
    try {
      Map<String, List<String>> infoMap = calcInfoMap(connection, runner);
      return new ConnectionInfo(infoMap,
                                new ClientData(calcClientMap(connection, runner, extractClient(infoMap), clientRootsCache)));
    }
    catch (PerforceAuthenticationException e) {
      return new ConnectionInfo(e);
    }
    catch(VcsException e){
      LOG.info(e);
      return new ConnectionInfo(e);
    }
  }

  private static String extractClient(Map<String, List<String>> infoMap) throws VcsException {
    List<String> clientValue = infoMap.get(PerforceRunner.CLIENT_NAME);
    if (clientValue == null || clientValue.isEmpty()) {
      throw new VcsException(PerforceLocalize.errorNoClientNameInInfoSpecificationFound());
    }

    return clientValue.get(0);
  }

  private static Map<String, List<String>> calcClientMap(P4Connection connection,
                                                                  PerforceRunner runner,
                                                                  String client,
                                                                  ClientRootsCache clientRootsCache) throws VcsException {
    Map<String, List<String>> clientMap = runner.loadClient(client, connection);
    convertRoots(clientMap, PerforceRunner.CLIENTSPEC_ROOT, clientRootsCache);
    convertRoots(clientMap, PerforceRunner.CLIENTSPEC_ALTROOTS, clientRootsCache);
    return clientMap;
  }

  private static Map<String, List<String>> calcInfoMap(P4Connection connection, PerforceRunner runner) throws VcsException {
    Map<String, List<String>> infoMap = runner.getInfo(connection);
    if (infoMap.containsKey(PerforceRunner.CLIENT_UNKNOWN)) {
      throw new VcsException(PerforceLocalize.errorClientUnknown());
    }
    // the following fields change on every invocation, and changes in these fields should not cause the
    // serverDataChanged notification to be fired
    infoMap.remove(PerforceRunner.CLIENT_ADDRESS);
    infoMap.remove(PerforceRunner.SERVER_DATE);
    infoMap.remove(PerforceRunner.PEER_ADDRESS);
    return infoMap;
  }

  public static RefreshInfo recalculateInfos(Map<P4Connection, ConnectionInfo> old,
                                             Collection<? extends P4Connection> allConnections,
                                             PerforceRunner runner,
                                             ClientRootsCache clientRootsCache) {
    Map<P4Connection, ConnectionInfo> newMap = calculateInfos(allConnections, runner, clientRootsCache);

    boolean hasErrors = newMap.values().stream().anyMatch(ConnectionInfo::hasErrorsBesidesAuthentication);

    Map<P4Connection, ConnectionInfo> info = new HashMap<>();
    if (hasErrors) {
      info.putAll(old);
    }
    info.putAll(newMap);
    return new RefreshInfo(Collections.unmodifiableMap(info), hasErrors);
  }

  public static Map<P4Connection, ConnectionInfo> calculateInfos(Collection<? extends P4Connection> allConnections,
                                                                 PerforceRunner runner,
                                                                 ClientRootsCache clientRootsCache) {
    Map<ConnectionKey, ConnectionInfo> cache = new HashMap<>();
    Map<P4Connection, ConnectionInfo> newMap = new HashMap<>();

    for (P4Connection connection : allConnections) {
      ConnectionKey key = connection.getConnectionKey();
      ConnectionInfo info = cache.get(key);
      if (info == null) {
        cache.put(key, info = calcInfo(connection, runner, clientRootsCache));
      }
      newMap.put(connection, info);
    }

    return newMap;
  }

  private static void convertRoots(Map<String, List<String>> clientSpec, String key, ClientRootsCache clientRootsCache) {
    List<String> in = clientSpec.get(key);
    if (in == null) {
      return;
    }
    List<String> out = new ArrayList<>(in.size());

    for (String s : in) {
      out.add(clientRootsCache.putGet(s));
    }

    clientSpec.put(key, out);
  }

  public static class RefreshInfo {
    public final Map<P4Connection, ConnectionInfo> newInfo;
    public final boolean hasAnyErrorsBesidesAuthentication;

    public RefreshInfo(Map<P4Connection, ConnectionInfo> newInfo, boolean hasAnyErrorsBesidesAuthentication) {
      this.newInfo = newInfo;
      this.hasAnyErrorsBesidesAuthentication = hasAnyErrorsBesidesAuthentication;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
        .add("hasAnyErrorsBesidesAuthentication", hasAnyErrorsBesidesAuthentication)
        .add("newInfo", newInfo)
        .toString();
    }
  }
}

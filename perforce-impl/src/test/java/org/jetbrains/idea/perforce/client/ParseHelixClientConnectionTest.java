// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.client;

import consulo.project.Project;
import org.jetbrains.idea.perforce.perforce.connections.HelixClientConnectionParametersProvider;
import org.jetbrains.idea.perforce.perforce.connections.HelixClientConnectionParametersProvider.ConfigFiles;
import org.jetbrains.idea.perforce.perforce.connections.P4ConnectionParameters;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Proxy;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ParseHelixClientConnectionTest {
  /**
   * The provider does not use the project; any call on this stub fails the test.
   */
  private static final Project UNUSED_PROJECT = (Project)Proxy.newProxyInstance(
    ParseHelixClientConnectionTest.class.getClassLoader(),
    new Class<?>[]{Project.class},
    (proxy, method, args) -> {
      throw new UnsupportedOperationException(method.toString());
    }
  );

  private static File getResource(String resourceName) throws URISyntaxException {
    URL resource = Objects.requireNonNull(ParseHelixClientConnectionTest.class.getResource("/" + resourceName), resourceName + " doesn't exist!");
    return new File(resource.toURI());
  }

  private static ConfigFiles configFiles(File connectionMapFile) {
    return new ConfigFiles() {
      @Override
      public File getClientConfig() {
        return connectionMapFile;
      }

      @Override
      public File getAdminConfig() {
        return connectionMapFile;
      }
    };
  }

  @Test
  public void testParseSingleClientConnection() throws URISyntaxException {
    File connectionMapFile = getResource("connectionmap-single.xml");
    assertTrue(connectionMapFile.exists());

    List<P4ConnectionParameters> connectionParameters = new ArrayList<>(
      new HelixClientConnectionParametersProvider(configFiles(connectionMapFile)).getConnectionParameters(UNUSED_PROJECT)
    );
    assertEquals(1, connectionParameters.size());

    P4ConnectionParameters singleConnectionParameters = connectionParameters.get(0);
    assertEquals("SomeUser", singleConnectionParameters.getUser());
    assertEquals("ssl:server-host:1666", singleConnectionParameters.getServer());
  }

  @Test
  public void testParseMultiClientConnection() throws URISyntaxException {
    File connectionMapFile = getResource("connectionmap-multi.xml");
    assertTrue(connectionMapFile.exists());

    List<P4ConnectionParameters> connectionParameters = new ArrayList<>(
      new HelixClientConnectionParametersProvider(configFiles(connectionMapFile)).getConnectionParameters(UNUSED_PROJECT)
    );
    assertEquals(2, connectionParameters.size());

    P4ConnectionParameters firstConnectionParameters = connectionParameters.get(0);
    assertEquals("SomeFirstUser", firstConnectionParameters.getUser());
    assertEquals("ssl:server-host-1:1666", firstConnectionParameters.getServer());

    P4ConnectionParameters secondConnectionParameters = connectionParameters.get(connectionParameters.size() - 1);
    assertEquals("SomeSecondUser", secondConnectionParameters.getUser());
    assertEquals("ssl:server-host-2:1666", secondConnectionParameters.getServer());
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.client;

import org.jetbrains.idea.perforce.perforce.connections.P4ClientParser;
import org.jetbrains.idea.perforce.perforce.connections.P4ConnectionParameters;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class P4ClientParserTest {
  private final P4ClientParser parser = new P4ClientParser();
  private final P4ConnectionParameters dummyParameters = new P4ConnectionParameters();

  private P4ClientParser.PerforceClient parse(String line, P4ConnectionParameters parameters) {
    return Objects.requireNonNull(parser.parseClientLine(line, parameters), "client expected for line: " + line);
  }

  @Test
  public void testParseClientWithDescription() {
    String line = "Client my-workspace 2025/10/07 root /Users/john/projects/myproject 'My workspace description'";

    P4ClientParser.PerforceClient client = parse(line, dummyParameters);

    assertEquals("my-workspace", client.getClientName());
    assertEquals("2025/10/07", client.getLastUpdate());
    assertEquals("/Users/john/projects/myproject", client.getWorkspaceRootPath());
    assertEquals("My workspace description", client.getDescription());
  }

  @Test
  public void testParseClientWithEmptyDescription() {
    String line = "Client john_ws 2025/10/07 root /Users/user/perforce-project ''";

    P4ClientParser.PerforceClient client = parse(line, dummyParameters);

    assertEquals("john_ws", client.getClientName());
    assertEquals("2025/10/07", client.getLastUpdate());
    assertEquals("/Users/user/perforce-project", client.getWorkspaceRootPath());
    assertEquals("", client.getDescription());
  }

  @Test
  public void testParseClientWithWindowsPath() {
    String line = "Client dev-workspace 2024/05/15 root C:\\Users\\Developer\\Projects\\app 'Development workspace'";

    P4ClientParser.PerforceClient client = parse(line, dummyParameters);

    assertEquals("dev-workspace", client.getClientName());
    assertEquals("2024/05/15", client.getLastUpdate());
    assertEquals("C:\\Users\\Developer\\Projects\\app", client.getWorkspaceRootPath());
    assertEquals("Development workspace", client.getDescription());
  }

  @Test
  public void testParseClientWithSpecialCharactersInDescription() {
    String line = "Client test-ws 2023/12/01 root /home/user/test 'Test workspace (v2) - updated!'";

    P4ClientParser.PerforceClient client = parse(line, dummyParameters);

    assertEquals("test-ws", client.getClientName());
    assertEquals("2023/12/01", client.getLastUpdate());
    assertEquals("/home/user/test", client.getWorkspaceRootPath());
    assertEquals("Test workspace (v2) - updated!", client.getDescription());
  }

  @Test
  public void testParseClientWithUnderscoresAndNumbersInName() {
    String line = "Client user_workspace_123 2025/01/20 root /var/perforce/ws 'Workspace 123'";

    P4ClientParser.PerforceClient client = parse(line, dummyParameters);

    assertEquals("user_workspace_123", client.getClientName());
    assertEquals("2025/01/20", client.getLastUpdate());
    assertEquals("/var/perforce/ws", client.getWorkspaceRootPath());
    assertEquals("Workspace 123", client.getDescription());
  }

  @Test
  public void testParseClientWithMultipleSpacesInDescription() {
    String line = "Client ws 2025/06/10 root /path/to/workspace 'Description   with   multiple   spaces'";

    P4ClientParser.PerforceClient client = parse(line, dummyParameters);

    assertEquals("ws", client.getClientName());
    assertEquals("Description   with   multiple   spaces", client.getDescription());
  }

  @Test
  public void testInvalidLineReturnsNull() {
    List<String> invalidLines = List.of(
      "",
      "Invalid line",
      "Client",
      "Client name-only",
      "Client name 2025/10/07",
      "Client name 2025/10/07 root",
      "Client name 2025/10/07 root /path",
      "Client name invalid-date root /path 'desc'"
    );

    for (String line : invalidLines) {
      P4ClientParser.PerforceClient client = parser.parseClientLine(line, dummyParameters);
      assertNull(client, "Expected null for line: " + line);
    }
  }

  @Test
  public void testParametersArePreserved() {
    P4ConnectionParameters parameters = new P4ConnectionParameters();
    parameters.setServer("perforce.example.com:1666");
    parameters.setUser("testuser");
    parameters.setClient("testclient");
    String line = "Client my-ws 2025/10/07 root /home/user/workspace 'Test'";

    P4ClientParser.PerforceClient client = parse(line, parameters);

    assertSame(parameters, client.getParameters());
  }
}

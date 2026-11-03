// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce;

import org.jetbrains.idea.perforce.perforce.OutputMessageParser;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SubmittedChangeListsParserPerformanceTest {
  @Test
  @Disabled("Performance test; needs the 5.6 MB 'testData/changes.txt' resource which is not bundled")
  public void test() throws IOException {
    String output;
    try (InputStream stream = Objects.requireNonNull(
      SubmittedChangeListsParserPerformanceTest.class.getResourceAsStream("/testData/changes.txt"),
      "testData/changes.txt is missing"
    )) {
      output = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
    long start = System.currentTimeMillis();
    OutputMessageParser.processChangesOutput(output);
    long executionTime = System.currentTimeMillis() - start;
    assertTrue(executionTime < 30000, "Execution time: " + executionTime);
  }
}

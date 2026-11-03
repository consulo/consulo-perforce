package org.jetbrains.idea.perforce;

import org.junit.jupiter.api.Test;
import org.jetbrains.idea.perforce.perforce.OutputMessageParser;

import static org.junit.jupiter.api.Assertions.*;

public class ServerVersionParsingTest {
  @Test
  public void test() {
    ServerVersion serverVersion = OutputMessageParser.parseServerVersion("P4D/NTX86/2004.2/68597 (2004/09/03)");
    assertEquals(2004, serverVersion.getVersionYear());
    assertEquals(2, serverVersion.getVersionNum());
  }

  @Test
  public void testPatched() {
    ServerVersion serverVersion = OutputMessageParser.parseServerVersion("P4D/LINUX24X86/2005.2.PATCH/100601 (2006/05/26)");
    assertEquals(2005, serverVersion.getVersionYear());
    assertEquals(2, serverVersion.getVersionNum());
  }
}

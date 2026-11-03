// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce;

import org.jetbrains.idea.perforce.perforce.View;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class PerforceViewTest {
  private static View view(String viewString) {
    return Objects.requireNonNull(View.create(viewString));
  }

  @Test
  public void should_support_wildcards_recursive() {
    View view = view("//depot/...  //kir-mac/...");

    String path = view.match("//depot/file.txt", "kir-mac");
    assertEquals("file.txt", path);
  }

  @Test
  public void should_support_wildcards_unrecursive() {
    View view = view("//depot/*  //kir-mac/*");

    String path = view.match("//depot/file.txt", "kir-mac");
    assertEquals("file.txt", path);

    String path1 = view.match("//depot/path/file.txt", "kir-mac");
    assertNull(path1);
  }

  @Test
  public void should_support_wildcards_unrecursive2() {
    View view = view("//depot/.../sss/*  //kir-mac/.../sss/*");

    String path = view.match("//depot/a/b/c/sss/file.txt", "kir-mac");
    assertEquals("a/b/c/sss/file.txt", path);

    String path1 = view.match("//depot/a/b/c/sss/ddd/file.txt", "kir-mac");
    assertNull(path1);
  }
}

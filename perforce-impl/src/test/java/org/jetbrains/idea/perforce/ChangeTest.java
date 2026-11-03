/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package org.jetbrains.idea.perforce;

import consulo.versionControlSystem.VcsException;
import org.junit.jupiter.api.Test;
import org.jetbrains.idea.perforce.perforce.PerforceChange;
import org.jetbrains.idea.perforce.perforce.View;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings({"ConstantConditions"})
public class ChangeTest {
  private static View view(String viewString) {
    return Objects.requireNonNull(View.create(viewString));
  }

  @Test
  public void testView() {
    View view = view("\t//depot/javacvs/src/javacvs/... //test-client2/client2_view1/...");
    assertEquals("//depot/javacvs/src/javacvs/...", view.getDepotPath());
    assertEquals("//test-client2/client2_view1/...", view.getLocalPath());
  }

  @Test
  public void testChange() throws Exception {
    ArrayList<View> views = new ArrayList<>();
    views.add(view("\t//depot/javacvs/src/javacvs/... //test-client2/client2_view1/..."));
    views.add(view("\t//depot/javacvs/src/smartcvs/... //test-client2/client2_view2/..."));
    PerforceChange change = PerforceChange.createOn("\t//depot/javacvs/src/javacvs/readme2.txt\t# edit",
      new TestPerforceClient("test-client2", null, "c://TestPerforce2/", views));
    assertEquals(new File("c://TestPerforce2/client2_view1/readme2.txt"), Objects.requireNonNull(change).getFile());
  }

  @Test
  public void testMultipleMatchesOnSameView() throws Exception {
    ArrayList<View> views = new ArrayList<>();
    views.add(view("\t//ExceptionAnalyser/... //unit-056/ExceptionAnalyser/..."));
    TestPerforceClient testClient = new TestPerforceClient("unit-056", null, "c:/yole", views);
    PerforceChange change = PerforceChange.createOn("//ExceptionAnalyser/Sisyphus/ideaplugin/src/com/intellij/sisyphus/ideaplugin/A.java\t# add",
                                          testClient);
    assertEquals(new File("c:/yole/ExceptionAnalyser/Sisyphus/ideaplugin/src/com/intellij/sisyphus/ideaplugin/A.java"), Objects.requireNonNull(change).getFile());

    PerforceChange change2 = PerforceChange.createOn("//ExceptionAnalyser/Sisyphus/ideaplugin/src/com/intellij/sisyphus/ideaplugin/B.java\t# add",
                                          testClient);
    assertEquals(new File("c:/yole/ExceptionAnalyser/Sisyphus/ideaplugin/src/com/intellij/sisyphus/ideaplugin/B.java"), Objects.requireNonNull(change2).getFile());
  }

  @Test
  public void testSCR1920() throws Exception {
    ArrayList<View> views = new ArrayList<>();
    views.add(view("\t//eMeasurement/workspace/%%1/ems_4.8_darwin/... //lanebr_4.8/%%1/..."));
    PerforceChange change = PerforceChange.createOn("\t//eMeasurement/workspace/com.ncs.es.reporting.v40.common/ems_4.8_darwin/src/com/ncs/es/reporting/v40/common/persistence/OrgUnitPersistenceImpl.java\t# edit",
      new TestPerforceClient("lanebr_4.8", null, "C:/esbe48", views));
    assertEquals(new File("C:/esbe48/com.ncs.es.reporting.v40.common/src/com/ncs/es/reporting/v40/common/persistence/OrgUnitPersistenceImpl.java"), Objects.requireNonNull(change).getFile());
  }

  @Test
  public void testSCR1920_2() throws Exception {
    ArrayList<View> views = new ArrayList<>();
    views.add(view("\t//eMeasurement/workspace/%%1.%%2/ems_4.8_darwin/... //lanebr_4.8/%%2.%%1/..."));
    PerforceChange change = PerforceChange.createOn("\t//eMeasurement/workspace/com.ncs.es.reporting.v40.common/ems_4.8_darwin/src/com/ncs/es/reporting/v40/common/persistence/OrgUnitPersistenceImpl.java\t# edit",
      new TestPerforceClient("lanebr_4.8", null, "C:/esbe48", views));
    assertEquals(new File("C:/esbe48/common.com.ncs.es.reporting.v40/src/com/ncs/es/reporting/v40/common/persistence/OrgUnitPersistenceImpl.java"), Objects.requireNonNull(change).getFile());
  }

  @Test
  public void testCasesInDepot() throws Exception {
    ArrayList<View> views = new ArrayList<>();
    views.add(view("\t//DEPOT/javacvs/src/javacvs/... //test-client2/client2_view1/..."));
    views.add(view("\t//DEPOT/javacvs/src/smartcvs/... //test-client2/client2_view2/..."));
    PerforceChange change = PerforceChange.createOn("\t//depot/javacvs/src/javacvs/readme2.txt\t# edit",
      new TestPerforceClient("test-client2", null, "c://TestPerforce2/", views));
    assertEquals(new File("c://TestPerforce2/client2_view1/readme2.txt"), Objects.requireNonNull(change).getFile());
  }

  @Test
  public void testConvertDepotPathToClient() {

    assertEquals(new File("client2_view1/readme2.txt"),
      new File(Objects.requireNonNull(View.getRelativePath("//depot/javacvs/src/javacvs/readme2.txt", "test-client2",
        Arrays.asList(view("\t//depot/javacvs/src/javacvs/... //test-client2/client2_view1/..."))))));

    File expected = new File("common.com.ncs.es.reporting.v40/src/com/ncs/es/reporting/v40/common/persistence/OrgUnitPersistenceImpl.java");
    String depotPath = "//eMeasurement/workspace/com.ncs.es.reporting.v40.common/ems_4.8_darwin/src/com/ncs/es/reporting/v40/common/persistence/OrgUnitPersistenceImpl.java";

    String actual = View.getRelativePath(depotPath,
      "lanebr_4.8",
      Arrays.asList(view("\t//eMeasurement/workspace/%%1.%%2/ems_4.8_darwin/... //lanebr_4.8/%%2.%%1/...")));

    assertEquals(expected, new File(Objects.requireNonNull(actual)));

  }

  @Test
  public void testExcludeMappingUsingWildcards() throws VcsException {
    ArrayList<View> views = new ArrayList<>();
    views.add(view("\t//DEPOT/... //test-client2/..."));
    views.add(view("\t-//DEPOT/.../exclude/... //test-client2/.../exclude/..."));

    PerforceChange change = PerforceChange.createOn("\t//depot/javacvs/src/javacvs/readme2.txt\t# edit",
      new TestPerforceClient("test-client2", null, "c://TestPerforce2/", views));
    assertNotNull(change);
    assertEquals(new File("c://TestPerforce2/javacvs/src/javacvs/readme2.txt"), Objects.requireNonNull(change).getFile());

  }

  @Test
  public void testExcludeMappingUsingWildcards_2() throws VcsException {
    ArrayList<View> views = new ArrayList<>();
    views.add(view("\t//DEPOT/... //test-client2/..."));
    views.add(view("\t-//DEPOT/.../exclude/... //test-client2/.../exclude/..."));

    PerforceChange change = PerforceChange.createOn("\t//depot/javacvs/exclude/javacvs/readme2.txt\t# edit",
      new TestPerforceClient("test-client2", null, "c://TestPerforce2/", views));
    assertNull(change);
  }

  @Test
  public void testExcludeMappingUsingWildcards_3() throws VcsException {
    ArrayList<View> views = new ArrayList<>();
    views.add(view("\t//DEPOT/.../include/... //test-client2/.../include/..."));

    PerforceChange change = PerforceChange.createOn("\t//depot/javacvs/include/javacvs/readme2.txt\t# edit",
      new TestPerforceClient("test-client2", null, "c://TestPerforce2/", views));
    assertNotNull(change);
    assertEquals(new File("c://TestPerforce2/javacvs/include/javacvs/readme2.txt"), Objects.requireNonNull(change).getFile());
  }

  @Test
  public void testIsExcluded() {
    List<View> views = Arrays.asList(view("//DEPOT/... //client/..."), view("-//DEPOT/.../*.xml //client/.../*.txt"),
                                     view("-//DEPOT/bar/.../*.xml //client/.../*.xsl"));
    assertFalse(View.isExcluded("//client/a.txt", views));

    assertTrue(View.isExcluded("//client/x/a.txt", views));
    assertTrue(View.isExcluded("//client/foo/a.txt", views));

    assertFalse(View.isExcluded("//client/x/a.xml", views));
    assertTrue(View.isExcluded("//client/x/a.xsl", views));
    assertTrue(View.isExcluded("//client/foo/a.xsl", views));
  }

  @Test
  public void testReInclude() {
    List<View> views = Arrays.asList(view("//DEPOT/... //client/..."), view("-//DEPOT/foo/... //client/foo/..."),
                                     view("//DEPOT/foo/bar/... //client/foo/bar/..."));
    assertFalse(View.isExcluded("//client/a.txt", views));
    assertTrue(View.isExcluded("//client/foo/a.txt", views));
    assertFalse(View.isExcluded("//client/foo/bar/a.txt", views));
  }

  @Test
  public void testLoop() throws VcsException {
    ArrayList<View> views = new ArrayList<>();
    views.add(view("\t//Actuate/Development/f1j/%%1 //jboehle.snowmass2/f1j/%%1"));
    PerforceChange change = PerforceChange.createOn("\t//Actuate/Development/f1j/readme2.txt\t# edit",
      new TestPerforceClient("jboehle.snowmass2", null, "c://TestPerforce2/", views));
    assertEquals(new File("c://TestPerforce2/f1j/readme2.txt"), Objects.requireNonNull(change).getFile());
  }

  @Test
  public void testPlugin() {
    ArrayList<View> views = new ArrayList<>();
    views.add(view("\t//... //jboehle.snowmass2/f1j/..."));
    assertNotNull(View.getRelativePath("//Actuate/Development/f1j/...", "jboehle.snowmass2", views));
  }

  @Test
  public void testTW4207() {
    View view = view(
      "\"//APPS/source/dev/4.2/SQLServer/DBGhost Build Output Scripts/...\" \"//MyApps/source/dev/4.2/SQLServer/DBGhost Build Output Scripts/...\"");

    assertNotNull(view);

    assertEquals("//APPS/source/dev/4.2/SQLServer/DBGhost Build Output Scripts/...", view.getDepotPath());
  }

  @Test
  public void testLongestMatch() throws VcsException {
    ArrayList<View> views = new ArrayList<>();
    views.add(view("\t//depot/code/work/... //LOCAL/code/work/..."));
    views.add(view("\t//depot/code/work/web/website/... //LOCAL/code/work/web/web_src/website/..."));

    PerforceChange change = PerforceChange.createOn("\t//depot/code/work/web/website/newclass.java\t# edit",
                                                          new TestPerforceClient("LOCAL", null, "c:///local", views));
    assertEquals(new File("c://local/code/work/web/web_src/website/newclass.java"), Objects.requireNonNull(change).getFile());
  }
}

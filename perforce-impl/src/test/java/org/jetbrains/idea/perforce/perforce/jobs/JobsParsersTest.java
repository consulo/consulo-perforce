// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.jobs;

import consulo.util.lang.Pair;
import consulo.versionControlSystem.VcsException;
import org.jetbrains.idea.perforce.application.ConnectionKey;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parsing of 'p4 jobspec -o', 'p4 jobs', 'p4 job -o' and 'p4 fixes' output, and building of 'p4 jobs' arguments.
 */
public class JobsParsersTest {
  private static final P4Connection CONNECTION = (P4Connection)Proxy.newProxyInstance(
    JobsParsersTest.class.getClassLoader(),
    new Class<?>[]{P4Connection.class},
    (proxy, method, args) -> {
      throw new UnsupportedOperationException(method.toString());
    }
  );
  private static final ConnectionKey KEY = new ConnectionKey("server:1666", "client", "user");

  // as returned by OutputMessageParser.readOutputLines() for 'p4 jobspec -o' (empty lines dropped)
  private static final List<String> JOB_SPEC = Arrays.asList(
    "# A Perforce Job Specification.",
    "#",
    "#  Updating this form can be dangerous!",
    "Fields:",
    "\t101 Job word 32 required",
    "\t102 Status select 10 required",
    "\t103 User word 32 required",
    "\t104 Date date 20 always",
    "\t105 Description text 0 required",
    "\t106 Severity select 10 optional",
    "Values:",
    "\tStatus open/suspended/closed",
    "Presets:",
    "\tStatus open"
  );

  private static PerforceJobSpecification spec() throws VcsException {
    return new SpecificationParser(JOB_SPEC).parse();
  }

  private static String value(PerforceJob job, StandardJobFields field) {
    return Objects.requireNonNull(job.getValueForStandardField(field), field.name()).getValue();
  }

  @Test
  public void testSpecification() throws VcsException {
    PerforceJobSpecification spec = spec();
    assertEquals(6, spec.getFields().size());

    PerforceJobField job = Objects.requireNonNull(spec.getFieldByCode(101));
    assertEquals("Job", job.getName());
    assertTrue(StandardJobFields.isStandardField(job));

    PerforceJobField severity = Objects.requireNonNull(spec.getFieldByCode(106));
    assertEquals("Severity", severity.getName());
    assertFalse(StandardJobFields.isStandardField(severity));

    assertEquals(List.of("Severity"), JobsWorker.getFreeFields(spec));
  }

  @Test
  public void testSpecificationErrors() {
    assertThrows(VcsException.class, () -> new SpecificationParser(List.of("Fields:", "\t99 Job word 32 required")).parse());
    assertThrows(VcsException.class, () -> new SpecificationParser(List.of("Fields:", "\tabc Job word 32 required")).parse());
    assertThrows(VcsException.class, () -> new SpecificationParser(List.of("Fields:", "\t101 Job word 32 sometimes")).parse());
    assertThrows(VcsException.class, () -> new SpecificationParser(List.of("Fields:", "\t101 Job word")).parse());
  }

  @Test
  public void testPersistenceType() {
    assertEquals(PerforceJobPersistenceType._default, PerforceJobPersistenceType.parse("Default"));
    assertEquals(PerforceJobPersistenceType.always, PerforceJobPersistenceType.parse("always"));
    assertNull(PerforceJobPersistenceType.parse("never"));
  }

  @Test
  public void testJobsOutput() throws VcsException {
    List<String> lines = Arrays.asList(
      "job000001 on 2011/04/12 by john *open* 'Fix the build '",
      "job000002 on 2011/04/13 by mary *closed* 'Second job '",
      "\tcontinued description"
    );
    List<PerforceJob> jobs = new JobsOutputParser(spec(), lines, CONNECTION, KEY).parse();
    assertEquals(2, jobs.size());

    PerforceJob first = jobs.get(0);
    assertEquals("job000001", first.getName());
    assertEquals("2011/04/12", value(first, StandardJobFields.date));
    assertEquals("john", value(first, StandardJobFields.user));
    assertEquals("open", value(first, StandardJobFields.status));
    assertEquals("Fix the build", value(first, StandardJobFields.description));
    assertEquals(KEY, first.getConnectionKey());

    PerforceJob second = jobs.get(1);
    assertEquals("job000002", second.getName());
    assertEquals("closed", value(second, StandardJobFields.status));
    assertEquals("Second job continued description", value(second, StandardJobFields.description));
  }

  @Test
  public void testJobsOutputWithoutOptionalParts() throws VcsException {
    List<PerforceJob> jobs = new JobsOutputParser(spec(), List.of("job000003"), CONNECTION, KEY).parse();
    assertEquals(1, jobs.size());
    PerforceJob job = jobs.get(0);
    assertEquals("job000003", job.getName());
    assertNull(job.getValueForStandardField(StandardJobFields.date));
    assertNull(job.getValueForStandardField(StandardJobFields.user));
    assertNull(job.getValueForStandardField(StandardJobFields.status));
    assertEquals("", value(job, StandardJobFields.description));
  }

  @Test
  public void testJobsOutputErrors() throws VcsException {
    PerforceJobSpecification spec = spec();
    assertThrows(VcsException.class, () -> new JobsOutputParser(spec, List.of("\torphan line"), CONNECTION, KEY).parse());
    assertThrows(VcsException.class, () -> new JobsOutputParser(spec, List.of("job1 on 2011/04/12 by john *open"), CONNECTION, KEY).parse());
  }

  @Test
  public void testJobDetails() throws VcsException {
    List<String> lines = Arrays.asList(
      "# A Perforce Job Specification.",
      "",
      "Job:\tjob000001",
      "",
      "Status:\topen",
      "",
      "Description:",
      "\tfirst line",
      "\tsecond line",
      "  # indented comment"
    );
    List<Pair<String, String>> details = new JobDetailsParser(lines.stream().filter(s -> !s.isEmpty()).toList()).parse();
    assertEquals(
      List.of(Pair.create("Job", "job000001"), Pair.create("Status", "open"), Pair.create("Description", "\nfirst line\nsecond line")),
      details
    );

    assertThrows(VcsException.class, () -> new JobDetailsParser(List.of("\tvalue without field")).parse());
    assertThrows(VcsException.class, () -> new JobDetailsParser(List.of("no field name")).parse());
  }

  @Test
  public void testFixes() throws VcsException {
    List<String> names = new FixesOutputParser(Arrays.asList(
      "job000001 fixed by change 12 on 2011/04/12 by john@ws (closed)",
      "\tdescription line",
      "job000002"
    )).parseJobNames();
    assertEquals(List.of("job000001", "job000002"), names);
  }

  @Test
  public void testByNamesConstraint() {
    String[] params = new ByNamesConstraint(List.of("a|b", "c\\d")).addParams(new String[]{"jobs"});
    assertArrayEquals(new String[]{"jobs", "-e", "Job=a\\|b|c\\\\d"}, params);
    assertEquals(-1, new ByNamesConstraint(List.of("a")).getMaxCount());

    String[] unchanged = {"jobs"};
    assertArrayEquals(unchanged, new ByNamesConstraint(List.of()).addParams(unchanged));
  }

  @Test
  public void testFullSearchSpecificator() {
    FullSearchSpecificator specificator = new FullSearchSpecificator();
    assertArrayEquals(new String[]{"jobs", "-m", "501"}, specificator.addParams(new String[]{"jobs"}));

    specificator.addStandardConstraint(FullSearchSpecificator.Parts.jobname, "job*");
    specificator.addStandardConstraint(FullSearchSpecificator.Parts.dateBefore, "2011/01/01");
    specificator.addStandardConstraint(FullSearchSpecificator.Parts.dateAfter, "2010/01/01");
    assertArrayEquals(
      new String[]{"jobs", "-m", "501", "-e", "(Job=job*) (Date<=2011/01/01) (Date>=2010/01/01) "},
      specificator.addParams(new String[]{"jobs"})
    );
    assertEquals(500, specificator.getMaxCount());
  }

  @Test
  public void testJobViewSearchSpecificator() {
    assertArrayEquals(new String[]{"jobs", "-m", "501"}, new JobViewSearchSpecificator(null, " ").addParams(new String[]{"jobs"}));
    assertArrayEquals(
      new String[]{"jobs", "-m", "501", "-e", "(status=open)"},
      new JobViewSearchSpecificator("status=open", null).addParams(new String[]{"jobs"})
    );
    assertArrayEquals(
      new String[]{"jobs", "-m", "501", "-e", "(status=open) & (user=john)"},
      new JobViewSearchSpecificator("status=open", "user=john").addParams(new String[]{"jobs"})
    );
    assertEquals(50, new JobViewSearchSpecificator(null, null).getMaxCount());
  }
}

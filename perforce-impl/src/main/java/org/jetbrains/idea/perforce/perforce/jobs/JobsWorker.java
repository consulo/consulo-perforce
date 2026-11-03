// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.jobs;

import consulo.project.Project;
import consulo.util.lang.Pair;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.change.LocalChangeList;
import org.jetbrains.idea.perforce.application.ConnectionKey;
import org.jetbrains.idea.perforce.application.PerforceNumberNameSynchronizer;
import org.jetbrains.idea.perforce.perforce.PerforceRunner;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class JobsWorker {
    private final Project myProject;
    private final PerforceRunner myRunner;
    private final PerforceNumberNameSynchronizer mySynchronizer;

    Project getProject() {
        return myProject;
    }

    public JobsWorker(Project project) {
        myProject = project;

        mySynchronizer = PerforceNumberNameSynchronizer.getInstance(myProject);
        myRunner = PerforceRunner.getInstance(myProject);
    }

    public PerforceJobSpecification getSpec(P4Connection connection) throws VcsException {
        List<String> lines = myRunner.getJobSpecification(connection);
        SpecificationParser parser = new SpecificationParser(lines);
        return parser.parse();
    }

    public List<PerforceJob> getJobs(
        PerforceJobSpecification specification,
        JobsSearchSpecificator specificator,
        P4Connection connection,
        ConnectionKey key
    ) throws VcsException {
        List<String> lines = myRunner.getJobs(connection, specificator);
        JobsOutputParser parser = new JobsOutputParser(specification, lines, connection, key);
        return parser.parse();
    }

    private @Nullable Long getNativeListNumber(LocalChangeList list, ConnectionKey key) {
        return mySynchronizer.getNumber(key, list.getName());
    }

    public List<Pair<String, String>> loadJob(PerforceJob job) throws VcsException {
        List<String> lines = myRunner.getJobDetails(job);
        JobDetailsParser parser = new JobDetailsParser(lines);
        return parser.parse();
    }

    public List<String> getJobNames(LocalChangeList list, P4Connection connection, ConnectionKey key) throws VcsException {
        Long number = getNativeListNumber(list, key);
        if (number == null) {
            return Collections.emptyList();
        }

        return new FixesOutputParser(myRunner.getJobsForChange(connection, number)).parseJobNames();
    }

    public static List<String> getFreeFields(PerforceJobSpecification spec) {
        List<String> result = new ArrayList<>();
        Collection<PerforceJobField> fields = spec.getFields();
        for (PerforceJobField field : fields) {
            String name = field.getName();
            if (!StandardJobFields.isStandardField(field) && name != null) {
                result.add(name);
            }
        }
        return result;
    }

    public void addJob(PerforceJob job, LocalChangeList list) throws VcsException {
        Long number = getNativeListNumber(list, job.getConnectionKey());
        if (number != null) {
            myRunner.addJobForList(job.getConnection(), number, job.getName());
        }
    }

    public void removeJob(PerforceJob job, LocalChangeList list) throws VcsException {
        Long number = getNativeListNumber(list, job.getConnectionKey());
        if (number != null) {
            myRunner.removeJobFromList(job.getConnection(), number, job.getName());
        }
    }

    public List<PerforceJob> getJobsForList(
        PerforceJobSpecification specification,
        LocalChangeList list,
        P4Connection connection,
        ConnectionKey key
    ) throws VcsException {
        List<String> jobNames = getJobNames(list, connection, key);
        // list should be modifiable
        if (jobNames.isEmpty()) {
            return new ArrayList<>();
        }
        return getJobs(specification, new ByNamesConstraint(jobNames), connection, key);
    }
}

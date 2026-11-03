// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.jobs;

import consulo.application.Application;
import consulo.application.progress.ProgressManager;
import consulo.perforce.localize.PerforceLocalize;
import consulo.project.Project;
import consulo.util.lang.Pair;
import consulo.util.lang.ref.SimpleReference;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.change.LocalChangeList;
import org.jetbrains.idea.perforce.application.ConnectionKey;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class JobDetailsLoader {
    private final Project myProject;
    private final JobsWorker myWorker;

    public JobDetailsLoader(Project project) {
        myWorker = new JobsWorker(project);
        myProject = project;
    }

    public List<Pair<String, String>> load(PerforceJob job) throws VcsException {
        List<Pair<String, String>> result = new ArrayList<>();
        SimpleReference<VcsException> exception = SimpleReference.create();
        Runnable loader = () -> {
            try {
                result.addAll(myWorker.loadJob(job));
            }
            catch (VcsException e) {
                exception.set(e);
            }
        };
        if (!Application.get().isDispatchThread()) {
            loader.run();
        }
        else {
            ProgressManager.getInstance().runProcessWithProgressSynchronously(loader, PerforceLocalize.jobLoadingFields(), true, myProject);
        }
        VcsException e = exception.get();
        if (e != null) {
            throw e;
        }
        return result;
    }

    public void fillConnections(LocalChangeList list, Map<ConnectionKey, P4JobsLogicConn> connMap) {
        ProgressManager.getInstance().runProcessWithProgressSynchronously(
            () -> {
                ErrorReporter reporter = new ErrorReporter(PerforceLocalize.jobGettingRelated());

                Map<ConnectionKey, P4Connection> map = ConnectionSelector.getConnections(myProject, list);
                if (map.isEmpty()) {
                    return;
                }

                for (Map.Entry<ConnectionKey, P4Connection> entry : map.entrySet()) {
                    ConnectionKey key = entry.getKey();
                    P4Connection connection = entry.getValue();
                    try {
                        P4JobsLogicConn p4JobsLogicConn = connMap.get(key);
                        PerforceJobSpecification spec =
                            (p4JobsLogicConn == null) ? myWorker.getSpec(connection) : p4JobsLogicConn.getSpec();
                        connMap.put(key, new P4JobsLogicConn(connection, spec, null));
                    }
                    catch (VcsException e1) {
                        reporter.report(myProject, e1);
                    }
                }
            },
            PerforceLocalize.jobGettingSpecs(),
            false,
            myProject
        );
    }

    public void loadJobsForList(
        LocalChangeList list,
        Map<ConnectionKey, P4JobsLogicConn> connMap,
        Map<ConnectionKey, List<PerforceJob>> perforceJobs
    ) {
        Runnable loader = () -> {
            ErrorReporter reporter = new ErrorReporter(PerforceLocalize.jobGettingRelated());
            Map<ConnectionKey, P4Connection> map = ConnectionSelector.getConnections(myProject, list);
            if (map.isEmpty()) {
                return;
            }

            for (Map.Entry<ConnectionKey, P4Connection> entry : map.entrySet()) {
                ConnectionKey key = entry.getKey();
                P4Connection connection = entry.getValue();
                try {
                    List<String> jobNames = myWorker.getJobNames(list, connection, key);
                    P4JobsLogicConn p4JobsLogicConn = connMap.get(key);
                    PerforceJobSpecification spec = (p4JobsLogicConn == null) ? myWorker.getSpec(connection) : p4JobsLogicConn.getSpec();
                    connMap.put(key, new P4JobsLogicConn(connection, spec, null));
                    perforceJobs.put(
                        key,
                        jobNames.isEmpty()
                            ? Collections.emptyList()
                            : myWorker.getJobs(spec, new ByNamesConstraint(jobNames), connection, key)
                    );
                }
                catch (VcsException e1) {
                    reporter.report(myProject, e1);
                }
            }
        };
        if (Application.get().isDispatchThread()) {
            ProgressManager.getInstance().runProcessWithProgressSynchronously(
                loader,
                PerforceLocalize.jobLoadingJobsForChangelist(),
                false,
                myProject
            );
        }
        else {
            loader.run();
        }
    }
}

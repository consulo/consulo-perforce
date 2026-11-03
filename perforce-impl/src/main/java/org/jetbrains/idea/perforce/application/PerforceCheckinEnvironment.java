/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.perforce.application;

import consulo.application.AccessToken;
import consulo.application.progress.PerformInBackgroundOption;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.perforce.localize.PerforceLocalize;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.MultiMap;
import consulo.util.dataholder.Key;
import consulo.util.lang.function.PairConsumer;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.change.Change;
import consulo.versionControlSystem.change.ChangeList;
import consulo.versionControlSystem.change.ChangeListManager;
import consulo.versionControlSystem.change.CommitContext;
import consulo.versionControlSystem.change.LocalChangeList;
import consulo.versionControlSystem.checkin.CheckinEnvironment;
import consulo.versionControlSystem.checkin.CheckinProjectPanel;
import consulo.versionControlSystem.ui.RefreshableOnComponent;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.idea.perforce.operations.P4AddOperation;
import org.jetbrains.idea.perforce.operations.P4DeleteOperation;
import org.jetbrains.idea.perforce.operations.VcsOperation;
import org.jetbrains.idea.perforce.operations.VcsOperationLog;
import org.jetbrains.idea.perforce.perforce.P4File;
import org.jetbrains.idea.perforce.perforce.PerforceChange;
import org.jetbrains.idea.perforce.perforce.PerforceRunner;
import org.jetbrains.idea.perforce.perforce.PerforceSettings;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jetbrains.idea.perforce.perforce.jobs.PerforceCheckinComponent;
import org.jetbrains.idea.perforce.perforce.jobs.PerforceJob;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PerforceCheckinEnvironment implements CheckinEnvironment {
    private static final Logger LOG = Logger.getInstance(PerforceCheckinEnvironment.class);
    public static final Key<List<PerforceJob>> LINKED_JOBS_KEY = Key.create("perforce_linked_jobs");

    private final Project myProject;
    private final PerforceRunner myRunner;
    private final PerforceVcs myVcs;

    /**
     * The commit options panel does not receive the commit context of the commit it belongs to,
     * so the jobs panel stores the linked jobs into this context, which is consulted on commit.
     */
    private volatile @Nullable CommitContext myOptionsCommitContext;

    public PerforceCheckinEnvironment(Project project, PerforceVcs perforceVcs) {
        myProject = project;
        myRunner = PerforceRunner.getInstance(project);
        myVcs = perforceVcs;
    }

    @Override
    public @Nullable RefreshableOnComponent createAdditionalOptionsPanel(
        CheckinProjectPanel panel,
        PairConsumer<Object, Object> additionalDataConsumer
    ) {
        if (!PerforceSettings.getSettings(myProject).USE_PERFORCE_JOBS) {
            myOptionsCommitContext = null;
            return null;
        }
        CommitContext commitContext = new CommitContext();
        myOptionsCommitContext = commitContext;
        return new PerforceCheckinComponent(myProject, commitContext);
    }

    @Override
    public @Nullable String getDefaultMessageFor(FilePath[] filesToCheckin) {
        return null;
    }

    @Override
    public @Nullable String getHelpId() {
        return null;
    }

    @Override
    public LocalizeValue getCheckinOperationName() {
        return PerforceLocalize.operationNameSubmit();
    }

    @Override
    public List<VcsException> commit(List<Change> changes, String commitMessage, CommitContext commitContext, Set<String> feedback) {
        List<VcsException> vcsExceptions = new ArrayList<>();
        try (AccessToken ignored = myVcs.writeLockP4()) {
            List<SubmitJob> map = getSubmitJobs(changes);
            if (map.isEmpty()) {
                vcsExceptions.add(new VcsException(PerforceLocalize.exceptionTextNothingFoundToSubmit().get()));
            }
            else {
                for (SubmitJob job : map) {
                    long submittedRevision = job.submit(commitMessage, getLinkedJobs(commitContext));
                    if (submittedRevision > 0) {
                        feedback.add("Perforce revision #" + submittedRevision);
                    }
                }
            }

            LOG.info("updating opened files after commit");
        }
        catch (VcsException e) {
            vcsExceptions.add(e);
        }
        return vcsExceptions;
    }

    private @Nullable List<PerforceJob> getLinkedJobs(CommitContext commitContext) {
        List<PerforceJob> jobs = commitContext.getUserData(LINKED_JOBS_KEY);
        if (jobs != null) {
            return jobs;
        }
        CommitContext optionsCommitContext = myOptionsCommitContext;
        return optionsCommitContext == null ? null : optionsCommitContext.getUserData(LINKED_JOBS_KEY);
    }

    public List<SubmitJob> getSubmitJobs(Collection<? extends Change> changes) throws VcsException {
        SplitListIntoConnections<SubmitJob> splitter = new SplitListIntoConnections<>(myProject, SubmitJob::new);
        splitter.execute(changes);
        MultiMap<ConnectionKey, FilePath> filePaths = splitter.getPaths();
        Map<ConnectionKey, SubmitJob> byConnectionMap = splitter.getByConnectionMap();

        for (ConnectionKey key : filePaths.keySet()) {
            SubmitJob job = byConnectionMap.get(key);
            if (job != null) {
                job.addChanges(myRunner.opened(job.getConnection(), filePaths.get(key), false));
            }
        }
        return new ArrayList<>(byConnectionMap.values());
    }

    public class SubmitJob {
        private final P4Connection myConnection;
        private final List<PerforceChange> myChanges = new ArrayList<>();

        public SubmitJob(P4Connection connection) {
            myConnection = connection;
        }

        public void addChanges(Collection<PerforceChange> changes) {
            myChanges.addAll(changes);
        }

        public P4Connection getConnection() {
            return myConnection;
        }

        public long submit(String comment, @Nullable List<PerforceJob> p4jobs) throws VcsException {
            if (myChanges.isEmpty()) {
                return -1;
            }
            long changeListID = createSingleChangeListForConnection();
            long submittedRevision = myRunner.submitForConnection(myConnection, myChanges, changeListID, comment, p4jobs);
            if (changeListID == -1) {
                myVcs.clearDefaultAssociated();
            }
            return submittedRevision;
        }

        private long createSingleChangeListForConnection() throws VcsException {
            MultiMap<Long, PerforceChange> byListMap = new MultiMap<>();
            for (PerforceChange change : myChanges) {
                byListMap.putValue(change.getChangeList(), change);
            }

            if (byListMap.size() == 0) {
                return -1; //???
            }
            if (byListMap.size() == 1) {
                return byListMap.keySet().iterator().next();
            }

            for (Long number : byListMap.keySet()) {
                if (number == -1) {
                    continue;
                }
                myRunner.reopen(
                    myConnection,
                    -1,
                    ContainerUtil.map(byListMap.get(number), change -> P4File.escapeWildcards(change.getDepotPath()))
                );
                myRunner.deleteChangeList(myConnection, number, false, true, false);
            }
            return -1;
        }
    }

    @Override
    public List<VcsException> scheduleMissingFileForDeletion(List<FilePath> files) {
        ChangeListManager manager = ChangeListManager.getInstance(myProject);
        List<VcsOperation> operations = new ArrayList<>();
        for (FilePath file : files) {
            Change change = manager.getChange(file);
            LocalChangeList list = change != null ? manager.getChangeList(change) : manager.getDefaultChangeList();
            operations.add(new P4DeleteOperation(list == null ? manager.getDefaultListName() : list.getName(), file));
        }
        VcsOperationLog.getInstance(myProject).queueOperations(
            operations,
            PerforceLocalize.fileRemovingFiles(),
            PerformInBackgroundOption.ALWAYS_BACKGROUND
        );
        return Collections.emptyList();
    }

    @Override
    public List<VcsException> scheduleUnversionedFilesForAddition(List<VirtualFile> files) {
        String activeChangeList = ChangeListManager.getInstance(myProject).getDefaultChangeList().getName();
        List<VcsOperation> ops = new ArrayList<>();
        for (VirtualFile file : files) {
            ops.add(new P4AddOperation(activeChangeList, file));
        }
        VcsOperationLog.getInstance(myProject).queueOperations(
            ops,
            PerforceLocalize.progressTitleRunningPerforceCommands(),
            PerformInBackgroundOption.ALWAYS_BACKGROUND
        );
        return Collections.emptyList();
    }

    @Override
    public boolean keepChangeListAfterCommit(ChangeList changeList) {
        return false;
    }

    @Override
    public boolean isRefreshAfterCommitNeeded() {
        return true;
    }
}

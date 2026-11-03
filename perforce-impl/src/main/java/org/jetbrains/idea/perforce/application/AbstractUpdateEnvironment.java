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

import consulo.application.Application;
import consulo.application.progress.ProgressIndicator;
import consulo.application.util.LineTokenizer;
import consulo.component.ProcessCanceledException;
import consulo.perforce.localize.PerforceLocalize;
import consulo.project.Project;
import consulo.project.util.WaitForProgressToShow;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.MultiMap;
import consulo.util.lang.ref.Ref;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.history.VcsRevisionNumber;
import consulo.versionControlSystem.update.SequentialUpdatesContext;
import consulo.versionControlSystem.update.UpdateEnvironment;
import consulo.versionControlSystem.update.UpdateSession;
import consulo.versionControlSystem.update.UpdateSessionAdapter;
import consulo.versionControlSystem.update.UpdatedFiles;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.idea.perforce.merge.PerforceMergeProvider;
import org.jetbrains.idea.perforce.perforce.ExecResult;
import org.jetbrains.idea.perforce.perforce.P4File;
import org.jetbrains.idea.perforce.perforce.PerforceRunner;
import org.jetbrains.idea.perforce.perforce.PerforceSettings;
import org.jetbrains.idea.perforce.perforce.VcsConnectionProblem;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jetbrains.idea.perforce.perforce.connections.PerforceConnectionManager;
import org.jetbrains.idea.perforce.perforce.connections.PerforceConnectionManagerI;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public abstract class AbstractUpdateEnvironment implements UpdateEnvironment {
    protected final Project myProject;

    AbstractUpdateEnvironment(Project project) {
        myProject = project;
    }

    protected static void processOutput(
        String output,
        UpdatedFiles updatedFiles,
        Map<String, String> patternToGroupId,
        PerforceClient client
    ) throws VcsException {
        String[] lines = LineTokenizer.tokenize(output, false);
        for (String line : lines) {
            fillUpdateInformationFromTheLine(line, updatedFiles, patternToGroupId, client);
        }
    }

    private static void fillUpdateInformationFromTheLine(
        String line,
        UpdatedFiles updatedFiles,
        Map<String, String> patternToGroupId,
        PerforceClient client
    ) throws VcsException {
        for (Map.Entry<String, String> entry : patternToGroupId.entrySet()) {
            if (processLine(line, updatedFiles, entry.getKey(), entry.getValue(), client)) {
                return;
            }
        }
    }

    private static boolean processLine(
        String line,
        UpdatedFiles updatedFiles,
        String message,
        String fileGroupId,
        PerforceClient client
    ) throws VcsException {
        int messageStart = line.indexOf(message);
        if (messageStart < 0) {
            return false;
        }

        String depotFilePath = line.substring(0, messageStart).trim();
        File fileByDepotName = PerforceManager.getFileByDepotName(depotFilePath, client);
        String filePath = fileByDepotName != null ? fileByDepotName.getPath() : depotFilePath;

        int revNumPos = depotFilePath.indexOf('#');
        VcsRevisionNumber revision =
            revNumPos > 0 ? new PerforceOnlyRevisionNumber(Long.parseLong(depotFilePath.substring(revNumPos + 1))) : null;

        updatedFiles.getGroupById(fileGroupId).add(filePath, PerforceVcs.getKey(), revision);
        return true;
    }

    protected PerforceSettings getSettings() {
        return PerforceSettings.getSettings(myProject);
    }

    private void resolveAutomatically(P4File contentRoot) throws VcsException {
        PerforceRunner.getInstance(myProject).resolveAutomatically(contentRoot);
    }

    @Override
    public UpdateSession updateDirectories(
        FilePath[] contentRoots,
        UpdatedFiles updatedFiles,
        ProgressIndicator progressIndicator,
        Ref<SequentialUpdatesContext> context
    ) throws ProcessCanceledException {
        PerforceSettings settings = getSettings();
        if (!settings.ENABLED) {
            return new UpdateSessionAdapter(Collections.singletonList(new VcsException(PerforceLocalize.perforceIsOffline().get())), false);
        }
        List<VcsException> vcsExceptions = new ArrayList<>();

        try {
            MultiMap<P4Connection, FilePath> map = FileGrouper.distributePathsByConnection(Arrays.asList(contentRoots), myProject);

            PerforceManager perforceManager = PerforceManager.getInstance(myProject);
            for (P4Connection connection : map.keySet()) {
                if (isRevertUnchanged(settings)) {
                    List<String> paths = ContainerUtil.map(map.get(connection), path -> P4File.create(path).getRecursivePath());
                    try {
                        PerforceRunner.getInstance(myProject).revertUnchanged(connection, paths);
                    }
                    catch (VcsConnectionProblem e) {
                        vcsExceptions.add(e);
                        Application.get().invokeLater(() -> e.attemptQuickFix(true));
                        return new UpdateSessionAdapter(vcsExceptions, true);
                    }
                    catch (VcsException e) {
                        vcsExceptions.add(e);
                    }
                }

                try {
                    PerforceClient client = perforceManager.getClient(connection);
                    // todo: try to find common parent dirs to execute cmd
                    for (FilePath path : map.get(connection)) {
                        P4File p4Dir = P4File.create(path);
                        ExecResult execResult = performUpdate(p4Dir, settings);
                        processOutput(execResult.getStdout(), updatedFiles, getPatternToGroupId(), client);
                        VcsException[] updateExceptions = PerforceRunner.checkErrors(execResult, settings, connection);
                        if (updateExceptions.length > 0) {
                            Collections.addAll(vcsExceptions, updateExceptions);
                        }
                        else {
                            if (isTryToResolveAutomatically(settings)) {
                                resolveAutomatically(p4Dir);
                            }
                        }
                    }
                }
                catch (VcsConnectionProblem e) {
                    vcsExceptions.add(e);
                    Application.get().invokeLater(() -> e.attemptQuickFix(true));
                    return new UpdateSessionAdapter(vcsExceptions, true);
                }
                catch (VcsException e) {
                    vcsExceptions.add(e);
                }
            }

            handleResolveConflicts(map);
        }
        finally {
            PerforceSettings.getSettings(myProject).SYNC_FORCE = false;
        }
        return new UpdateSessionAdapter(vcsExceptions, false);
    }

    private void handleResolveConflicts(MultiMap<P4Connection, FilePath> map) {
        try {
            List<VirtualFile> filesToResolve = new LinkedList<>();
            for (P4Connection connection : map.keySet()) {
                for (FilePath root : map.get(connection)) {
                    VirtualFile file = root.getVirtualFile();
                    if (file != null) {
                        filesToResolve.addAll(
                            filterByServerVersion(PerforceRunner.getInstance(myProject).getResolvedWithConflicts(connection, file))
                        );
                    }
                }
            }
            if (!filesToResolve.isEmpty()) {
                WaitForProgressToShow.runOrInvokeAndWaitAboveProgress(
                    () -> new PerforceMergeProvider(myProject).showMergeDialog(filesToResolve)
                );
            }
        }
        catch (VcsException e) {
            //ignore
        }
    }

    private List<VirtualFile> filterByServerVersion(Collection<VirtualFile> allFilesToResolve) throws VcsException {
        PerforceConnectionManagerI connectionManager = PerforceConnectionManager.getInstance(myProject);
        List<VirtualFile> result = new ArrayList<>();
        for (VirtualFile virtualFile : allFilesToResolve) {
            if (PerforceSettings.getSettings(myProject).getServerVersion(connectionManager.getConnectionForFile(virtualFile)) >= 2004) {
                result.add(virtualFile);
            }
        }
        return result;
    }

    protected abstract boolean isTryToResolveAutomatically(PerforceSettings settings);

    protected abstract Map<String, String> getPatternToGroupId();

    protected abstract boolean isRevertUnchanged(PerforceSettings settings);

    protected abstract ExecResult performUpdate(P4File p4Dir, PerforceSettings settings) throws VcsException;
}

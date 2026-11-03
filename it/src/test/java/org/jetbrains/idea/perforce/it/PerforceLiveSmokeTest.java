/*
 * Copyright 2013-2026 consulo.io
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
package org.jetbrains.idea.perforce.it;

import consulo.it.HeadlessProjectExtension;
import consulo.it.HeadlessProjects;
import consulo.project.Project;
import consulo.versionControlSystem.ProjectLevelVcsManager;
import consulo.versionControlSystem.VcsDirectoryMapping;
import consulo.versionControlSystem.annotate.FileAnnotation;
import consulo.versionControlSystem.impl.internal.change.ChangeListManagerImpl;
import consulo.versionControlSystem.history.VcsHistorySession;
import consulo.versionControlSystem.util.VcsUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.idea.perforce.application.PerforceShelf;
import org.jetbrains.idea.perforce.application.PerforceVcs;
import org.jetbrains.idea.perforce.perforce.FStat;
import org.jetbrains.idea.perforce.perforce.P4File;
import org.jetbrains.idea.perforce.perforce.PerforceChange;
import org.jetbrains.idea.perforce.perforce.PerforceChangeCache;
import org.jetbrains.idea.perforce.perforce.PerforceChangeList;
import org.jetbrains.idea.perforce.perforce.PerforceRunner;
import org.jetbrains.idea.perforce.perforce.PerforceSettings;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jetbrains.idea.perforce.perforce.connections.PerforceConnectionManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the plugin's p4 layer (runner, connection manager, parsers, history and annotation providers) against a real
 * {@code p4d} in a headless project whose base directory is the client root.
 */
@ExtendWith(HeadlessProjectExtension.class)
public class PerforceLiveSmokeTest {
    @Test
    public void testRunnerAgainstLiveServer(HeadlessProjects projects) throws Exception {
        Path base = projects.newDirectory();
        Path clientRoot = Files.createDirectories(base.resolve("clientRoot"));
        try (P4TestServer server = P4TestServer.start(base.resolve("server"))) {
            server.createClient(clientRoot);
            Files.writeString(clientRoot.resolve("a.txt"), "line 1\nline 2\n");
            server.p4(clientRoot, null, "add", "a.txt");
            server.p4(clientRoot, null, "submit", "-d", "initial");
            server.p4(clientRoot, null, "edit", "a.txt");
            Files.writeString(clientRoot.resolve("a.txt"), "line 1\nline 2 changed\nline 3\n");
            server.p4(clientRoot, null, "submit", "-d", "second");

            Project project = projects.open(clientRoot);
            PerforceSettings settings = PerforceSettings.getSettings(project);
            settings.useP4CONFIG = false;
            settings.pathToExec = server.getP4().getPath();
            settings.port = server.getPort();
            settings.client = P4TestServer.CLIENT;
            settings.user = P4TestServer.USER;
            settings.ENABLED = true;
            ProjectLevelVcsManager.getInstance(project)
                .setDirectoryMappings(List.of(new VcsDirectoryMapping(clientRoot.toString().replace('\\', '/'), PerforceVcs.NAME)));
            HeadlessProjects.awaitIdle(project);

            VirtualFile file = Objects.requireNonNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(clientRoot.resolve("a.txt")));
            P4Connection connection = PerforceConnectionManager.getInstance(project).getConnectionForFile(file);
            assertNotNull(connection, "no connection for the client root");

            PerforceRunner runner = PerforceRunner.getInstance(project);
            Map<String, List<String>> info = runner.getInfo(connection);
            assertEquals(List.of(P4TestServer.CLIENT), info.get(PerforceRunner.CLIENT_NAME), info.toString());
            assertTrue(runner.getClients(connection).contains(P4TestServer.CLIENT));

            P4File p4File = P4File.create(file);
            FStat fstat = runner.fstat(p4File);
            assertEquals("//depot/a.txt", fstat.depotFile);
            assertEquals("2", fstat.haveRev);
            assertEquals(2, runner.haveRevision(p4File));

            List<PerforceChangeList> submitted = runner.getSubmittedChangeLists(connection, null, null, 10, false, List.of("//depot/..."));
            assertEquals(2, submitted.size());
            assertEquals("second", submitted.get(0).getComment().trim());

            VcsHistorySession session = Objects.requireNonNull(PerforceVcs.getInstance(project).getVcsHistoryProvider())
                .createSessionFor(VcsUtil.getFilePath(file));
            assertNotNull(session);
            assertEquals(2, session.getRevisionList().size());

            FileAnnotation annotation = Objects.requireNonNull(PerforceVcs.getInstance(project).getAnnotationProvider()).annotate(file);
            assertEquals(3, annotation.getLineCount());

            runner.edit(p4File);
            List<PerforceChange> opened = runner.opened(connection, List.of(VcsUtil.getFilePath(file)), true);
            assertEquals(1, opened.size());
            assertEquals("//depot/a.txt", opened.get(0).getDepotPath());

            runner.revert(p4File, false);
            assertTrue(runner.opened(connection, List.of(VcsUtil.getFilePath(file)), false).isEmpty());

            // shelve an edit into a new pending change list, revert it, and read the shelf back through describe -S
            long number = runner.createChangeList("shelve me", connection, null);
            runner.edit(p4File, number);
            Files.writeString(clientRoot.resolve("a.txt"), "shelved content\n");
            runner.shelve(connection, number, List.of(p4File.getEscapedPath()));
            runner.revert(p4File, false);

            List<PerforceChangeList> pending = runner.getPendingChangeLists(connection);
            assertEquals(List.of(number), pending.stream().map(PerforceChangeList::getNumber).toList());
            PerforceShelf shelf = new PerforceShelf(project);
            runner.fillChangeCache(connection, new PerforceChangeCache(project), shelf, pending);
            assertTrue(shelf.hasLocalChanges(connection.getConnectionKey(), number));

            runner.unshelve(connection, number, List.of(p4File.getEscapedPath()));
            assertEquals("shelved content\n", Files.readString(clientRoot.resolve("a.txt")));
            runner.deleteFromShelf(connection, number, List.of(p4File.getEscapedPath()));
            runner.revert(p4File, false);
            PerforceShelf emptyShelf = new PerforceShelf(project);
            runner.fillChangeCache(connection, new PerforceChangeCache(project), emptyShelf, runner.getPendingChangeLists(connection));
            assertFalse(emptyShelf.hasLocalChanges(connection.getConnectionKey(), number));

            // let the change list manager finish its refresh and notifications, then close before the server stops,
            // so background refreshes don't hit a dead port
            ChangeListManagerImpl.getInstanceImpl(project).waitUntilRefreshed();
            HeadlessProjects.awaitIdle(project);
            projects.close(project);
        }
    }
}

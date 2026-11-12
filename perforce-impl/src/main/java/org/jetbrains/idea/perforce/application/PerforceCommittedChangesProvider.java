/*
 * Copyright 2000-2006 JetBrains s.r.o.
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

import consulo.application.util.LineTokenizer;
import consulo.application.util.function.AsynchConsumer;
import consulo.application.util.registry.Registry;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.perforce.localize.PerforceLocalize;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.io.FileUtil;
import consulo.util.lang.Pair;
import consulo.versionControlSystem.CachingCommittedChangesProvider;
import consulo.versionControlSystem.ChangeListColumn;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.ProjectLevelVcsManager;
import consulo.versionControlSystem.RepositoryLocation;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.action.VcsContextFactory;
import consulo.versionControlSystem.change.Change;
import consulo.versionControlSystem.change.ChangesUtil;
import consulo.versionControlSystem.change.ContentRevision;
import consulo.versionControlSystem.change.FilePathsHelper;
import consulo.versionControlSystem.change.commited.DecoratorManager;
import consulo.versionControlSystem.change.commited.RepositoryLocationGroup;
import consulo.versionControlSystem.change.commited.VcsCommittedListsZipper;
import consulo.versionControlSystem.change.commited.VcsCommittedListsZipperAdapter;
import consulo.versionControlSystem.change.commited.VcsCommittedViewAuxiliary;
import consulo.versionControlSystem.history.VcsRevisionNumber;
import consulo.versionControlSystem.util.VcsUtil;
import consulo.versionControlSystem.versionBrowser.ChangeBrowserSettings;
import consulo.versionControlSystem.versionBrowser.ChangesBrowserSettingsEditor;
import consulo.versionControlSystem.versionBrowser.CommittedChangeList;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.idea.perforce.changesBrowser.PerforceChangeBrowserSettings;
import org.jetbrains.idea.perforce.changesBrowser.PerforceChangeBrowserSettingsService;
import org.jetbrains.idea.perforce.changesBrowser.PerforceOnlyDatesVersionFilterComponent;
import org.jetbrains.idea.perforce.changesBrowser.PerforceVersionFilterComponent;
import org.jetbrains.idea.perforce.perforce.ExecResult;
import org.jetbrains.idea.perforce.perforce.P4File;
import org.jetbrains.idea.perforce.perforce.P4Revision;
import org.jetbrains.idea.perforce.perforce.P4WhereResult;
import org.jetbrains.idea.perforce.perforce.PerforceChangeCache;
import org.jetbrains.idea.perforce.perforce.PerforceChangeList;
import org.jetbrains.idea.perforce.perforce.PerforceRunner;
import org.jetbrains.idea.perforce.perforce.PerforceSettings;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jetbrains.idea.perforce.perforce.connections.PerforceConnectionManager;
import org.jspecify.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class PerforceCommittedChangesProvider implements CachingCommittedChangesProvider<PerforceChangeList, PerforceChangeBrowserSettings> {
    private static final Logger LOG = Logger.getInstance(PerforceCommittedChangesProvider.class);

    private final Project myProject;
    private final PerforceRunner myRunner;
    private final MyZipper myZipper;
    private final ChangeListColumn[] myColumns = new ChangeListColumn[]{
        ChangeListColumn.NUMBER, ChangeListColumn.DATE, ChangeListColumn.NAME, new ClientColumn(), ChangeListColumn.DESCRIPTION
    };
    private static final String IS_OPENED_SIGNATURE = "is opened and not being changed";
    private static final String IS_OPENED_SIGNATURE2 = "- is opened for edit - not changed";
    private static final String MUST_BE_RESOLVED = "- must resolve";

    public PerforceCommittedChangesProvider(Project project) {
        myProject = project;
        myRunner = PerforceRunner.getInstance(myProject);
        myZipper = new MyZipper();
    }

    @Override
    public PerforceChangeBrowserSettings createDefaultSettings() {
        return PerforceChangeBrowserSettingsService.getInstance(myProject).getSettings();
    }

    @Override
    public VcsCommittedListsZipper getZipper() {
        return myZipper;
    }

    private static class MyGroupCreator implements VcsCommittedListsZipperAdapter.GroupCreator {
        @Override
        public Object createKey(RepositoryLocation location) {
            String url = ((PerforceRepositoryLocation) location).getLocation();
            int idx = url.indexOf("://");
            return (idx == -1) ? url : url.substring(0, idx);
        }

        @Override
        public RepositoryLocationGroup createGroup(Object key, Collection<RepositoryLocation> locations) {
            RepositoryLocationGroup group = new RepositoryLocationGroup(key.toString());
            for (RepositoryLocation location : locations) {
                group.add(location);
            }
            return group;
        }
    }

    private static final class MyZipper extends VcsCommittedListsZipperAdapter {
        private MyZipper() {
            super(new MyGroupCreator());
        }
    }

    @RequiredUIAccess
    @Override
    public ChangesBrowserSettingsEditor<PerforceChangeBrowserSettings> createFilterUI(boolean showDateFilter) {
        Collection<P4Connection> connections = PerforceSettings.getSettings(myProject).getAllConnections();
        if (connections.size() == 1) {
            return new PerforceVersionFilterComponent(myProject, connections.iterator().next(), showDateFilter);
        }
        return new PerforceOnlyDatesVersionFilterComponent();
    }

    @Override
    public RepositoryLocation getLocationFor(FilePath root) {
        P4Connection connection = PerforceSettings.getSettings(myProject).getConnectionForFile(root);
        assert connection != null : "Null connection for " + root;
        String serverAddress = PerforceManager.getInstance(myProject).getClient(connection).getDeclaredServerPort();
        String location = serverAddress + "://" + root.getPresentableUrl();
        return PerforceRepositoryLocation.create(root, location, myProject);
    }

    @Override
    public RepositoryLocation getLocationFor(FilePath root, String repositoryPath) {
        return getLocationFor(root);
    }

    @Override
    public void loadCommittedChanges(
        PerforceChangeBrowserSettings settings,
        RepositoryLocation location,
        int maxCount,
        AsynchConsumer<CommittedChangeList> consumer
    ) throws VcsException {
        try {
            List<PerforceChangeList> changeLists = new ArrayList<>();
            PerforceSettings p4Settings = PerforceSettings.getSettings(myProject);
            if (!p4Settings.ENABLED) {
                return;
            }
            String url = ((PerforceRepositoryLocation) location).getURL();

            String client = settings.getClientFilter();
            String user = settings.getUserFilter();
            // todo make asynchronous later; Perforce is fast, maybe it's not worth doing
            List<PerforceChangeList> changeListList = myRunner.getSubmittedChangeLists(
                client,
                user,
                P4File.create(new File(url)),
                settings,
                maxCount,
                p4Settings.SHOW_INTEGRATED_IN_COMMITTED_CHANGES
            );
            settings.filterChanges(changeLists);
            for (PerforceChangeList changeList : changeListList) {
                consumer.accept(changeList);
            }
        }
        finally {
            consumer.finished();
        }
    }

    @Override
    public List<PerforceChangeList> getCommittedChanges(PerforceChangeBrowserSettings settings, RepositoryLocation location, int maxCount)
        throws VcsException {
        PerforceSettings p4Settings = PerforceSettings.getSettings(myProject);
        if (!p4Settings.ENABLED) {
            throw new VcsException(PerforceLocalize.perforceIsOffline().get());
        }
        String url = ((PerforceRepositoryLocation) location).getURL();

        String client = settings.getClientFilter();
        String user = settings.getUserFilter();
        List<PerforceChangeList> changeLists = new ArrayList<>(myRunner.getSubmittedChangeLists(
            client,
            user,
            P4File.create(new File(url)),
            settings,
            maxCount,
            p4Settings.SHOW_INTEGRATED_IN_COMMITTED_CHANGES
        ));
        LOG.debug("Changelists from Perforce: " + changeLists.size());
        settings.filterChanges(changeLists);
        LOG.debug("Changelists after filtering: " + changeLists.size());
        return changeLists;
    }

    @Override
    public ChangeListColumn[] getColumns() {
        return myColumns;
    }

    @Override
    public @Nullable VcsCommittedViewAuxiliary createActions(DecoratorManager manager, RepositoryLocation location) {
        return null;
    }

    @Override
    public int getUnlimitedCountValue() {
        return 0;
    }

    @Override
    public @Nullable Pair<PerforceChangeList, FilePath> getOneList(VirtualFile file, VcsRevisionNumber number) throws VcsException {
        PerforceSettings p4Settings = PerforceSettings.getSettings(myProject);
        if (!p4Settings.ENABLED) {
            return null;
        }
        FilePath filePath = VcsUtil.getFilePath(file.getPath(), false);
        VirtualFile root;
        if (file.isInLocalFileSystem() || !file.isValid()) {
            filePath = ChangesUtil.getCommittedPath(myProject, filePath);
            VirtualFile validParent = ChangesUtil.findValidParentAccurately(filePath);
            if (validParent == null) {
                return null;
            }
            root = ProjectLevelVcsManager.getInstance(myProject).getVcsRootFor(validParent);
        }
        else {
            root = ProjectLevelVcsManager.getInstance(myProject).getVcsRootFor(file);
        }
        if (root == null) {
            return null;
        }
        long changelistNumber;
        try {
            changelistNumber = Long.parseLong(number.asString());
        }
        catch (NumberFormatException e) {
            throw new VcsException(e);
        }
        P4Connection connection = PerforceConnectionManager.getInstance(myProject).getConnectionForFile(root);
        if (connection == null) {
            throw new VcsException(PerforceLocalize.errorCanNotFindConnectionFor(root.getPath()).get());
        }

        P4File rootP4File = P4File.create(root);
        ChangeBrowserSettings settings = new ChangeBrowserSettings();
        settings.USE_CHANGE_BEFORE_FILTER = true;
        settings.CHANGE_BEFORE = String.valueOf(changelistNumber);
        settings.USE_CHANGE_AFTER_FILTER = true;
        settings.CHANGE_AFTER = String.valueOf(changelistNumber);
        List<PerforceChangeList> changeListList = myRunner.getSubmittedChangeLists(
            null,
            null,
            rootP4File,
            settings,
            1,
            p4Settings.SHOW_INTEGRATED_IN_COMMITTED_CHANGES
        );
        if (changeListList.isEmpty()) {
            List<PerforceChangeList> longerList = myRunner.getSubmittedChangeLists(null, null, rootP4File, settings, 0, true);
            for (PerforceChangeList list : longerList) {
                if (list.getNumber() == changelistNumber) {
                    return correctNameIfNeeded(list, filePath, connection);
                }
            }
            return null;
        }

        return correctNameIfNeeded(changeListList.get(0), filePath, connection);
    }

    private Pair<PerforceChangeList, FilePath> correctNameIfNeeded(
        PerforceChangeList changeList,
        FilePath filePath,
        P4Connection connection
    ) throws VcsException {
        for (Change change : changeList.getChanges()) {
            ContentRevision afterRevision = change.getAfterRevision();
            if (afterRevision != null && FileUtil.pathsEqual(filePath.getPath(), afterRevision.getFile().getPath())) {
                return Pair.create(changeList, filePath);
            }
        }

        P4Revision[] revisions = myRunner.filelog(P4File.create(filePath), true);
        for (P4Revision revision : revisions) {
            if (revision.getChangeNumber() == changeList.getNumber()) {
                P4WhereResult where = myRunner.where(revision.getDepotPath(), connection);
                return Pair.create(changeList, VcsUtil.getFilePath(where.getLocal(), false));
            }
        }
        return Pair.create(changeList, filePath);
    }

    @Override
    public RepositoryLocation getForNonLocal(VirtualFile file) {
        return PerforceRepositoryLocation.create(file, myProject);
    }

    @Override
    public boolean supportsIncomingChanges() {
        return true;
    }

    @Override
    public int getFormatVersion() {
        return 2;
    }

    @Override
    public void writeChangeList(DataOutput stream, PerforceChangeList list) throws IOException {
        list.writeToStream(stream);
    }

    @Override
    public PerforceChangeList readChangeList(RepositoryLocation location, DataInput stream) throws IOException {
        P4Connection connection;
        PerforceClient perforceClient;
        try {
            PerforceRepositoryLocation perforceRepositoryLocation = (PerforceRepositoryLocation) location;
            connection = perforceRepositoryLocation.getConnection();
            perforceClient = perforceRepositoryLocation.getClient();
        }
        catch (VcsException e) {
            throw new IOException(e);
        }
        return new PerforceChangeList(myProject, stream, connection, perforceClient, new PerforceChangeCache(myProject));
    }

    @Override
    public boolean isMaxCountSupported() {
        return true;
    }

    @Override
    public @Nullable Collection<FilePath> getIncomingFiles(RepositoryLocation location) throws VcsException {
        if (!Registry.is("p4.use.p4.sync.for.incoming.files", false)) {
            return null;
        }

        if (!PerforceSettings.getSettings(myProject).ENABLED) {
            throw new VcsException(PerforceLocalize.perforceIsOffline().get());
        }

        PerforceRepositoryLocation repLocation = (PerforceRepositoryLocation) location;
        P4File file = P4File.create(new File(repLocation.getURL()));
        ExecResult result = myRunner.previewSync(file);
        if (result.getExitCode() != 0 || !result.getStderr().isEmpty()) {
            if (result.getStderr().contains(PerforceRunner.FILES_UP_TO_DATE)) {
                return Collections.emptyList();
            }
            if (result.getStderr().contains(PerforceRunner.NO_SUCH_FILE_MESSAGE)) { // the root is not in clientspec or ignored
                return Collections.emptyList();
            }
            throw new VcsException(PerforceLocalize.errorRefreshingIncomingChanges(result.getExitCode(), result.getStderr()).get());
        }
        P4Connection connection = PerforceConnectionManager.getInstance(myProject).getConnectionForFile(file);
        if (connection == null) {
            throw new VcsException(PerforceLocalize.errorInvalidPerforceSettingsFor0(file.getLocalPath()).get());
        }
        PerforceClient client = PerforceManager.getInstance(myProject).getClient(connection);
        PerforceManager perforceManager = PerforceManager.getInstance(myProject);
        String clientRoot = perforceManager.getClientRoot(connection);
        if (clientRoot == null) {
            throw new VcsException(PerforceLocalize.errorInvalidPerforceSettingsFor0(file.getLocalPath()).get());
        }
        P4WhereResult p4WhereResult = myRunner.whereDir(file, connection);
        String depotRoot = p4WhereResult.getDepot();

        clientRoot = FilePathsHelper.convertPath(clientRoot);
        depotRoot = FilePathsHelper.convertPath(depotRoot);

        List<FilePath> files = new ArrayList<>();
        String[] lines = LineTokenizer.tokenize(result.getStdout(), false);
        for (String line : lines) {
            String lineConverted = FilePathsHelper.convertPath(line);
            int pos = lineConverted.indexOf(clientRoot);
            if (pos >= 0) {
                File localFile = new File(line.substring(pos));
                LOG.debug("Incoming file: " + line.substring(pos));
                files.add(VcsContextFactory.getInstance().createFilePathOn(localFile));
            }
            else if (line.contains(IS_OPENED_SIGNATURE)) {
                pos = line.indexOf(" - ");
                if (pos >= 0) {
                    String depotPath = line.substring(0, pos);
                    File localPath = PerforceManager.getFileByDepotName(depotPath, client);
                    if (localPath != null) {
                        files.add(VcsContextFactory.getInstance().createFilePathOn(localPath));
                    }
                }
            }
            else if (line.contains(IS_OPENED_SIGNATURE2)) {
                pos = line.indexOf(IS_OPENED_SIGNATURE2);
                if (pos >= 0) {
                    String depotPath = line.substring(0, pos);
                    File localPath = PerforceManager.getFileByDepotName(depotPath, client);
                    if (localPath != null) {
                        files.add(VcsContextFactory.getInstance().createFilePathOn(localPath));
                    }
                }
            }
            else if (line.contains(MUST_BE_RESOLVED)) {
                pos = lineConverted.indexOf(depotRoot);
                int pathEnd = line.indexOf(MUST_BE_RESOLVED);
                if (pos >= 0) {
                    String depotPath = line.substring(pos, pathEnd).trim();
                    File localPath = PerforceManager.getFileByDepotName(depotPath, client);
                    if (localPath != null) {
                        files.add(VcsContextFactory.getInstance().createFilePathOn(localPath));
                    }
                }
            }
            else {
                LOG.info("Unknown line in incoming files: " + line);
            }
        }
        return files;
    }

    @Override
    public boolean refreshCacheByNumber() {
        return true;
    }

    @Override
    public @Nullable String getChangelistTitle() {
        return PerforceLocalize.changesBrowserChangelistTerm().get();
    }

    @Override
    public boolean isChangeLocallyAvailable(
        FilePath filePath,
        @Nullable VcsRevisionNumber localRevision,
        VcsRevisionNumber changeRevision,
        PerforceChangeList changeList
    ) {
        return localRevision != null && localRevision.compareTo(changeRevision) >= 0;
    }

    @Override
    public boolean refreshIncomingWithCommitted() {
        return false;
    }

    private static class ClientColumn extends ChangeListColumn<PerforceChangeList> {
        @Override
        public LocalizeValue getTitle() {
            return PerforceLocalize.changesBrowserClientColumnName();
        }

        @Override
        public Object getValue(PerforceChangeList changeList) {
            return changeList.getClient();
        }

        @Override
        public Comparator<PerforceChangeList> getComparator() {
            return Comparator.comparing(PerforceChangeList::getClient);
        }
    }
}

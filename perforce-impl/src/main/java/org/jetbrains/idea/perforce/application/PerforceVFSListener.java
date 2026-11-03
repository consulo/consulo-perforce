// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).

package org.jetbrains.idea.perforce.application;

import consulo.application.Application;
import consulo.application.progress.PerformInBackgroundOption;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.progress.Task;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.perforce.localize.PerforceLocalize;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.MultiMap;
import consulo.versionControlSystem.AbstractVcsHelper;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.VcsVFSListener;
import consulo.versionControlSystem.change.ChangeListManager;
import consulo.versionControlSystem.util.VcsUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.event.BulkFileListener;
import consulo.virtualFileSystem.event.VFileContentChangeEvent;
import consulo.virtualFileSystem.event.VFileCreateEvent;
import consulo.virtualFileSystem.event.VFileEvent;
import consulo.virtualFileSystem.status.FileStatus;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import org.jetbrains.idea.perforce.operations.P4AddOperation;
import org.jetbrains.idea.perforce.operations.P4CopyOperation;
import org.jetbrains.idea.perforce.operations.P4EditOperation;
import org.jetbrains.idea.perforce.operations.P4MoveRenameOperation;
import org.jetbrains.idea.perforce.operations.VcsOperation;
import org.jetbrains.idea.perforce.operations.VcsOperationLog;
import org.jetbrains.idea.perforce.perforce.LastUnchangedContentTracker;
import org.jetbrains.idea.perforce.perforce.P4File;
import org.jetbrains.idea.perforce.perforce.PerforceCachingContentRevision;
import org.jetbrains.idea.perforce.perforce.PerforceSettings;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jetbrains.idea.perforce.perforce.connections.PerforceConnectionManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public final class PerforceVFSListener extends VcsVFSListener {
    private static final Logger LOG = Logger.getInstance(PerforceVFSListener.class);

    private PerforceVFSListener(Project project) {
        super(project, PerforceVcs.getInstance(project));
    }

    public static PerforceVFSListener createInstance(Project project) {
        PerforceVFSListener listener = new PerforceVFSListener(project);
        listener.installListeners();
        return listener;
    }

    private void installListeners() {
        myProject.getApplication().getMessageBus().connect(this).subscribe(BulkFileListener.class, new BulkFileListener() {
            @Override
            public void after(List<? extends VFileEvent> events) {
                for (VFileEvent event : events) {
                    if (event instanceof VFileCreateEvent) {
                        if (!(event.getFileSystem() instanceof LocalFileSystem)) {
                            continue;
                        }
                        FilePath filePath = VcsUtil.getFilePath(event.getPath());
                        if (!isUnderMyVcs(filePath)) {
                            continue; //call event.getFile() only for the file path under Perforce VCS
                        }

                        VirtualFile file = event.getFile();
                        if (file != null) {
                            LastUnchangedContentTracker.markTouched(file);
                        }
                    }
                }
            }
        });
    }

    @Override
    protected boolean filterOutByStatus(FileStatus status) {
        return status == FileStatus.IGNORED;
    }

    @Override
    protected boolean shouldIgnoreDeletion(FileStatus status) {
        return status == FileStatus.UNKNOWN;
    }

    @Override
    protected boolean isEventAccepted(VFileEvent event) {
        return !(event.getRequestor() instanceof PerforceOfflineRollbackEnvironment) && super.isEventAccepted(event);
    }

    @Override
    protected void executeAdd(List<VirtualFile> addedFiles, Map<VirtualFile, VirtualFile> copiedFiles) {
        if (Application.get().isUnitTestMode()) {
            performAddingWithConfirmation(addedFiles, copiedFiles);
            return;
        }
        if (!PerforceSettings.getSettings(myProject).ENABLED) {
            performAddingWithConfirmation(addedFiles, copiedFiles);
            return;
        }

        Project project = myProject;
        ProgressManager.getInstance().run(new Task.Backgroundable(project, PerforceLocalize.progressTitleCheckingForIgnoredFiles(), false) {
            @Override
            public void run(ProgressIndicator pi) {
                MultiMap<P4Connection, VirtualFile> map = FileGrouper.distributeFilesByConnection(addedFiles, project);
                for (P4Connection connection : map.keySet()) {
                    try {
                        addedFiles.removeAll(PerforceUnversionedTracker.getFilesOutsideClientSpec(project, connection, map.get(connection)));
                    }
                    catch (VcsException e) {
                        AbstractVcsHelper.getInstance(project).showError(e, PerforceLocalize.perforceError());
                    }
                }
                performAddingWithConfirmation(addedFiles, copiedFiles);
            }
        });
    }

    @Override
    protected void performAdding(Collection<VirtualFile> addedFiles, Map<VirtualFile, VirtualFile> copyFromMap) {
        List<VcsOperation> operations = createOperations(addedFiles, copyFromMap);
        VcsOperationLog.getInstance(myProject).queueOperations(
            operations,
            PerforceLocalize.progressTitleRunningPerforceCommands(),
            PerformInBackgroundOption.ALWAYS_BACKGROUND
        );
    }

    private List<VcsOperation> createOperations(Collection<VirtualFile> addedFiles, Map<VirtualFile, VirtualFile> copyFromMap) {
        String chList = myChangeListManager.getDefaultChangeList().getName();

        List<VcsOperation> operations = new ArrayList<>();
        Predicate<VirtualFile> fileProcessor = file -> {
            if (!file.isDirectory()) {
                VirtualFile copyFrom = copyFromMap.get(file);
                operations.add(copyFrom != null ? new P4CopyOperation(chList, file, copyFrom) : new P4AddOperation(chList, file));
            }
            return true;
        };
        for (VirtualFile file : addedFiles) {
            VirtualFileUtil.processFileRecursivelyWithoutIgnored(file, fileProcessor);
        }
        return operations;
    }

    @Override
    protected Function<Object, LocalizeValue> getSingleFileAddPromptGenerator() {
        return PerforceLocalize::confirmationTextAddFiles;
    }

    @Override
    protected LocalizeValue getSingleFileAddTitleValue() {
        return PerforceLocalize.confirmationTitleAddFiles();
    }

    @Override
    protected LocalizeValue getAddTitleValue() {
        return PerforceLocalize.addSelectFiles();
    }

    @Override
    protected void performDeletion(List<FilePath> filesToDelete) {
        PerforceVcs.getInstance(myProject).getCheckinEnvironment().scheduleMissingFileForDeletion(filesToDelete);
    }

    @Override
    protected Function<Object, LocalizeValue> getSingleFileDeletePromptGenerator() {
        return PerforceLocalize::confirmationTextRemoveFiles;
    }

    @Override
    protected LocalizeValue getSingleFileDeleteTitleValue() {
        return PerforceLocalize.confirmationTitleRemoveFiles();
    }

    @Override
    protected LocalizeValue getDeleteTitleValue() {
        return PerforceLocalize.deleteSelectFiles();
    }

    @Override
    protected void processMovedFile(VirtualFile file, String newParentPath, String newName) {
        LOG.debug("processMovedFile " + file + " newParentPath=" + newParentPath + " newName=" + newName);
        updateLastUnchangedContent(file, myChangeListManager);
        PerforceCachingContentRevision.removeCachedContent(file);
        P4File.invalidateFstat(file);
    }

    @Override
    protected void performMoveRename(List<MovedFileInfo> movedFiles) {
        List<VcsOperation> operations = new ArrayList<>();
        for (MovedFileInfo movedFile : movedFiles) {
            operations.add(new P4MoveRenameOperation(
                ChangeListManager.getInstance(myProject).getDefaultChangeList().getName(),
                movedFile.myOldPath,
                movedFile.myNewPath
            ));
        }

        VcsOperationLog.getInstance(myProject).queueOperations(
            operations,
            PerforceLocalize.progressTitleRunningPerforceCommands(),
            PerformInBackgroundOption.ALWAYS_BACKGROUND
        );
    }

    @Override
    protected boolean processBeforeContentsChange() {
        return true;
    }

    @Override
    protected void beforeContentsChange(List<VFileContentChangeEvent> events) {
        List<VirtualFile> files = new ArrayList<>(events.size());
        for (VFileContentChangeEvent event : events) {
            VirtualFile file = event.getFile();
            updateLastUnchangedContent(file, myChangeListManager);

            if (!event.isFromRefresh() && myChangeListManager.getStatus(file) == FileStatus.NOT_CHANGED) {
                P4Connection connection = PerforceConnectionManager.getInstance(myProject).getConnectionForFile(file);
                if (connection != null && PerforceChangeProvider.isAllWriteWorkspace(connection, myProject)) {
                    files.add(file);
                }
            }
        }

        if (!files.isEmpty()) {
            asyncEdit(files);
        }
    }

    private void asyncEdit(List<VirtualFile> files) {
        PerforceVcs vcs = PerforceVcs.getInstance(myProject);
        Set<VirtualFile> exisingAsyncEditedFiles = vcs.getAsyncEditedFiles();
        VirtualFile[] notEditedFiles = ContainerUtil.filter(files, f -> !exisingAsyncEditedFiles.contains(f)).toArray(VirtualFile[]::new);
        if (notEditedFiles.length == 0) {
            return;
        }

        vcs.startAsyncEdit(notEditedFiles);

        List<P4EditOperation> operations = new ArrayList<>(notEditedFiles.length);
        for (VirtualFile file : notEditedFiles) {
            P4EditOperation op = new P4EditOperation(myChangeListManager.getDefaultListName(), file);
            op.setSuppressErrors(true);
            operations.add(op);
        }
        VcsOperationLog.getInstance(myProject).queueOperations(
            operations,
            PerforceLocalize.progressTitleRunningPerforceCommands(),
            PerformInBackgroundOption.ALWAYS_BACKGROUND,
            () -> vcs.refreshFiles(notEditedFiles)
        );
    }

    public static void updateLastUnchangedContent(VirtualFile file, ChangeListManager changeListManager) {
        FileStatus status = changeListManager.getStatus(file);
        if (status == FileStatus.NOT_CHANGED) {
            LastUnchangedContentTracker.updateLastUnchangedContent(file);
        }
    }
}

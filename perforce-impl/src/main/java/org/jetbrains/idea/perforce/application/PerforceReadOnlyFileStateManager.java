// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.application;

import consulo.application.Application;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.project.ui.wm.IdeFrame;
import consulo.project.ui.wm.event.ApplicationActivationListener;
import consulo.versionControlSystem.AbstractVcs;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.ProjectLevelVcsManager;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.change.Change;
import consulo.versionControlSystem.change.ChangeListManager;
import consulo.versionControlSystem.change.ChangeListManagerGate;
import consulo.versionControlSystem.change.ChangelistBuilder;
import consulo.versionControlSystem.change.ContentRevision;
import consulo.versionControlSystem.change.LocalChangeList;
import consulo.versionControlSystem.change.VcsDirtyScope;
import consulo.versionControlSystem.util.VcsUtil;
import consulo.virtualFileSystem.VFileProperty;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import consulo.virtualFileSystem.event.AsyncFileListener;
import consulo.virtualFileSystem.event.VFileContentChangeEvent;
import consulo.virtualFileSystem.event.VFileCopyEvent;
import consulo.virtualFileSystem.event.VFileCreateEvent;
import consulo.virtualFileSystem.event.VFileDeleteEvent;
import consulo.virtualFileSystem.event.VFileEvent;
import consulo.virtualFileSystem.event.VFileMoveEvent;
import consulo.virtualFileSystem.event.VFilePropertyChangeEvent;
import consulo.virtualFileSystem.status.FileStatus;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PerforceReadOnlyFileStateManager {
  private static final Logger LOG = Logger.getInstance(PerforceReadOnlyFileStateManager.class);

  private final Project myProject;
  private final PerforceDirtyFilesHandler myDirtyFilesHandler;
  private final ProjectLevelVcsManager myVcsManager;
  private final Object myLock = new Object();
  private final ApplicationActivationListener myFrameStateListener = new ApplicationActivationListener() {
    @Override
    public void applicationDeactivated(IdeFrame ideFrame) {
      processFocusLost();
    }
  };
  private final Set<FilePath> myPreviousAddedSnapshot = new HashSet<>();

  private final Map<VirtualFile, Set<VirtualFile>> myWritableFiles = new HashMap<>();

  private volatile boolean myHasLostFocus = false;

  public PerforceReadOnlyFileStateManager(Project project, PerforceDirtyFilesHandler dirtyFilesHandler) {
    myProject = project;
    myDirtyFilesHandler = dirtyFilesHandler;
    myVcsManager = ProjectLevelVcsManager.getInstance(project);
  }

  public void activate(Disposable parentDisposable) {
    Disposer.register(parentDisposable, this::deactivate);

    VirtualFileManager.getInstance().addAsyncFileListener(new MyVfsListener(), parentDisposable);
    Application.get().getMessageBus().connect(parentDisposable).subscribe(ApplicationActivationListener.class, myFrameStateListener);
  }

  private void deactivate() {
    myHasLostFocus = true;
  }

  public void addWritableFiles(VirtualFile root, Collection<VirtualFile> filesCollection, boolean withIgnored) {
    addWritableFilesFromRoot(filesCollection, root, withIgnored, null);
  }

  public void addWritableFiles(VirtualFile root, Collection<VirtualFile> filesCollection, boolean withIgnored, VcsDirtyScope scopeFilter) {
    addWritableFilesFromRoot(filesCollection, root, withIgnored, scopeFilter);
  }

  private void addWritableFilesFromRoot(Collection<VirtualFile> filesCollection,
                                        VirtualFile root,
                                        boolean withIgnored,
                                        @Nullable VcsDirtyScope scopeFilter) {
    Set<VirtualFile> writableFilesUnderRoot = getWritableFilesUnderRoot(root);
    ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);

    for (VirtualFile file : writableFilesUnderRoot) {
      if (shouldIncludeFile(file, withIgnored, scopeFilter, changeListManager)) {
        filesCollection.add(file);
      }
    }
  }

  private Set<VirtualFile> getWritableFilesUnderRoot(VirtualFile root) {
    synchronized (myWritableFiles) {
      // do not collect init files under lock
      Set<VirtualFile> files = myWritableFiles.get(root);
      if (files != null) {
        return new HashSet<>(files);
      }
    }

    return initializeWritableFiles(root);
  }

  private static boolean shouldIncludeFile(VirtualFile file,
                                           boolean withIgnored,
                                           @Nullable VcsDirtyScope scopeFilter,
                                           ChangeListManager changeListManager) {
    if (scopeFilter != null && !scopeFilter.belongsTo(VcsUtil.getFilePath(file))) {
      return false;
    }
    return withIgnored || !changeListManager.isIgnoredFile(file);
  }

  private Set<VirtualFile> initializeWritableFiles(VirtualFile root) {
    Set<VirtualFile> newWritableFiles = new HashSet<>();
    myVcsManager.iterateVfUnderVcsRoot(root, virtualFile -> {
      addFileIfWritable(newWritableFiles, virtualFile);
      return true;
    });
    synchronized (myWritableFiles) {
      Set<VirtualFile> files = myWritableFiles.get(root);
      if (files != null) {
        return new HashSet<>(files);
      }
      myWritableFiles.put(root, new HashSet<>(newWritableFiles));
      return newWritableFiles;
    }
  }

  private void updateWritableFiles(@Nullable VirtualFile root, boolean isWritable, VirtualFile file) {
    synchronized (myWritableFiles) {
      Set<VirtualFile> writableFiles = myWritableFiles.get(root);
      if (writableFiles != null) {
        if (isWritable) {
          writableFiles.add(file);
        }
        else {
          writableFiles.remove(file);
        }
      }
    }
  }

  public void getChanges(VcsDirtyScope dirtyScope,
                         ChangelistBuilder builder,
                         ProgressIndicator progress,
                         ChangeListManagerGate addGate) throws VcsException {
    Set<FilePath> newAdded = getAddedFilesInCurrentChangesView(addGate);
    synchronized (myLock) {
      progress.checkCanceled();
      recheckPreviouslyAddedFiles(newAdded);
      recheckWhatUnversionedRefreshNeeded(dirtyScope);
    }

    Set<String> missingFiles = myDirtyFilesHandler.scanAndGetMissingFiles(addGate, progress);

    for (String path : missingFiles) {
      FilePath filePath = VcsUtil.getFilePath(path, false);
      if (FileStatus.DELETED != addGate.getStatus(filePath)) {
        builder.processLocallyDeletedFile(filePath);
      }
    }
  }

  private Set<FilePath> getAddedFilesInCurrentChangesView(ChangeListManagerGate addGate) {
    Set<FilePath> result = new HashSet<>();
    for (LocalChangeList list : addGate.getListsCopy()) {
      for (Change change : list.getChanges()) {
        ContentRevision afterRevision = change.getAfterRevision();
        if (FileStatus.ADDED == change.getFileStatus() && afterRevision != null) {
          FilePath file = afterRevision.getFile();
          if (fileIsUnderP4Root(file)) {
            result.add(file);
          }
        }
      }
    }
    return result;
  }

  private void recheckWhatUnversionedRefreshNeeded(VcsDirtyScope dirtyScope) {
    if (myHasLostFocus && dirtyScope.wasEveryThingDirty()) {
      LOG.info("--- recheck missing");
      myHasLostFocus = false;
      myDirtyFilesHandler.rescanIfProblems();
    }
  }

  private void recheckPreviouslyAddedFiles(Set<FilePath> newAdded) {
    Set<FilePath> copy = new HashSet<>(myPreviousAddedSnapshot);
    copy.removeAll(newAdded);
    myPreviousAddedSnapshot.clear();
    myPreviousAddedSnapshot.addAll(newAdded);
    if (!copy.isEmpty()) {
      myDirtyFilesHandler.reportRecheck(copy);
    }
  }

  public void processFocusLost() {
    myHasLostFocus = true;
  }

  public void discardUnversioned() {
    synchronized (myWritableFiles) {
      myWritableFiles.clear();
    }
    myDirtyFilesHandler.scheduleTotalRescan();
  }

  private class MyVfsListener implements AsyncFileListener {
    @Override
    public @Nullable ChangeApplier prepareChange(List<? extends VFileEvent> events) {
      List<VfsAction> beforeActions = new ArrayList<>();
      List<VfsAction> afterActions = new ArrayList<>();

      for (VFileEvent event : events) {
        ProgressManager.checkCanceled();
        if (event instanceof VFilePropertyChangeEvent propertyEvent) {
          if (VirtualFile.PROP_WRITABLE.equals(propertyEvent.getPropertyName())) {
            if (!(propertyEvent.getNewValue() instanceof Boolean isWritable)) {
              continue;
            }
            FilePath path = getPathUnderP4Root(propertyEvent.getFile());
            if (path != null) {
              VirtualFile root = myVcsManager.getVcsRootFor(path);
              afterActions.add(new WritableChange(path, root, isWritable, propertyEvent.getFile()));
            }
          }
        }
        else if (event instanceof VFileContentChangeEvent contentEvent) {
          VirtualFile file = contentEvent.getFile();
          if (!file.isWritable()) {
            FilePath path = getPathUnderP4Root(file);
            if (path != null) {
              afterActions.add(new ReportRecheck(path));
            }
          }
        }
        else if (event instanceof VFileCreateEvent createEvent) {
          FilePath newPath = VcsUtil.getFilePath(createEvent.getPath(), createEvent.isDirectory());
          if (fileIsUnderP4Root(newPath)) {
            afterActions.add(new ReportRecheck(newPath));
          }
        }
        else if (event instanceof VFileMoveEvent moveEvent) {
          boolean isDirectory = moveEvent.getFile().isDirectory();
          FilePath newPath = VcsUtil.getFilePath(moveEvent.getNewPath(), isDirectory);
          FilePath oldPath = VcsUtil.getFilePath(moveEvent.getOldPath(), isDirectory);
          if (fileIsUnderP4Root(newPath)) {
            afterActions.add(new ReportRecheck(newPath));
          }
          if (fileIsUnderP4Root(oldPath)) {
            beforeActions.add(new ReportDelete(oldPath));
          }
        }
        else if (event instanceof VFileCopyEvent copyEvent) {
          FilePath copiedPath = VcsUtil.getFilePath(copyEvent.getPath(), copyEvent.getFile().isDirectory());
          if (fileIsUnderP4Root(copiedPath)) {
            afterActions.add(new ReportRecheck(copiedPath));
          }
        }
        else if (event instanceof VFileDeleteEvent deleteEvent) {
          FilePath path = getPathUnderP4Root(deleteEvent.getFile());
          if (path != null) {
            beforeActions.add(new ReportDelete(path));
          }
        }
      }

      if (beforeActions.isEmpty() && afterActions.isEmpty()) {
        return null;
      }

      return new ChangeApplier() {
        @Override
        public void beforeVfsChange() {
          for (VfsAction action : beforeActions) {
            action.process(PerforceReadOnlyFileStateManager.this);
          }
        }

        @Override
        public void afterVfsChange() {
          for (VfsAction action : afterActions) {
            action.process(PerforceReadOnlyFileStateManager.this);
          }
        }
      };
    }

    private @Nullable FilePath getPathUnderP4Root(VirtualFile file) {
      FilePath path = VcsUtil.getFilePath(file);
      return fileIsUnderP4Root(path) ? path : null;
    }
  }

  private sealed interface VfsAction permits WritableChange, ReportRecheck, ReportDelete {
    void process(PerforceReadOnlyFileStateManager manager);
  }

  private record WritableChange(FilePath path, @Nullable VirtualFile root, boolean isWritable, VirtualFile file) implements VfsAction {
    @Override
    public void process(PerforceReadOnlyFileStateManager manager) {
      manager.myDirtyFilesHandler.reportRecheck(path);
      manager.updateWritableFiles(root, isWritable, file);
    }
  }

  private record ReportRecheck(FilePath path) implements VfsAction {
    @Override
    public void process(PerforceReadOnlyFileStateManager manager) {
      manager.myDirtyFilesHandler.reportRecheck(path);
    }
  }

  private record ReportDelete(FilePath path) implements VfsAction {
    @Override
    public void process(PerforceReadOnlyFileStateManager manager) {
      manager.myDirtyFilesHandler.reportDelete(path);
    }
  }

  private boolean fileIsUnderP4Root(FilePath path) {
    if (myProject.isDisposed()) {
      return false;
    }

    if (ChangeListManager.getInstance(myProject).isIgnoredFile(path)) {
      return false;
    }

    AbstractVcs vcs = myVcsManager.getVcsFor(path);
    return vcs != null && vcs.getKeyInstanceMethod().equals(PerforceVcs.getKey());
  }

  private static void addFileIfWritable(Set<VirtualFile> files, VirtualFile vf) {
    Application.get().runReadAction(() -> {
      if (vf.isValid() && !vf.isDirectory() && vf.isWritable() && !vf.is(VFileProperty.SYMLINK)) {
        files.add(vf);
      }
    });
  }
}

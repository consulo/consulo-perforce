// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.application;

import org.jetbrains.idea.perforce.perforce.LastUnchangedContentTracker;
import consulo.application.progress.ProgressIndicator;
import consulo.project.Project;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.action.VcsContextFactory;
import consulo.versionControlSystem.change.Change;
import consulo.versionControlSystem.change.ChangeListManager;
import consulo.versionControlSystem.change.ChangeListManagerGate;
import consulo.versionControlSystem.change.ChangeProvider;
import consulo.versionControlSystem.change.ChangelistBuilder;
import consulo.versionControlSystem.change.ChangesUtil;
import consulo.versionControlSystem.change.ContentRevision;
import consulo.versionControlSystem.change.CurrentContentRevision;
import consulo.versionControlSystem.change.VcsDirtyScope;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.idea.perforce.operations.VcsOperation;
import org.jetbrains.idea.perforce.operations.VcsOperationLog;
import org.jetbrains.idea.perforce.perforce.PerforceCachingContentRevision;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class PerforceOfflineChangeProvider implements ChangeProvider {
  private final Project myProject;

  public PerforceOfflineChangeProvider(Project project) {
    myProject = project;
  }

  @Override
  public void getChanges(VcsDirtyScope dirtyScope, ChangelistBuilder builder, ProgressIndicator progress,
                         ChangeListManagerGate addGate) throws VcsException {
    ChangeListManager clm = ChangeListManager.getInstance(dirtyScope.getProject());

    Set<VirtualFile> writable = PerforceChangeProvider.collectWritableFiles(
      PerforceVcs.getInstance(myProject).getOnlineChangeProvider().getReadOnlyFileStateManager(),
      dirtyScope, true);

    Iterator<VirtualFile> iterator = writable.iterator();
    while (iterator.hasNext()) {
      VirtualFile file = iterator.next();
      if (clm.isIgnoredFile(file)) {
        builder.processIgnoredFile(file);
        iterator.remove();
      }
    }

    VcsOperationLog opLog = VcsOperationLog.getInstance(myProject);
    Map<String, @Nullable String> reopenedPaths = opLog.getReopenedPaths();
    List<LastSuccessfulUpdateTracker.PersistentChangeList> changeLists =
      LastSuccessfulUpdateTracker.getInstance(myProject).getChangeLists();
    if (changeLists != null) {
      for (LastSuccessfulUpdateTracker.PersistentChangeList changeList : changeLists) {
        for (LastSuccessfulUpdateTracker.ChangedFile file : changeList.files) {
          String changeListName = changeList.name;
          if (file.beforePath != null && reopenedPaths.containsKey(file.beforePath)) {
            changeListName = reopenedPaths.get(file.beforePath);
          }
          else if (file.afterPath != null && reopenedPaths.containsKey(file.afterPath)) {
            changeListName = reopenedPaths.get(file.afterPath);
          }
          if (changeListName == null) {
            continue;
          }

          FilePath beforePath = createFilePath(file.beforePath);
          FilePath afterPath = createFilePath(file.afterPath);
          if (isInScope(dirtyScope, beforePath, afterPath)) {
            ContentRevision beforeRevision = null;
            ContentRevision afterRevision = null;
            if (beforePath != null) {
              beforeRevision = PerforceCachingContentRevision.createOffline(myProject, beforePath,
                                                                            afterPath != null ? afterPath : beforePath);
            }
            if (afterPath != null) {
              afterRevision = CurrentContentRevision.create(afterPath);
              writable.remove(afterPath.getVirtualFile());
            }
            builder.processChangeInList(new Change(beforeRevision, afterRevision), changeListName, PerforceVcs.getKey());
          }
        }
      }
    }

    List<VcsOperation> list = opLog.getPendingOperations();
    for (VcsOperation op : list) {
      Change c = op.getChange(myProject, addGate);
      if (c != null) {
        FilePath afterPath = ChangesUtil.getAfterPath(c);
        if (isInScope(dirtyScope, ChangesUtil.getBeforePath(c), afterPath)) {
          if (afterPath != null) {
            writable.remove(afterPath.getVirtualFile());
          }
          builder.processChangeInList(c, op.getChangeList(), PerforceVcs.getKey());
        }
      }
    }

    for (VirtualFile file : writable) {
      if (LastUnchangedContentTracker.hasSavedContent(file)) {
        builder.processModifiedWithoutCheckout(file);
      }
      else {
        builder.processUnversionedFile(file);
      }
    }
  }

  private static boolean isInScope(VcsDirtyScope dirtyScope, @Nullable FilePath beforePath, @Nullable FilePath afterPath) {
    return (beforePath != null && dirtyScope.belongsTo(beforePath)) || (afterPath != null && dirtyScope.belongsTo(afterPath));
  }

  private static @Nullable FilePath createFilePath(@Nullable String beforePath) {
    if (beforePath == null) {
      return null;
    }
    return VcsContextFactory.getInstance().createFilePathOn(new File(beforePath), false);
  }

  @Override
  public boolean isModifiedDocumentTrackingRequired() {
    return false;
  }

  @Override
  public void doCleanup(List<VirtualFile> files) {
  }
}
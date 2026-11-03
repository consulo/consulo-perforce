// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.application;

import consulo.application.progress.ProgressIndicator;
import consulo.application.util.function.ThrowableComputable;
import consulo.component.ProcessCanceledException;
import consulo.component.messagebus.MessageBusConnection;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.ProjectLevelVcsManager;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.VcsMappingListener;
import consulo.versionControlSystem.change.ChangeListManagerGate;
import consulo.versionControlSystem.change.VcsDirtyScopeManager;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.status.FileStatus;
import org.jetbrains.idea.perforce.perforce.PerforceOfflineModeExitedListener;
import org.jetbrains.idea.perforce.perforce.PerforceSettings;
import org.jetbrains.idea.perforce.perforce.connections.P4EnvHelper;
import org.jetbrains.idea.perforce.perforce.connections.P4EnvListener;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class PerforceDirtyFilesHandler {
  private static final Logger LOG = Logger.getInstance(PerforceDirtyFilesHandler.class);
  private static final int FILES_THRESHOLD = 200;

  private final Project myProject;
  private final PerforceUnversionedTracker myUnversionedTracker;

  private final Set<FilePath> myDirtyFiles = new HashSet<>();
  private final Object myScannerLock = new Object();
  private final VcsDirtyScopeManager myDirtyScopeManager;

  private boolean myTotalRescanThresholdPassed = true;

  private volatile boolean myIsActive = false;

  private volatile boolean myPreviousRescanProblem = false;

  public PerforceDirtyFilesHandler(Project project, PerforceUnversionedTracker unversionedTracker) {
    myProject = project;
    myUnversionedTracker = unversionedTracker;
    myDirtyScopeManager = VcsDirtyScopeManager.getInstance(project);
    myDirtyScopeManager.markEverythingDirty();
  }

  public void activate(Disposable parentDisposable) {
    Disposer.register(parentDisposable, this::deactivate);

    MessageBusConnection connection = myProject.getMessageBus().connect(parentDisposable);
    connection.subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, (VcsMappingListener)this::cancelAndRescheduleTotalRescan);
    connection.subscribe(PerforceSettings.OFFLINE_MODE_EXITED, (PerforceOfflineModeExitedListener)this::cancelAndRescheduleTotalRescan);
    connection.subscribe(P4EnvHelper.P4_ENV_CHANGED, (P4EnvListener)this::cancelAndRescheduleTotalRescan);
  }

  private void deactivate() {
    myIsActive = false;
  }

  private void cancelAndRescheduleTotalRescan() {
    myIsActive = false;
    scheduleTotalRescan();
  }

  public void rescanIfProblems() {
    if (myPreviousRescanProblem) {
      scheduleTotalRescan();
    }
  }

  public void scheduleTotalRescan() {
    LOG.debug("totalRescan scheduled");
    synchronized (myScannerLock) {
      myTotalRescanThresholdPassed = true;
      myDirtyFiles.clear();
      myDirtyScopeManager.markEverythingDirty();
    }
  }

  public Set<String> scanAndGetMissingFiles(ChangeListManagerGate addGate, ProgressIndicator progress) throws VcsException {
    ThrowableComputable<UnversionedScopeScanner.ScanResult, VcsException> scanner = createScanner();

    progress.checkCanceled();
    UnversionedScopeScanner.ScanResult result = rescan(scanner);
    progress.checkCanceled();

    myUnversionedTracker.markUnknown(result.allLocalFiles);
    List<VirtualFile> localOnly = result.localOnly.stream().filter(file -> !isKnownToPerforce(addGate, file)).collect(Collectors.toList());
    myUnversionedTracker.markUnversioned(localOnly);

    myUnversionedTracker.scheduleUpdate();
    return result.missingFiles;
  }

  private static boolean isKnownToPerforce(ChangeListManagerGate addGate, VirtualFile file) {
    FileStatus status = addGate.getStatus(file);
    if (LOG.isDebugEnabled()) {
      LOG.debug("status " + status + " for " + file);
    }
    return status != null;
  }

  private ThrowableComputable<UnversionedScopeScanner.ScanResult, VcsException> createScanner() {
    UnversionedScopeScanner scanner = new UnversionedScopeScanner(myProject) {
      @Override
      protected void checkCanceled() {
        if (!myIsActive) {
          throw new ProcessCanceledException();
        }
      }
    };
    synchronized (myScannerLock) {
      if (myTotalRescanThresholdPassed) {
        myTotalRescanThresholdPassed = false;
        return () -> scanner.doRescan(UnversionedScopeScanner.createEverythingDirtyScope(myProject), true);
      }
      else {
        Set<FilePath> dirtyFiles = new HashSet<>(myDirtyFiles);
        myDirtyFiles.clear();
        return () -> scanner.doRescan(dirtyFiles, false);
      }
    }
  }

  private UnversionedScopeScanner.ScanResult rescan(ThrowableComputable<UnversionedScopeScanner.ScanResult, VcsException> scanner)
    throws VcsException {
    myIsActive = true;
    myPreviousRescanProblem = false;
    try {
      return scanner.compute();
    }
    catch (VcsException e) {
      myPreviousRescanProblem = true;
      throw e;
    }
  }

  public void reportRecheck(FilePath path) {
    myUnversionedTracker.markUnknown(path);
    if (addDirtyFile(path)) {
      myDirtyScopeManager.fileDirty(path);
    }
  }

  public void reportDelete(FilePath path) {
    if (addDirtyFile(path)) {
      myDirtyScopeManager.fileDirty(path);
    }
  }

  public void reportRecheck(Set<FilePath> targets) {
    for (FilePath target : targets) {
      if (!addDirtyFile(target)) {
        return;
      }
    }
    myDirtyScopeManager.filePathsDirty(targets, null);
  }

  private boolean addDirtyFile(FilePath holder) {
    synchronized (myScannerLock) {
      if (myTotalRescanThresholdPassed) {
        return false;
      }
      LOG.debug("addDirtyFile: " + holder);
      myDirtyFiles.add(holder);
      if (myDirtyFiles.size() > FILES_THRESHOLD) {
        scheduleTotalRescan();
        return false;
      }
      return true;
    }
  }
}

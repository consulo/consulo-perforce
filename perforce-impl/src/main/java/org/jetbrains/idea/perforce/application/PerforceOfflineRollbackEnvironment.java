// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.application;

import org.jetbrains.idea.perforce.perforce.LastUnchangedContentTracker;
import consulo.application.WriteAction;
import consulo.logging.Logger;
import consulo.perforce.localize.PerforceLocalize;
import consulo.project.Project;
import consulo.util.io.FileUtil;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.change.Change;
import consulo.versionControlSystem.change.ContentRevision;
import consulo.versionControlSystem.change.VcsDirtyScopeManager;
import consulo.versionControlSystem.rollback.DefaultRollbackEnvironment;
import consulo.versionControlSystem.rollback.RollbackProgressListener;
import consulo.virtualFileSystem.StandardFileSystems;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import org.jetbrains.idea.perforce.operations.P4RevertOperation;
import org.jetbrains.idea.perforce.operations.VcsOperationLog;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class PerforceOfflineRollbackEnvironment extends DefaultRollbackEnvironment {
  private static final Logger LOG = Logger.getInstance(PerforceOfflineRollbackEnvironment.class);

  private final Project myProject;

  public PerforceOfflineRollbackEnvironment(Project project) {
    myProject = project;
  }

  @Override
  public String getRollbackOperationName() {
    return PerforceLocalize.operationNameRevert().get();
  }

  @Override
  public void rollbackChanges(List<Change> changes, List<VcsException> vcsExceptions, RollbackProgressListener listener) {
    List<VcsException> exceptions = Collections.synchronizedList(vcsExceptions);

    List<Runnable> actions = new ArrayList<>();

    for (Change c : changes) {
      ContentRevision beforeRevision = c.getBeforeRevision();
      if (beforeRevision != null) {
        try {
          ContentRevision afterRevision = c.getAfterRevision();
          boolean isRenameOrMove;
          VirtualFile file;
          if (afterRevision != null && !afterRevision.getFile().equals(beforeRevision.getFile())) {
            file = afterRevision.getFile().getVirtualFile();
            isRenameOrMove = true;
          }
          else {
            file = beforeRevision.getFile().getVirtualFile();
            isRenameOrMove = false;
          }
          if (LOG.isDebugEnabled()) {
            LOG.debug("before rollback " + c + "; " + beforeRevision);
            if (file != null) {
              LOG.debug("has last content " + (LastUnchangedContentTracker.getLastUnchangedContent(file) != null));
            }
          }
          String content = beforeRevision.getContent();
          if (content != null && file != null) {
            actions.add(() -> {
              if (myProject.isDisposed()) {
                return;
              }
              listener.accept(c);
              VcsOperationLog.getInstance(myProject).addToLog(new P4RevertOperation(c));
              try {
                file.setWritable(true);
                VirtualFileUtil.saveText(file, content);
                if (isRenameOrMove && afterRevision != null) {
                  handleMoveRename(beforeRevision, file, afterRevision);
                }
                file.setWritable(false);
              }
              catch (IOException e) {
                exceptions.add(new VcsException(e));
              }
            });
          }
          else {
            LOG.debug("contentUnavailable: content " + (content != null) + ", file " + file);
            exceptions.add(contentUnavailable(beforeRevision.getFile().getPath()));
          }
        }
        catch (VcsException ex) {
          exceptions.add(ex);
        }
      }
      else {
        VcsOperationLog.getInstance(myProject).addToLog(new P4RevertOperation(c));
      }
    }

    executeActions(listener, exceptions, actions);
  }

  private static void executeActions(RollbackProgressListener listener, List<? super VcsException> exceptions, List<? extends Runnable> actions) {
    if (!exceptions.isEmpty()) {
      return;
    }

    listener.determinate();
    for (Runnable action : actions) {
      WriteAction.runAndWait(action::run);
    }
  }

  private static VcsException contentUnavailable(String file) {
    return new VcsException(PerforceLocalize.errorCannotRevertFileOriginalContentIsNotAvailableOffline(file));
  }

  private void handleMoveRename(ContentRevision beforeRevision, VirtualFile file, ContentRevision afterRevision) throws IOException {
    String oldName = beforeRevision.getFile().getName();
    FilePath oldParentPath = beforeRevision.getFile().getParentPath();
    VirtualFile newParent = file.getParent();
    if (oldParentPath != null && newParent != null) {
      if (!FileUtil.toSystemIndependentName(oldParentPath.getPath()).equals(newParent.getPath())) {
        File oldParentFile = new File(oldParentPath.getPath());
        //noinspection ResultOfMethodCallIgnored
        oldParentFile.mkdirs();
        VirtualFile oldParentVFile = StandardFileSystems.local().refreshAndFindFileByPath(oldParentFile.getAbsolutePath());
        if (oldParentVFile != null) {
          file.move(this, oldParentVFile);
        }
      }
    }
    if (!file.getName().equals(oldName)) {
      file.rename(this, oldName);
    }
    VcsDirtyScopeManager.getInstance(myProject).fileDirty(beforeRevision.getFile());
    VcsDirtyScopeManager.getInstance(myProject).fileDirty(afterRevision.getFile());
  }

  @Override
  public void rollbackMissingFileDeletion(List<FilePath> files, List<VcsException> exceptions, RollbackProgressListener listener) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void rollbackModifiedWithoutCheckout(List<VirtualFile> files,
                                              List<VcsException> vcsExceptions,
                                              RollbackProgressListener listener) {
    List<VcsException> exceptions = Collections.synchronizedList(vcsExceptions);

    List<Runnable> actions = new ArrayList<>();

    for (VirtualFile file : files) {
      byte[] content = LastUnchangedContentTracker.getLastUnchangedContent(file);
      if (content == null) {
        exceptions.add(contentUnavailable(file.getPath()));
      } else {
        actions.add(() ->{
            if (myProject.isDisposed()) {
              return;
            }
            LOG.debug("rollbackModifiedWithoutCheckout file = " + file);
            listener.accept(file);
            try {
              file.setWritable(true);
              file.setBinaryContent(content);
              file.setWritable(false);
              VcsDirtyScopeManager.getInstance(myProject).fileDirty(file);
            }
            catch (IOException e) {
              exceptions.add(new VcsException(e));
            }
        });
      }
    }

    executeActions(listener, exceptions, actions);
  }
}
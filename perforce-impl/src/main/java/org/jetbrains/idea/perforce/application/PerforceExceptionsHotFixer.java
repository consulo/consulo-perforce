// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.application;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.progress.Task;
import consulo.component.ProcessCanceledException;
import consulo.perforce.localize.PerforceLocalize;
import consulo.project.Project;
import consulo.ui.ex.errorTreeView.ErrorTreeElementKind;
import consulo.ui.ex.errorTreeView.HotfixData;
import consulo.ui.ex.errorTreeView.HotfixGate;
import consulo.ui.ex.errorTreeView.MutableErrorTreeView;
import consulo.ui.ex.errorTreeView.SimpleErrorData;
import consulo.util.io.FileUtil;
import consulo.versionControlSystem.ActionType;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.VcsExceptionsHotFixer;
import consulo.versionControlSystem.change.VcsDirtyScopeManager;
import consulo.versionControlSystem.util.VcsUtil;
import consulo.virtualFileSystem.RefreshQueue;
import consulo.virtualFileSystem.StandardFileSystems;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileSystem;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.idea.perforce.perforce.FStat;
import org.jetbrains.idea.perforce.perforce.P4File;
import org.jetbrains.idea.perforce.perforce.PerforceRunner;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
@Singleton
public final class PerforceExceptionsHotFixer implements VcsExceptionsHotFixer {

  private final MyListChecker myUpdateChecker;

  @Inject
  public PerforceExceptionsHotFixer(Project project) {
    List<MyChecker> myCheckers = Collections.singletonList(new MyClobberWriteableChecker(project));
    myUpdateChecker = new MyListChecker(myCheckers);
  }

  @Override
  public @Nullable Map<HotfixData, List<VcsException>> groupExceptions(ActionType type, List<VcsException> exceptions) {
    if (ActionType.update.equals(type)) {
      return myUpdateChecker.process(exceptions);
    }
    return null;
  }

  private static List<VcsException> getOrCreate(Map<HotfixData, List<VcsException>> map, HotfixData data) {
    return map.computeIfAbsent(data, k -> new ArrayList<>());
  }

  private static final class MyListChecker {
    private final List<MyChecker> myCheckers;

    private MyListChecker(List<MyChecker> checkers) {
      myCheckers = checkers;
    }

    public Map<HotfixData, List<VcsException>> process(List<VcsException> list) {
      List<VcsException> aDefault = null;
      Map<HotfixData, List<VcsException>> result = new HashMap<>();
      for (VcsException exception : list) {
        boolean found = false;
        for (MyChecker checker : myCheckers) {
          if (checker.check(exception)) {
            List<VcsException> excList = getOrCreate(result, checker.getKey());
            excList.add(checker.convert(exception));
            found = true;
            break;
          }
        }
        if (! found) {
          if (aDefault == null) {
            aDefault = new ArrayList<>();
          }
          aDefault.add(exception);
        }
      }
      if (aDefault != null) {
        result.put(null, aDefault);
      }

      return result;
    }
  }

  private abstract static class MyChecker {
    private final HotfixData myKey;

    MyChecker(HotfixData key) {
      myKey = key;
    }

    protected abstract boolean check(VcsException exc);

    public VcsException convert(VcsException e) {
      return e;
    }

    public HotfixData getKey() {
      return myKey;
    }
  }

  private static class MyClobberWriteableHotfix implements Consumer<HotfixGate> {
    private final Project myProject;

    MyClobberWriteableHotfix(Project project) {
      myProject = project;
    }

    @Override
    public void accept(HotfixGate hotfixGate) {
      ProgressManager.getInstance().run(new Task.Backgroundable(myProject, PerforceLocalize.activityOpeningFileForEdit(), true) {
        @Override
        public void run(ProgressIndicator indicator) {
          String name = hotfixGate.getGroupName();
          List<Object> childData = hotfixGate.getView().getGroupChildrenData(name);

          List<SimpleErrorData> processed = new ArrayList<>();
          List<VirtualFile> processedFiles = new ArrayList<>();
          List<SimpleErrorData> failed = new ArrayList<>();
          try {
            edit(childData, processed, processedFiles, failed);
          }
          catch (ProcessCanceledException e) {
            for (Object child : childData) {
              if (child instanceof VirtualFile vf) {
                failed.add(createErrorData(vf, PerforceLocalize.hotfixOpCanceled().get()));
              }
            }
          }

          MutableErrorTreeView view = hotfixGate.getView();
          view.removeGroup(name);

          if (! processed.isEmpty()) {
            view.addFixedHotfixGroup(PerforceLocalize.hotfixCannotClobberFiles().get(), processed);
            refreshVfs(processedFiles);
          }
          if (! failed.isEmpty()) {
            view.addHotfixGroup(new HotfixData(MyHotfixes.FIX_CLOBBER_WRITEABLES, PerforceLocalize.hotfixCannotClobberFiles().get(),
                                 ' ' + PerforceLocalize.hotfixOpenForEdit().get(), MyClobberWriteableHotfix.this), failed);
          }
        }

        @Override
        public void onCancel() {
          onSuccess();
        }

        @Override
        public void onSuccess() {
          hotfixGate.getView().reload();
        }
      });
    }

    private void refreshVfs(List<VirtualFile> processedFiles) {
      RefreshQueue.getInstance().refresh(true, false, () -> {
        VcsDirtyScopeManager.getInstance(myProject).filesDirty(processedFiles, null);
      }, processedFiles);
    }

    private void edit(List<Object> childData, List<SimpleErrorData> processed, List<VirtualFile> processedFiles,
                      List<SimpleErrorData> failed) {
      PerforceRunner runner = PerforceRunner.getInstance(myProject);
      ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();

      for (Object child : childData) {
        if (child instanceof VirtualFile vf) {
          if (indicator != null) {
            indicator.checkCanceled();
          }
          P4File p4File = P4File.create(VcsUtil.getFilePath(vf));
          try {
            FStat p4FStat = p4File.getFstat(myProject, true);

            if (FStat.STATUS_ON_SERVER_AND_LOCAL != p4FStat.status) {
              failed.add(createErrorData(vf, PerforceLocalize.hotfixSkippedStatusUnknown().get()));
            }
            else if (FStat.LOCAL_CHECKED_IN != p4FStat.local) {
              failed.add(createErrorData(vf, PerforceLocalize.hotfixSkippedNotChecked().get()));
            } else {
              String complaint = PerforceVcs.getFileNameComplaint(p4File);
              if (complaint != null) {
                failed.add(createErrorData(vf, PerforceLocalize.messageTextFilenameNonAcceptable(complaint).get()));
              } else {
                runner.edit(p4File);
                processed.add(createFixedData(vf));
                processedFiles.add(vf);
              }
            }
          }
          catch (VcsException e) {
            failed.add(createErrorData(vf, String.valueOf(e.getMessage())));
          }
        }
      }
    }

    private static SimpleErrorData createFixedData(VirtualFile vf) {
      return new SimpleErrorData(ErrorTreeElementKind.ERROR, new String[] {vf.getPath()}, vf);
    }

    private static SimpleErrorData createErrorData(VirtualFile vf, String comment) {
      // todo message format
      return new SimpleErrorData(ErrorTreeElementKind.ERROR, new String[] {vf.getPath() + " (fix failed: " + comment + ")"}, vf);
    }
  }

    private static final class MyClobberWriteableChecker extends MyChecker {
      private static final String ourClobberWriteable = "Can't clobber writable file";
      private final Project myProject;

      private MyClobberWriteableChecker(Project project) {
      super(new HotfixData(MyHotfixes.FIX_CLOBBER_WRITEABLES, PerforceLocalize.hotfixCannotClobberFiles().get(),
                           ' ' + PerforceLocalize.hotfixOpenForEdit().get(), new MyClobberWriteableHotfix(project)));
        myProject = project;
      }

      @Override
      public VcsException convert(VcsException e) {
        VirtualFile vf = e.getVirtualFile();
        if (vf == null) {
          return e;
        }
        VcsException newE = new VcsException(vf.getPresentableUrl());
        newE.setVirtualFile(vf);
        return newE;
      }

      @Override
      protected boolean check(VcsException exc) {
      String[] messages = exc.getMessages();
      if (messages.length > 0 && messages[0].startsWith(ourClobberWriteable)) {

        String filePathCandidate = messages[0].substring(ourClobberWriteable.length());
        filePathCandidate = PerforceManager.getInstance(myProject).convertP4ParsedPath(null, filePathCandidate);
        filePathCandidate = FileUtil.toSystemDependentName(filePathCandidate);
        VirtualFileSystem lfs = StandardFileSystems.local();
        File ioFile = new File(filePathCandidate);
        VirtualFile vf = lfs.findFileByPath(ioFile.getAbsolutePath());
        if (vf == null) {
          vf = lfs.refreshAndFindFileByPath(ioFile.getAbsolutePath());
        }
        if (vf != null) {
          exc.setVirtualFile(vf);
          return true;
        }
      }
        return false;
      }
  }

  private interface MyHotfixes {
    String FIX_CLOBBER_WRITEABLES = "FIX_CLOBBER_WRITEABLES";
  }
}

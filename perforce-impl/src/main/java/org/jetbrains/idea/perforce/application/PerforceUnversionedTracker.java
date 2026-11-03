// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.application;

import consulo.application.Application;
import com.google.common.base.Stopwatch;
import consulo.application.util.registry.Registry;
import consulo.component.util.ComparableObject;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.logging.Logger;
import consulo.perforce.localize.PerforceLocalize;
import consulo.project.Project;
import consulo.ui.ex.awt.util.Alarm;
import consulo.ui.ex.awt.util.MergingUpdateQueue;
import consulo.ui.ex.awt.util.Update;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.JBIterable;
import consulo.util.collection.MultiMap;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.change.ChangeListListener;
import consulo.versionControlSystem.change.VcsIgnoreManager;
import consulo.versionControlSystem.change.VcsManagedFilesHolderListener;
import consulo.versionControlSystem.util.VcsUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import org.jetbrains.idea.perforce.ServerVersion;
import org.jetbrains.idea.perforce.perforce.ExecResult;
import org.jetbrains.idea.perforce.perforce.PerforceRunner;
import org.jetbrains.idea.perforce.perforce.PerforceSettings;
import org.jetbrains.idea.perforce.perforce.View;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PerforceUnversionedTracker {
  private static final Logger LOG = Logger.getInstance(PerforceUnversionedTracker.class);

  /**
   * Whether ignored files are processed at all (the "vcs.process.ignored" option, enabled by default).
   */
  private static final boolean PROCESS_IGNORED = true;

  private final Object LOCK = new Object();

  private final Set<FilePath> myUnversionedFiles = new HashSet<>();
  private final Set<FilePath> myIgnoredFiles = new HashSet<>();
  private final Set<VirtualFile> myDirtyLocalFiles = new HashSet<>();

  private final Project myProject;

  private final MergingUpdateQueue myQueue;
  private boolean myInUpdate;
  private @Nullable Disposable myParentDisposable;

  public PerforceUnversionedTracker(Project project) {
    myProject = project;
    myQueue = new MergingUpdateQueue("PerforceUnversionedTracker", 500, true, null, project, null, Alarm.ThreadToUse.POOLED_THREAD);
  }

  public void activate(Disposable parentDisposable) {
    Disposer.register(parentDisposable, this::deactivate);
    myParentDisposable = parentDisposable;
  }

  public void deactivate() {
    synchronized (LOCK) {
      myUnversionedFiles.clear();
      myIgnoredFiles.clear();
      myDirtyLocalFiles.clear();
    }
  }

  public boolean isLocalOnly(VirtualFile file) {
    synchronized (LOCK) {
      if (myDirtyLocalFiles.contains(file)) {
        return true;
      }
    }

    FilePath path = VcsUtil.getFilePath(file);
    return isUnversioned(path) || isIgnored(path);
  }

  public boolean isUnversioned(FilePath file) {
    synchronized (LOCK) {
      return myUnversionedFiles.contains(file);
    }
  }

  public boolean isIgnored(FilePath file) {
    if (isPotentiallyIgnoredFile(file)) {
      return true;
    }
    synchronized (LOCK) {
      return myIgnoredFiles.contains(file);
    }
  }

  public Collection<FilePath> getIgnoredFiles() {
    synchronized (LOCK) {
      return new ArrayList<>(myIgnoredFiles);
    }
  }

  public Collection<FilePath> getUnversionedFiles() {
    synchronized (LOCK) {
      return new ArrayList<>(myUnversionedFiles);
    }
  }


  public boolean isInUpdateMode() {
    synchronized (LOCK) {
      return myInUpdate;
    }
  }

  public void scheduleUpdate() {
    synchronized (LOCK) {
      if (myDirtyLocalFiles.isEmpty()) {
        return;
      }
      myInUpdate = true;
    }
    myProject.getMessageBus().syncPublisher(VcsManagedFilesHolderListener.class).updatingModeChanged();
    if (Application.get().isUnitTestMode()) {
      update();
    }
    else {
      Disposable parentDisposable = myParentDisposable;
      myQueue.queue(new Update(new ComparableObject.Impl(this, "update")) {
        @Override
        public boolean isDisposed() {
          return parentDisposable != null && Disposer.isDisposed(parentDisposable);
        }

        @Override
        public void run() {
          update();
        }
      });
    }
  }

  private void update() {
    MultiMap<P4Connection, VirtualFile> map;
    Stopwatch sw = Stopwatch.createStarted();
    List<VirtualFile> dirtyFiles;
    synchronized (LOCK) {
      LOG.debug("update started for " + myDirtyLocalFiles.size() + " files");
      if (myDirtyLocalFiles.isEmpty()) {
        myInUpdate = false;
        return;
      }

      dirtyFiles = new ArrayList<>(myDirtyLocalFiles);
    }

    map = FileGrouper.distributeFilesByConnection(dirtyFiles, myProject);
    Set<VirtualFile> ignoredSet = new HashSet<>();
    try {
      for (P4Connection connection : map.keySet()) {
        ignoredSet.addAll(getFilesOutsideClientSpec(myProject, connection, map.get(connection)));
      }
    }
    catch (VcsException e) {
      LOG.warn("Failed to get ignored files", e);
    }

    synchronized (LOCK) {
      for (VirtualFile file : map.values()) {
        FilePath path = VcsUtil.getFilePath(file);
        if (ignoredSet.contains(file)) {
          myIgnoredFiles.add(path);
        } else {
          myUnversionedFiles.add(path);
        }
      }

      myDirtyLocalFiles.clear();
      myInUpdate = false;
    }

    sw.stop();
    LOG.debug("update finished in %d seconds".formatted(sw.elapsed().toSeconds()));

    myProject.getMessageBus().syncPublisher(VcsManagedFilesHolderListener.class).updatingModeChanged();
    myProject.getMessageBus().syncPublisher(ChangeListListener.class).unchangedFileStatusChanged();
  }

  public void markUnversioned(List<VirtualFile> files) {
    synchronized (LOCK) {
      myDirtyLocalFiles.addAll(files);
    }
  }

  public void markUnknown(Set<VirtualFile> files) {
    for (VirtualFile file : files) {
      FilePath path = VcsUtil.getFilePath(file);
      synchronized (LOCK) {
        myUnversionedFiles.remove(path);
        myIgnoredFiles.remove(path);
      }
    }
  }

  public void markUnknown(@Nullable FilePath path) {
    if (path != null) {
      synchronized (LOCK) {
        myUnversionedFiles.remove(path);
        myIgnoredFiles.remove(path);
      }
    }
  }

  private static Set<VirtualFile> getExcludedFiles(Project project, P4Connection connection, Collection<VirtualFile> files) throws VcsException {
    PerforceClient client = PerforceManager.getInstance(project).getClient(connection);
    String clientName = client.getName();
    if (clientName == null) {
      return Collections.emptySet();
    }

    List<String> roots = ContainerUtil.map(client.getRoots(), FileUtil::toSystemIndependentName);
    List<View> views = client.getViews();
    return new LinkedHashSet<>(ContainerUtil.filter(files, f -> {
      String clientSpecPath = getClientSpecPath(f, clientName, roots);
      return clientSpecPath != null && View.isExcluded(clientSpecPath, views);
    }));
  }

  private static @Nullable String getClientSpecPath(VirtualFile file, String clientName, List<String> roots) {
    String path = file.getPath();
    for (String root : roots) {
      if (FileUtil.startsWith(path, root)) {
        return "//" + clientName + "/" + StringUtil.trimStart(path.substring(root.length()), "/");
      }
    }
    return null;
  }

  private static Set<VirtualFile> getIgnoredFiles(Project project, P4Connection connection, List<VirtualFile> toCheckIgnored)
    throws VcsException{

    if (toCheckIgnored.isEmpty() || !PROCESS_IGNORED) {
      return Collections.emptySet();
    }

    ServerVersion serverVersion = PerforceManager.getInstance(project).getServerVersion(connection);

    if (serverVersion != null && serverVersion.supportsIgnoresCommand()) {
      PerforceSettings settings = PerforceSettings.getSettings(project);
      if (settings.USE_PATTERN_MATCHING_IGNORE) {
        return getIgnoredByPatterns(project, connection, toCheckIgnored);
      }

      return getIgnoredFilesByIgnores(project, connection, toCheckIgnored);
    }

    return getIgnoredFilesByPreviewAdd(project, connection, toCheckIgnored);
  }

  private static Set<VirtualFile> getIgnoredByPatterns(Project project, P4Connection connection, List<VirtualFile> toCheckIgnored) {
    Stopwatch sw = Stopwatch.createStarted();
    P4IgnoresMappingsHelper mappingsHelper = P4IgnoresMappingsHelper.create(project, connection);
    Set<VirtualFile> ignoredFiles = new HashSet<>();
    for (VirtualFile file : toCheckIgnored) {
      if (mappingsHelper.isIgnored(file)) {
        ignoredFiles.add(file);
      }
    }

    sw.stop();
    LOG.debug("checking %d ignored files by pattern matching took %d s".formatted(toCheckIgnored.size(), sw.elapsed().toSeconds()));
    return ignoredFiles;
  }

  private static Set<VirtualFile> getIgnoredFilesByPreviewAdd(Project project, P4Connection connection, List<VirtualFile> toCheckIgnored)
    throws VcsException {
    Stopwatch sw = Stopwatch.createStarted();
    ExecResult execResult = PerforceRunner.getInstance(project).previewAdd(connection, toCheckIgnored);

    Set<VirtualFile> ignored = new LinkedHashSet<>();
    for (String line : execResult.getStderr().split("\n")) {
      if (line.isEmpty()) {
        continue;
      }
      if (line.contains("no permission for operation on file(s)")) {
        continue;
      }
      String path = StringUtil.trimEnd(line, PerforceRunner.NOT_IN_CLIENT_VIEW_MESSAGE + ".");
      if (path.equals(line)) {
        PerforceRunner.checkError(execResult, PerforceSettings.getSettings(project), null);
        continue;
      }
      ContainerUtil.addIfNotNull(ignored, VirtualFileUtil.findFileByIoFile(new File(path), false));
    }
    for (String line : execResult.getStdout().split("\n")) {
      if (line.isEmpty()) {
        continue;
      }
      String path = StringUtil.trimEnd(line, ' ' + PerforceLocalize.fileIgnoredCannotBeAdded().get());
      if (path.equals(line)) {
        continue;
      }
      ContainerUtil.addIfNotNull(ignored, VirtualFileUtil.findFileByIoFile(new File(path), false));
    }

    sw.stop();
    LOG.debug("checking %d ignored files by add preview took %d s".formatted(toCheckIgnored.size(), sw.elapsed().toSeconds()));

    return ignored;
  }

  private static Set<VirtualFile> getIgnoredFilesByIgnores(Project project, P4Connection connection, List<VirtualFile> toCheckIgnored) {
    Stopwatch sw = Stopwatch.createStarted();
    Set<VirtualFile> ignored = new LinkedHashSet<>();
    // 'p4 ignores' doesn't support '-x argfile', so we split manually
    for (List<VirtualFile> group : JBIterable.from(toCheckIgnored).split(100).map(JBIterable::toList)) {
      ExecResult execResult = PerforceRunner.getInstance(project).ignores(connection, group);

      if (execResult.getExitCode() == -1) {
        LOG.warn("P4 Ignores returned -1");
        continue;
      }

      for (String line : execResult.getStdout().split("\n")) {
        if (line.isEmpty()) {
          continue;
        }

        String path = StringUtil.trimEnd(line, ' ' + PerforceLocalize.fileIgnored().get());
        ContainerUtil.addIfNotNull(ignored, VirtualFileUtil.findFileByIoFile(new File(path), false));
      }
    }

    sw.stop();
    LOG.debug("checking %d ignored files by p4 ignores took %d s".formatted(toCheckIgnored.size(), sw.elapsed().toSeconds()));
    return ignored;
  }

  public static Set<VirtualFile> getFilesOutsideClientSpec(Project project, P4Connection connection, Collection<VirtualFile> files)
    throws VcsException {
    List<VirtualFile> filtered = ContainerUtil.filter(files, file -> !file.getPath().contains("..."));

    Set<VirtualFile> excluded = getExcludedFiles(project, connection, filtered);

    Set<VirtualFile> result = new LinkedHashSet<>(excluded);
    result.addAll(getIgnoredFiles(project, connection, ContainerUtil.filter(filtered, f -> !excluded.contains(f))));
    return result;
  }

  private boolean isPotentiallyIgnoredFile(FilePath file) {
    return (Registry.is("p4.ignore.all.potentially.ignored", true) && VcsIgnoreManager.getInstance(myProject).isPotentiallyIgnoredFile(file));
  }
}

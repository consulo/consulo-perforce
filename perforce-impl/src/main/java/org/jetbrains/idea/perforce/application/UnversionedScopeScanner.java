// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.application;

import consulo.application.util.registry.Registry;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.collection.MultiMap;
import consulo.util.io.FileUtil;
import consulo.util.lang.Pair;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.change.ChangeListManager;
import consulo.versionControlSystem.change.VcsIgnoreManager;
import consulo.versionControlSystem.util.VcsRootIterator;
import consulo.versionControlSystem.util.VcsUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import org.jetbrains.idea.perforce.perforce.P4HaveParser;
import org.jetbrains.idea.perforce.perforce.PathsHelper;
import org.jetbrains.idea.perforce.perforce.PerforceRunner;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class UnversionedScopeScanner {
  private static final Logger LOG = Logger.getInstance(UnversionedScopeScanner.class);
  private final Project myProject;
  private final PerforceRunner myRunner;

  public UnversionedScopeScanner(Project project) {
    myProject = project;
    myRunner = PerforceRunner.getInstance(myProject);
  }

  protected abstract void checkCanceled();

  public ScanResult doRescan(Collection<FilePath> dirtyFiles, boolean force) throws VcsException {
    ScanResult result = new ScanResult();
    if (dirtyFiles.isEmpty()) {
      return result;
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("scope=" + dirtyFiles);
    }
    MultiMap<P4Connection, FilePath> map = FileGrouper.distributePathsByConnection(dirtyFiles, myProject);
    for (P4Connection connection : map.keySet()) {
      Collection<FilePath> files = map.get(connection);

      Set<VirtualFile> localFiles = enumerateLocalFiles(files);
      result.allLocalFiles.addAll(localFiles);
      checkCanceled();

      LocalFileSystem fs = LocalFileSystem.getInstance();
      myRunner.executeMultiple(collectPaths(files), connection, new P4HaveParser(PerforceManager.getInstance(myProject)) {
        @Override
        protected void consumeRevision(String path, long revision) {
          checkCanceled();

          VirtualFile vFile = findVirtualFile(path);
          if (vFile != null) {
            localFiles.remove(vFile);
          }
          else {
            if (LOG.isDebugEnabled()) {
              LOG.debug("locally missing file reported: " + path);
            }
            result.missingFiles.add(path);
          }
        }

        private @Nullable VirtualFile findVirtualFile(String path) {
          VirtualFile vFile = fs.findFileByPathIfCached(FileUtil.toSystemIndependentName(path));
          if (vFile != null) {
            return vFile;
          }

          // p4 may report AppleDouble resource fork files as "%name" next to the original file
          File ioFile = new File(path);
          String appleForkPath = FileUtil.toSystemIndependentName(ioFile.getParent()) + "/%" + ioFile.getName();
          return fs.findFileByPathIfCached(FileUtil.toSystemIndependentName(appleForkPath));
        }
      });
      checkCanceled();

      if (force) {
        result.localOnly.addAll(localFiles);
      }
      else {
        ChangeListManager clm = ChangeListManager.getInstance(myProject);
        for (VirtualFile file : localFiles) {
          boolean ignored = clm.isIgnoredFile(file);
          if (LOG.isDebugEnabled()) {
            LOG.debug("localOnly reported: " + file + (ignored ? ", ignored" : ""));
          }
          if (!ignored) {
            result.localOnly.add(file);
          }
        }
      }
    }
    return result;
  }

  private PathsHelper collectPaths(Collection<FilePath> value) {
    PathsHelper helper = new PathsHelper(PerforceManager.getInstance(myProject));
    for (FilePath dir : value) {
      if (dir.isDirectory()) {
        helper.addRecursively(dir);
      }
      else {
        helper.add(dir);
      }
    }
    return helper;
  }

  static List<FilePath> createEverythingDirtyScope(Project project) throws VcsException {
    List<FilePath> scope = new ArrayList<>();
    for (Pair<P4Connection, Collection<VirtualFile>> pair : PerforceVcs.getInstance(project).getRootsByConnections()) {
      for (VirtualFile file : pair.second) {
        scope.add(VcsUtil.getFilePath(file));
      }
    }
    return scope;
  }

  static class ScanResult {
    Set<String> missingFiles = new HashSet<>();
    List<VirtualFile> localOnly = new ArrayList<>();
    Set<VirtualFile> allLocalFiles = new HashSet<>();
  }

  private Set<VirtualFile> enumerateLocalFiles(Collection<FilePath> roots) {
    Set<VirtualFile> localFiles = new HashSet<>();
    if (myProject.isDisposed()) {
      return localFiles;
    }
    // every root is treated as a recursively dirty directory
    for (FilePath root : roots) {
      VirtualFile vRoot = root.getVirtualFile();
      if (vRoot == null) {
        vRoot = VirtualFileUtil.findFileByIoFile(root.getIOFile(), false);
      }
      if (vRoot == null || !vRoot.isValid()) {
        continue;
      }
      VcsRootIterator.iterateVfUnderVcsRoot(myProject, vRoot, file -> {
        if (!file.isDirectory() && !skipPotentiallyIgnored(file)) {
          localFiles.add(file);
        }
        else {
          checkCanceled();
        }
        return true;
      });
    }
    return localFiles;
  }

  private boolean skipPotentiallyIgnored(VirtualFile file) {
    return Registry.is("p4.ignore.all.potentially.ignored", true) && VcsIgnoreManager.getInstance(myProject).isPotentiallyIgnoredFile(file);
  }
}

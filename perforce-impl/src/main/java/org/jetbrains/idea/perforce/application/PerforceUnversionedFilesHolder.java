// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.application;

import consulo.logging.Logger;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.change.FileHolder;
import consulo.versionControlSystem.change.VcsManagedFilesHolder;
import consulo.versionControlSystem.change.VcsModifiableDirtyScope;
import consulo.virtualFileSystem.VirtualFile;

import java.util.Collection;

public class PerforceUnversionedFilesHolder implements VcsManagedFilesHolder {
  private static final Logger LOG = Logger.getInstance(PerforceUnversionedFilesHolder.class);

  private final PerforceUnversionedTracker myUnversionedTracker;

  public PerforceUnversionedFilesHolder(PerforceUnversionedTracker unversionedTracker) {
    myUnversionedTracker = unversionedTracker;
  }

  @Override
  public boolean isInUpdatingMode() {
    return myUnversionedTracker.isInUpdateMode();
  }

  @Override
  public boolean containsFile(FilePath file, VirtualFile vcsRoot) {
    return myUnversionedTracker.isUnversioned(file);
  }

  @Override
  public Collection<FilePath> values() {
    return myUnversionedTracker.getUnversionedFiles();
  }

  // the tracker owns the data: the holder is a read-only view over it

  @Override
  public void cleanAll() {
  }

  @Override
  public void cleanAndAdjustScope(VcsModifiableDirtyScope scope) {
  }

  @Override
  public void addFile(FilePath file) {
    LOG.warn("Attempt to populate vcs-managed files holder with " + file, new Throwable());
  }

  @Override
  public FileHolder copy() {
    return this;
  }

  @Override
  public HolderType getType() {
    return HolderType.UNVERSIONED;
  }
}

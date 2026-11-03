// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.application;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.persist.StoragePathMacros;
import consulo.project.Project;
import consulo.util.xml.serializer.annotation.Attribute;
import consulo.versionControlSystem.change.Change;
import consulo.versionControlSystem.change.ChangeListListener;
import consulo.versionControlSystem.change.ChangeListManager;
import consulo.versionControlSystem.change.ContentRevision;
import consulo.versionControlSystem.change.LocalChangeList;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.idea.perforce.perforce.PerforceSettings;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
@Singleton
@State(name = "LastSuccessfulUpdateTracker", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class LastSuccessfulUpdateTracker implements PersistentStateComponent<LastSuccessfulUpdateTracker.ChangesUpdateResult> {
  public static LastSuccessfulUpdateTracker getInstance(Project project) {
    return project.getInstance(LastSuccessfulUpdateTracker.class);
  }

  public static class ChangedFile {
    @Attribute("beforePath")
    public @Nullable String beforePath;
    @Attribute("afterPath")
    public @Nullable String afterPath;
  }

  public static class PersistentChangeList {
    public @Nullable String name;
    public List<ChangedFile> files = new ArrayList<>();
  }

  public static class ChangesUpdateResult {
    public List<PersistentChangeList> changeLists = new ArrayList<>();
  }

  private final Project myProject;
  private ChangesUpdateResult myResult = new ChangesUpdateResult();
  private boolean myUpdateSuccessful;

  @Inject
  public LastSuccessfulUpdateTracker(Project project) {
    myProject = project;
    project.getMessageBus().connect().subscribe(ChangeListListener.class, new MyChangeListListener());
  }

  @Override
  public ChangesUpdateResult getState() {
    return myResult;
  }

  @Override
  public void loadState(ChangesUpdateResult state) {
    myResult = state;
  }

  public void updateStarted() {
    myUpdateSuccessful = false;
  }

  public void updateSuccessful() {
    myUpdateSuccessful = true;
  }

  public List<PersistentChangeList> getChangeLists() {
    return myResult.changeLists;
  }

  private class MyChangeListListener implements ChangeListListener {
    @Override
    public void changeListUpdateDone() {
      if (myUpdateSuccessful && PerforceSettings.getSettings(myProject).ENABLED) {
        List<PersistentChangeList> results = new ArrayList<>();
        List<LocalChangeList> changeLists = ChangeListManager.getInstance(myProject).getChangeLists();
        for (LocalChangeList changeList : changeLists) {
          PersistentChangeList persistentList = new PersistentChangeList();
          persistentList.name = changeList.getName();
          persistentList.files = new ArrayList<>();
          results.add(persistentList);

          for (Change c : changeList.getChanges()) {
            ChangedFile f = new ChangedFile();
            ContentRevision beforeRevision = c.getBeforeRevision();
            if (beforeRevision != null) {
              f.beforePath = beforeRevision.getFile().getPath();
            }
            ContentRevision afterRevision = c.getAfterRevision();
            f.afterPath = afterRevision == null ? null : afterRevision.getFile().getPath();
            persistentList.files.add(f);
          }
        }
        myResult.changeLists = results;
      }
    }
  }
}

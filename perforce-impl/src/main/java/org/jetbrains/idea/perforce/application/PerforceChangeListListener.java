// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.application;

import consulo.application.progress.PerformInBackgroundOption;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.perforce.localize.PerforceLocalize;
import consulo.project.Project;
import consulo.versionControlSystem.AbstractVcs;
import consulo.versionControlSystem.AbstractVcsHelper;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.change.Change;
import consulo.versionControlSystem.change.ChangeList;
import consulo.versionControlSystem.change.ChangeListListener;
import consulo.versionControlSystem.change.ChangesUtil;
import consulo.versionControlSystem.change.LocalChangeList;
import org.jetbrains.idea.perforce.operations.P4MoveToChangeListOperation;
import org.jetbrains.idea.perforce.operations.VcsOperation;
import org.jetbrains.idea.perforce.operations.VcsOperationLog;
import org.jetbrains.idea.perforce.perforce.PerforceRunner;
import org.jetbrains.idea.perforce.perforce.PerforceSettings;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

// it assumes it is not called on AWT thread
public class PerforceChangeListListener implements ChangeListListener {
  private static final Logger LOG = Logger.getInstance(PerforceChangeListListener.class);
  private final Project myProject;
  private final PerforceNumberNameSynchronizer mySynchronizer;

  public PerforceChangeListListener(Project project, PerforceNumberNameSynchronizer synchronizer) {
    myProject = project;
    mySynchronizer = synchronizer;
  }

  @Override
  public void changeListAdded(ChangeList list) {
    if (!(list instanceof LocalChangeList localChangeList)) {
      return;
    }

    PerforceSettings settings = PerforceSettings.getSettings(myProject);
    if (!settings.ENABLED || !settings.FORCE_SYNC_CHANGELISTS) {
      return;
    }

    for(P4Connection connection: settings.getAllConnections()) {
      if (!checkConnection(connection, PerforceLocalize.changelistCreateError()))
        continue;

      try {
        mySynchronizer.findOrCreate(connection, localChangeList);
      }
      catch (VcsException e) {
        AbstractVcsHelper.getInstance(myProject).showError(e, PerforceLocalize.changelistCreateError());
        LOG.info(e);
      }
    }
  }

  @Override
  public void changeListRemoved(ChangeList list) {
    String name = list.getName();
    applyToAllMatching(name, new MyCallback() {
      @Override
      public void call(Long number, P4Connection connection, ConnectionKey connectionKey) throws VcsException {
        PerforceRunner.getInstance(myProject).deleteChangeList(connection, number, true, false, true);

        mySynchronizer.removeList(number);
      }
    }, PerforceLocalize.changelistDeleteError());
  }

  private interface MyCallback {
    void call(Long number, P4Connection connection, ConnectionKey connectionKey) throws VcsException;
  }

  private void applyToAllMatching(String listName, MyCallback numberConsumer, LocalizeValue errorPrefix) {
    PerforceSettings settings = PerforceSettings.getSettings(myProject);
    if (!settings.ENABLED) {
      return;
    }

    for(P4Connection connection: settings.getAllConnections()) {
      if (!checkConnection(connection, errorPrefix))
        continue;

      ConnectionKey connectionKey = connection.getConnectionKey();

      Long number = mySynchronizer.getNumber(connectionKey, listName);
      if (number != null) {
        try {
          numberConsumer.call(number, connection, connectionKey);
        }
        catch (VcsException e) {
          AbstractVcsHelper.getInstance(myProject).showError(e, errorPrefix);
          LOG.info(e);
        }
      }
    }
  }

  @Override
  public void changeListRenamed(ChangeList list, String oldName) {
    handleChangeListRename(list, oldName);
  }

  @Override
  public void changeListCommentChanged(ChangeList list, String oldComment) {
    handleChangeListRename(list, list.getName());
  }

  private void handleChangeListRename(ChangeList list, String oldName) {
    PerforceRunner runner = PerforceRunner.getInstance(myProject);
    applyToAllMatching(oldName, new MyCallback() {
      @Override
      public void call(Long number, P4Connection connection, ConnectionKey connectionKey) throws VcsException {
        runner.renameChangeList(number, getP4Description(list), connection);
      }
    }, PerforceLocalize.changelistRenameError());

    String newName = list.getName();
    if (! oldName.equals(newName)) {
      mySynchronizer.renameList(oldName, newName);
    }
  }

  private static String getP4Description(ChangeList list) {
    String description = list.getComment().trim();
    if (description.isEmpty()) description = list.getName();
    return description;
  }

  @Override
  public void changesMoved(Collection<Change> changes, ChangeList fromList, ChangeList toList) {
    List<Change> changesUnderPerforce = new ArrayList<>();
    for (Change change : changes) {
      AbstractVcs vcs = ChangesUtil.getVcsForChange(change, myProject);
      if (vcs != null && PerforceVcs.getKey().equals(vcs.getKeyInstanceMethod())) {
        changesUnderPerforce.add(change);
      }
    }

    List<VcsOperation> operations = new ArrayList<>();
    String toListName = toList.getName();
    for(final Change change: changesUnderPerforce) {
      operations.add(new P4MoveToChangeListOperation(change, toListName));
    }
    VcsOperationLog.getInstance(myProject).queueOperations(operations, PerforceLocalize.changelistMovingToAnother(),
                                                       PerformInBackgroundOption.ALWAYS_BACKGROUND);
  }

  private boolean checkConnection(P4Connection connection, LocalizeValue errorPrefix) {
    try {
      PerforceManager.ensureValidClient(myProject, connection);
    }
    catch (VcsException e) {
      AbstractVcsHelper.getInstance(myProject).showError(e, errorPrefix);
      LOG.info(e);
      return false;
    }

    return true;
  }
}

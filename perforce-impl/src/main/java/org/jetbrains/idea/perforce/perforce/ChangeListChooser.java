/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.perforce.perforce;

import consulo.application.progress.ProgressManager;
import consulo.project.Project;
import consulo.ui.ex.awt.Messages;
import consulo.versionControlSystem.VcsException;
import consulo.ui.ex.awt.ColoredListCellRenderer;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.perforce.localize.PerforceLocalize;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;

import org.jspecify.annotations.Nullable;

import javax.swing.ComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JList;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

public class ChangeListChooser {
    private final JComboBox myChangeList;

    private final P4Connection myConnection;
    private final PerforceRunner myRunner;
    private final Project myProject;

    public ChangeListChooser(JComboBox changeLists,
                                                      @Nullable JButton newButton,
                                                      Project project,
                                                      P4Connection connection) {
        myChangeList = changeLists;
        myConnection = connection;
        myRunner = PerforceRunner.getInstance(project);
        myProject = project;

        if (newButton != null) {
            newButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    String description = Messages.showInputDialog(PerforceLocalize.requestTextEnterChangeDescription().get(), PerforceLocalize.requestTitleCreateNewChangeList().get(), Messages.getQuestionIcon());
                    if (description != null) {
                        try {
                            long newChangeListNumber = ProgressManager.getInstance().runProcessWithProgressSynchronously(
                                () -> myRunner.createChangeList(description, connection, null), PerforceLocalize.activityPerforming(), false, myProject);
                            refreshChangeLists(newChangeListNumber);
                        }
                        catch (VcsException e1) {
                            Messages.showErrorDialog(PerforceLocalize.messageTextCannotCreateNewChangelist(String.valueOf(e1.getLocalizedMessage())).get(), PerforceLocalize.messageTitleCreateNewChangelist().get());
                        }
                    }
                }
            });
        }
        myChangeList.setRenderer(new ColoredListCellRenderer<Object>() {
            @Override
            protected void customizeCellRenderer(JList<?> list, @Nullable Object value, int index, boolean selected, boolean hasFocus) {
                if (value instanceof PerforceChangeList changeList) {
                    String description = changeList.getComment();
                    if (description.length() > 30) {
                        description = description.substring(0, 25) + "...";
                    }
                    append("#" + changeList.getNumber() + " (" + description + ")", SimpleTextAttributes.REGULAR_ATTRIBUTES);
                }
                else if (value != null) {
                    //noinspection HardCodedStringLiteral
                    append(value.toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
                }
            }
        });
    }

    private void refreshChangeLists(long newChangeListNumber) {
        long selected = newChangeListNumber > 0 ? newChangeListNumber : getChangeListNumber();
        try {
            List<PerforceChangeList> changeLists = ProgressManager.getInstance().runProcessWithProgressSynchronously(
                () -> myRunner.getPendingChangeLists(myConnection), PerforceLocalize.activityPerforming(), false, myProject);
            fillChangeLists(changeLists, selected);
        }
        catch (VcsException e1) {
            Messages.showErrorDialog(PerforceLocalize.messageTextCannotLoadChanges(String.valueOf(e1.getLocalizedMessage())).get(), PerforceLocalize.messageTitleRefreshChanges().get());
        }
    }

    public void fillChangeLists(List<PerforceChangeList> changeLists, long selection) {
        myChangeList.removeAllItems();
        myChangeList.addItem(getDefault());
        Object selected = getDefault();
        for (PerforceChangeList changeListInfo : changeLists) {
            myChangeList.addItem(changeListInfo);
            if (changeListInfo.getNumber() == selection) {
                selected = changeListInfo;
            }
        }
        myChangeList.setSelectedItem(selected);
    }

    public long getChangeListNumber() {
        ComboBoxModel model = myChangeList.getModel();
        Object selectedItem = model.getSelectedItem();
        if (selectedItem instanceof PerforceChangeList) {
            return ((PerforceChangeList)selectedItem).getNumber();
        }
        else {
            return -1;
        }
    }

    private static String getDefault() {
        return PerforceLocalize.defaultChangelistPresentation().get();
    }
}

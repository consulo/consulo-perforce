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
package org.jetbrains.idea.perforce.application;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import consulo.configurable.ConfigurationException;
import consulo.logging.Logger;
import consulo.perforce.localize.PerforceLocalize;
import consulo.project.Project;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.IdeBorderFactory;
import consulo.ui.ex.awt.JBList;
import consulo.ui.ex.awt.JBScrollPane;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.ScrollPaneFactory;
import consulo.util.lang.StringUtil;
import consulo.versionControlSystem.VcsException;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.idea.perforce.perforce.BranchSpec;
import org.jetbrains.idea.perforce.perforce.ChangeListChooser;
import org.jetbrains.idea.perforce.perforce.P4File;
import org.jetbrains.idea.perforce.perforce.ParticularConnectionSettings;
import org.jetbrains.idea.perforce.perforce.PerforceChangeList;
import org.jetbrains.idea.perforce.perforce.PerforceRunner;
import org.jetbrains.idea.perforce.perforce.PerforceSettings;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jspecify.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.border.TitledBorder;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.text.DateFormat;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ParticularConnectionPerforceIntegratePanel implements PerforcePanel {
    private static final Logger LOG = Logger.getInstance(ParticularConnectionPerforceIntegratePanel.class);
    private final Project myProject;
    private final JComboBox<String> myBranches;
    private final JComboBox myChangeLists;
    private final JButton myNewChangeListButton;
    private final JPanel myPanel;
    private final ChangeListChooser myChangeListChooser;
    private final JCheckBox myUsingChangeList;
    private final JCheckBox myIsReverse;
    private final JTextArea myBranchSpecDescription;

    private final JLabel myIntegrateWithChangeListLabel;
    private final P4Connection myConnection;
    private final PerforceRunner myRunner;

    @SuppressWarnings("unchecked")
    public ParticularConnectionPerforceIntegratePanel(Project project, P4Connection connection) {
        myProject = project;
        myPanel = new JPanel();
        myPanel.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
        JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
        myPanel.add(panel1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null,
            0, false));
        panel1.setBorder(IdeBorderFactory.PlainSmallWithIndent.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            PerforceLocalize.configureIntegrateBranchSpecTitle().get(),
            TitledBorder.DEFAULT_JUSTIFICATION,
            TitledBorder.DEFAULT_POSITION,
            null,
            null
        ));
        myBranches = new JComboBox<>();
        panel1.add(myBranches, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
            GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
            0, false));
        myIsReverse = new JCheckBox();
        PerforceUpdatePanel.loadButtonText(myIsReverse, PerforceLocalize.configureIntegrateReverseCheckbox());
        panel1.add(myIsReverse, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        JBScrollPane jBScrollPane1 = new JBScrollPane();
        jBScrollPane1.setEnabled(true);
        jBScrollPane1.setWheelScrollingEnabled(true);
        panel1.add(jBScrollPane1, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
            new Dimension(-1, 100), null, null, 0, false));
        myBranchSpecDescription = new JTextArea();
        myBranchSpecDescription.setEditable(false);
        jBScrollPane1.setViewportView(myBranchSpecDescription);
        JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
        myPanel.add(panel2, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null,
            0, false));
        JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        panel2.add(panel3, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null,
            0, false));
        panel3.setBorder(IdeBorderFactory.PlainSmallWithIndent.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            PerforceLocalize.configureIntegrateStoreChangesToChangeListGroupTitle().get(),
            TitledBorder.DEFAULT_JUSTIFICATION,
            TitledBorder.DEFAULT_POSITION,
            null,
            null
        ));
        myChangeLists = new JComboBox();
        panel3.add(myChangeLists, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
            GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
            0, false));
        myNewChangeListButton = new JButton();
        PerforceUpdatePanel.loadButtonText(myNewChangeListButton, PerforceLocalize.buttonTextNew());
        panel3.add(myNewChangeListButton,
            new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        JPanel panel4 = new JPanel();
        panel4.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        panel2.add(panel4, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null,
            0, false));
        myUsingChangeList = new JCheckBox();
        PerforceUpdatePanel.loadButtonText(myUsingChangeList, PerforceLocalize.configureIntegrateIntegrateChangeListLabel());
        panel4.add(myUsingChangeList, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myIntegrateWithChangeListLabel = new JLabel();
        myIntegrateWithChangeListLabel.setText("###");
        panel4.add(myIntegrateWithChangeListLabel,
            new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        Spacer spacer1 = new Spacer();
        myPanel.add(spacer1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
            GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));

        myChangeListChooser = new ChangeListChooser(myChangeLists, myNewChangeListButton, project, connection);
        myConnection = connection;
        myRunner = PerforceRunner.getInstance(project);

        myUsingChangeList.addActionListener(e -> {
            if (myUsingChangeList.isSelected()) {
                PerforceChangeList selectedChangeList = chooseChangeList(project);

                if (selectedChangeList != null) {
                    myIntegrateWithChangeListLabel.setText(String.valueOf(selectedChangeList.getNumber()));
                }
                else {
                    myIntegrateWithChangeListLabel.setText("");
                    myUsingChangeList.setSelected(false);
                }
            }
            else {
                myIntegrateWithChangeListLabel.setText("");
            }
        });

        myBranches.addActionListener(e -> {
            String selectedBranchName = getSelectedBranchName();
            if (selectedBranchName == null) {
                myBranchSpecDescription.setText(PerforceLocalize.noBranchSpecSelectedLabelText().get());
            }
            else {
                try {
                    BranchSpec branchSpec = myRunner.loadBranchSpec(selectedBranchName, connection);
                    myBranchSpecDescription.setText(composeBranchSpecText(branchSpec));
                }
                catch (VcsException e1) {
                    myBranchSpecDescription.setText(
                        PerforceLocalize.cannotLoadBranchSpecDescriptionErrorText(String.valueOf(e1.getLocalizedMessage())).get()
                    );
                }
            }
        });
    }

    public JComponent getRootComponent() {
        return myPanel;
    }

    private @Nullable String getSelectedBranchName() {
        return (String) myBranches.getSelectedItem();
    }

    private @Nullable PerforceChangeList chooseChangeList(Project project) {
        try {
            List<PerforceChangeList> changes = getChangesToIntegrate(getSelectedBranchName(), myIsReverse.isSelected());
            ChooseChangeListDialog dialog = new ChooseChangeListDialog(project, changes);
            return dialog.showAndGet() ? dialog.getSelectedChangeList() : null;
        }
        catch (VcsException e) {
            LOG.info(e);
            return null;
        }
    }

    public List<PerforceChangeList> getChangesToIntegrate(@Nullable String branchName, boolean reverse) throws VcsException {
        List<String> fileSpecs;
        if (branchName != null) {
            fileSpecs = myRunner.getBranchViews(branchName, reverse, myConnection);
        }
        else {
            VirtualFile baseDir = Objects.requireNonNull(myProject.getBaseDir());
            fileSpecs = Collections.singletonList(P4File.create(baseDir).getRecursivePath());
        }

        return myRunner.getSubmittedChangeLists(myConnection, null, null, -1, false, fileSpecs);
    }

    private static String composeBranchSpecText(BranchSpec branchSpec) {
        StringBuilder result = new StringBuilder();
        result.append(branchSpec.getDescription());
        result.append(" (");
        result.append(PerforceLocalize.configureIntegrateBranchSpecDescriptionPartCreatedBy(branchSpec.getOwner()).get());
        result.append(")");
        result.append("\n");
        for (String view : branchSpec.getViews()) {
            result.append("\n");
            result.append(view);
        }

        return result.toString();
    }

    @Override
    public void updateFrom(PerforceSettings settings) {
        myBranches.removeAllItems();

        ParticularConnectionSettings connectionSettings = settings.getSettings(myConnection);

        try {
            List<String> branches = myRunner.getBranches(myConnection);

            for (String branch : branches) {
                myBranches.addItem(branch);
            }

            myBranches.setSelectedItem(connectionSettings.INTEGRATE_BRANCH_NAME);

            if (myBranches.getSelectedIndex() < 0 && myBranches.getItemCount() > 0) {
                myBranches.setSelectedIndex(0);
            }
        }
        catch (VcsException e) {
            Messages.showErrorDialog(
                PerforceLocalize.integrateConfigurableCannotLoadBranchesErrorMessage(String.valueOf(e.getLocalizedMessage())).get(),
                PerforceLocalize.integrateConfigurableLoadBranchesDialogTitle().get()
            );
        }

        try {
            myChangeListChooser.fillChangeLists(myRunner.getPendingChangeLists(myConnection), 0);
        }
        catch (VcsException e1) {
            Messages.showErrorDialog(
                PerforceLocalize.messageTextCannotLoadChanges(String.valueOf(e1.getLocalizedMessage())).get(),
                PerforceLocalize.messageTitleRefreshChanges().get()
            );
        }

        myIsReverse.setSelected(connectionSettings.INTEGRATE_REVERSE);

        List<PerforceChangeList> changeLists;
        try {
            changeLists = getChangesToIntegrate(getSelectedBranchName(), myIsReverse.isSelected());
        }
        catch (VcsException e) {
            LOG.info(e);
            changeLists = Collections.emptyList();
        }

        boolean isChangeExists = connectionSettings.INTEGRATE_CHANGE_LIST && changeLists.stream()
            .anyMatch(it -> String.valueOf(it.getNumber()).equals(connectionSettings.INTEGRATED_CHANGE_LIST_NUMBER));
        myUsingChangeList.setSelected(isChangeExists);
        myIntegrateWithChangeListLabel.setText(isChangeExists ? connectionSettings.INTEGRATED_CHANGE_LIST_NUMBER : "");
    }

    @Override
    public void applyTo(PerforceSettings settings) throws ConfigurationException {
        String selectedBranch = getSelectedBranchName();
        if (selectedBranch == null) {
            throw new ConfigurationException(PerforceLocalize.configureIntegrateBranchspecNoneSelected());
        }
        ParticularConnectionSettings connectionSettings = settings.getSettings(myConnection);

        connectionSettings.INTEGRATE_BRANCH_NAME = selectedBranch;
        saveSettings(connectionSettings);
    }

    @Override
    public void cancel(PerforceSettings settings) {
        ParticularConnectionSettings connectionSettings = settings.getSettings(myConnection);

        String selectedBranch = getSelectedBranchName();
        if (selectedBranch != null) {
            connectionSettings.INTEGRATE_BRANCH_NAME = selectedBranch;
        }
        saveSettings(connectionSettings);
    }

    private void saveSettings(ParticularConnectionSettings connectionSettings) {
        connectionSettings.INTEGRATE_TO_CHANGELIST_NUM = myChangeListChooser.getChangeListNumber();

        connectionSettings.INTEGRATED_CHANGE_LIST_NUMBER = myIntegrateWithChangeListLabel.getText();
        connectionSettings.INTEGRATE_CHANGE_LIST = myUsingChangeList.isSelected();
        connectionSettings.INTEGRATE_REVERSE = myIsReverse.isSelected();
    }

    @Override
    public boolean isModified(PerforceSettings settings) {
        ParticularConnectionSettings connectionSettings = settings.getSettings(myConnection);

        return !Objects.equals(connectionSettings.INTEGRATE_BRANCH_NAME, myBranches.getSelectedItem()) ||
            connectionSettings.INTEGRATE_TO_CHANGELIST_NUM != myChangeListChooser.getChangeListNumber() ||
            !Objects.equals(connectionSettings.INTEGRATED_CHANGE_LIST_NUMBER, myIntegrateWithChangeListLabel.getText()) ||
            connectionSettings.INTEGRATE_CHANGE_LIST != myUsingChangeList.isSelected() ||
            connectionSettings.INTEGRATE_REVERSE != myIsReverse.isSelected();
    }

    @Override
    public JPanel getPanel() {
        return myPanel;
    }

    /**
     * Lets the user pick one of the submitted changelists that can be integrated.
     */
    private static class ChooseChangeListDialog extends DialogWrapper {
        private final JBList<PerforceChangeList> myList;

        ChooseChangeListDialog(Project project, List<PerforceChangeList> changeLists) {
            super(project, true);
            myList = new JBList<>(changeLists);
            myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            myList.setCellRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(
                    JList<?> list,
                    Object value,
                    int index,
                    boolean isSelected,
                    boolean cellHasFocus
                ) {
                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    if (value instanceof PerforceChangeList changeList) {
                        setText(changeList.getNumber() + "  " +
                            DateFormat.getDateTimeInstance().format(changeList.getCommitDate()) + "  " +
                            changeList.getCommitterName() + "  " +
                            StringUtil.first(StringUtil.notNullize(changeList.getComment()).replace('\n', ' '), 100, true));
                    }
                    return this;
                }
            });
            if (!changeLists.isEmpty()) {
                myList.setSelectedIndex(0);
            }
            setTitle(PerforceLocalize.integrateChooseChangelistTitle());
            init();
        }

        @Override
        protected JComponent createCenterPanel() {
            JComponent scrollPane = ScrollPaneFactory.createScrollPane(myList);
            scrollPane.setPreferredSize(new Dimension(600, 400));
            return scrollPane;
        }

        @Override
        public @Nullable JComponent getPreferredFocusedComponent() {
            return myList;
        }

        @Nullable PerforceChangeList getSelectedChangeList() {
            return myList.getSelectedValue();
        }
    }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.jobs;

import consulo.application.Application;
import consulo.localize.LocalizeValue;
import consulo.perforce.localize.PerforceLocalize;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.platform.base.localize.CommonLocalize;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.ActionToolbar;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.ex.action.DefaultActionGroup;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.JBTabbedPane;
import consulo.ui.ex.awt.Messages;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.change.LocalChangeList;
import org.jetbrains.idea.perforce.application.ConnectionKey;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jspecify.annotations.Nullable;

import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EditChangelistJobsDialog extends DialogWrapper {
    private final Project myProject;
    private final LocalChangeList myList;
    private final JobsWorker myWorker;
    private final Map<ConnectionKey, P4JobsLogicConn> myConnMap;

    private @Nullable JComponent myInitFocus;

    //--------------
    private final Map<ConnectionKey, MyTab> myTabsMap;
    private JPanel myMainPanel;

    @RequiredUIAccess
    public EditChangelistJobsDialog(
        Project project,
        LocalChangeList list,
        boolean inMemory,
        Map<ConnectionKey, P4JobsLogicConn> connMap,
        Map<ConnectionKey, List<PerforceJob>> perforceJobs
    ) {
        super(project, true);

        myTabsMap = new HashMap<>();

        myProject = project;
        myList = list;
        myWorker = new JobsWorker(myProject);
        myConnMap = connMap;

        myMainPanel = createTabs(inMemory, perforceJobs);
        setCancelButtonText(CommonLocalize.buttonClose());
        setTitle(PerforceLocalize.jobEditLinked(myList.getName()));

        init();
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return myInitFocus == null ? myMainPanel : myInitFocus;
    }

    @Override
    protected @Nullable String getDimensionServiceKey() {
        return "org.jetbrains.idea.perforce.perforce.jobs.EditChangelistJobsDialog";
    }

    @RequiredUIAccess
    private JPanel createTabs(boolean inMemory, Map<ConnectionKey, List<PerforceJob>> perforceJobs) {
        JPanel mainPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gb = DefaultGb.create();

        gb.weightx = 1;
        gb.weighty = 1;
        gb.fill = GridBagConstraints.BOTH;
        JTabbedPane pane = new JBTabbedPane();
        if (myConnMap.size() > 1) {
            mainPanel.add(pane, gb);
        }

        for (Map.Entry<ConnectionKey, P4JobsLogicConn> entry : myConnMap.entrySet()) {
            P4JobsLogicConn value = entry.getValue();
            MyTab tab = new MyTab(myProject, myList, entry.getValue().getSpec(), myWorker, entry.getKey(),
                value.getConnection(), inMemory, perforceJobs.get(entry.getKey()));
            if (myInitFocus == null) {
                myInitFocus = tab.getMainTable().getPreferredFocusTarget();
            }

            myTabsMap.put(entry.getKey(), tab);
            if (myConnMap.size() > 1) {
                pane.add(entry.getKey().getServer(), tab.createCenterPanel());
            }
            else {
                mainPanel.add(tab.createCenterPanel(), gb);
            }
        }
        return mainPanel;
    }

    @Override
    protected JComponent createCenterPanel() {
        return myMainPanel;
    }

    public Map<ConnectionKey, List<PerforceJob>> getJobs() {
        Map<ConnectionKey, List<PerforceJob>> result = new HashMap<>();
        for (MyTab tab : myTabsMap.values()) {
            result.put(tab.myKey, tab.getJobs());
        }
        return result;
    }

    @Override
    protected Action[] createActions() {
        return new Action[]{getCancelAction()};
    }

    private static final class MyTab implements JobsTablePresentation {
        private final Project myProject;
        private final AdderRemover myAdderRemover;
        private final LocalChangeList myList;
        private final PerforceJobSpecification mySpecification;

        private final JobsWorker myWorker;

        private final ConnectionKey myKey;
        private final P4Connection myConnection;

        private final JobsMasterDetails myMainTable;
        private @Nullable JTextField myAddText;
        private @Nullable JButton myAddBtn;
        private @Nullable JButton mySearchBtn;
        private @Nullable JButton myRemoveBtn;

        @RequiredUIAccess
        private MyTab(
            Project project,
            LocalChangeList list,
            PerforceJobSpecification specification,
            JobsWorker worker,
            ConnectionKey key,
            P4Connection connection,
            boolean inMemory,
            @Nullable List<PerforceJob> jobs
        ) {
            myProject = project;
            myList = list;
            mySpecification = specification;
            myWorker = worker;
            myKey = key;
            myConnection = connection;
            myAdderRemover = inMemory ? new MemoryAdderRemover(this) : new WritingAdderRemover(myWorker, this);

            myMainTable = new JobsMasterDetails(myProject);
            if (jobs != null) {
                myMainTable.fillTree(jobs, null);
            }
        }

        JobsMasterDetails getMainTable() {
            return myMainTable;
        }

        private JComponent createToolbar(JComponent targetComponent) {
            DefaultActionGroup group = new DefaultActionGroup();
            group.add(new UnlinkSelectedAction());

            group.add(new AnAction(PerforceLocalize.search(), PerforceLocalize.search(), PlatformIconGroup.actionsFind()) {
                @RequiredUIAccess
                @Override
                public void actionPerformed(AnActionEvent e) {
                    AddJobToChangeListDialog dialog = new AddJobToChangeListDialog(myProject, true, myWorker, mySpecification,
                        myConnection, myKey);
                    dialog.show();
                    if (dialog.isOK()) {
                        List<PerforceJob> jobs = dialog.getSelectedJobs();
                        for (PerforceJob job : jobs) {
                            VcsException vcsException = myAdderRemover.add(job, myList, myProject);
                            reportException(vcsException, PerforceLocalize.jobAddingToChangelist());
                        }
                    }
                }
            });

            group.add(new TextFieldAction(PerforceLocalize.jobFindMatchingPatternTitle(), PerforceLocalize.jobFindMatchingPattern(),
                PlatformIconGroup.generalAdd(), 20) {
                @Override
                public void perform() {
                    String text = myField.getText().trim();
                    if (!text.isEmpty()) {
                        List<PerforceJob> jobs = PerforceCheckinComponent.getJobsUnderProgress(myWorker,
                            new ByNamesConstraint(Collections.singletonList(text)), myConnection, myKey, mySpecification);
                        if (jobs == null) {
                            return;
                        }
                        if (jobs.size() > 1) {
                            Messages.showMessageDialog(myProject, PerforceLocalize.jobSeveralMatchingPattern().get(),
                                PerforceLocalize.jobAdd().get(), Messages.getInformationIcon());
                            return;
                        }
                        if (jobs.isEmpty()) {
                            Messages.showMessageDialog(myProject, PerforceLocalize.jobNoMatchingPattern().get(),
                                PerforceLocalize.jobAdd().get(), Messages.getInformationIcon());
                            return;
                        }
                        if (!reportException(myAdderRemover.add(jobs.get(0), myList, myProject), PerforceLocalize.jobAdding())) {
                            JTextField addText = myAddText;
                            if (addText != null) {
                                addText.setText("");
                            }
                        }
                    }
                }
            });

            ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar("PerforceEditChangeListJobs", group, true);
            actionToolbar.setTargetComponent(targetComponent);
            return actionToolbar.getComponent();
        }

        private final class UnlinkSelectedAction extends AnAction implements AnActionWithSyncUpdate {
            private UnlinkSelectedAction() {
                super(PerforceLocalize.jobUnlinkSelectedTitle(), PerforceLocalize.jobUnlinkSelected(), PlatformIconGroup.generalRemove());
            }

            @RequiredUIAccess
            @Override
            public void actionPerformed(AnActionEvent e) {
                for (PerforceJob job : myMainTable.getSelectedJobs()) {
                    reportException(myAdderRemover.remove(job, myList, myProject), PerforceLocalize.jobRemovingFromChangelist());
                }
            }

            @Override
            public void update(AnActionEvent e) {
                e.getPresentation().setEnabled(!myMainTable.getSelectedJobs().isEmpty());
            }
        }

        private boolean reportException(@Nullable VcsException vcsException, LocalizeValue operation) {
            if (vcsException != null) {
                new ErrorReporter(operation).report(myProject, vcsException);
                return true;
            }
            return false;
        }

        @RequiredUIAccess
        private JComponent createCenterPanel() {
            JPanel main = new JPanel(new GridBagLayout());

            GridBagConstraints gb = new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                new Insets(0, 0, 1, 0), 0, 0);

            gb.fill = GridBagConstraints.HORIZONTAL;
            gb.weightx = 1;
            main.add(createToolbar(main), gb);

            ++gb.gridy;
            gb.weightx = 1;
            gb.weighty = 1;
            gb.fill = GridBagConstraints.BOTH;
            gb.gridwidth = 2;

            main.add(myMainTable.createComponent(), gb);

            JPanel btnPanel = new JPanel(new GridBagLayout());
            GridBagConstraints panelGb = DefaultGb.create();

            myAddText = new JTextField(30);
            myAddBtn = new JButton(CommonLocalize.buttonAdd().get());
            mySearchBtn = new JButton(PerforceLocalize.search().get());
            myRemoveBtn = new JButton(CommonLocalize.buttonRemove().get());

            gb.gridwidth = 1;
            panelGb.anchor = GridBagConstraints.NORTHWEST;
            btnPanel.add(myAddText, panelGb);

            ++panelGb.gridx;
            panelGb.anchor = GridBagConstraints.CENTER;
            panelGb.fill = GridBagConstraints.NONE;
            panelGb.weightx = 0;
            btnPanel.add(myAddBtn, panelGb);

            ++panelGb.gridx;
            btnPanel.add(mySearchBtn, panelGb);

            ++panelGb.gridx;
            btnPanel.add(myRemoveBtn, panelGb);

            ++gb.gridy;
            gb.weightx = 1;
            gb.weighty = 0;
            gb.anchor = GridBagConstraints.EAST;
            gb.fill = GridBagConstraints.HORIZONTAL;

            return main;
        }

        @Override
        public void refreshJobs(@Nullable PerforceJob job) throws VcsException {
            Application application = Application.get();
            if (application.isDispatchThread()) {
                throw new IllegalStateException("Must not be called on the UI thread");
            }
            List<PerforceJob> jobs = myWorker.getJobsForList(mySpecification, myList, myConnection, myKey);
            application.invokeLater(() -> {
                myMainTable.fillTree(jobs, job);
                if (job == null) {
                    selectDefault();
                }
            });
        }

        @RequiredUIAccess
        @Override
        public void addJob(PerforceJob job) {
            myMainTable.addJob(job);
        }

        @RequiredUIAccess
        @Override
        public void removeSelectedJobs() {
            myMainTable.removeSelectedJobs();
        }

        private void selectDefault() {
            List<PerforceJob> perforceJobs = myMainTable.getJobs();
            if (!perforceJobs.isEmpty()) {
                myMainTable.selectNodeInTree(perforceJobs.get(0).getName());
            }
        }

        public List<PerforceJob> getJobs() {
            return myMainTable.getJobs();
        }
    }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.jobs;

import consulo.application.Application;
import consulo.application.progress.ProgressManager;
import consulo.localize.LocalizeValue;
import consulo.perforce.localize.PerforceLocalize;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.JBColor;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.util.collection.ArrayUtil;
import consulo.versionControlSystem.VcsException;
import org.jetbrains.idea.perforce.application.ConnectionKey;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jspecify.annotations.Nullable;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import java.awt.CardLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;

public class AddJobToChangeListDialog extends DialogWrapper {
    private static final String NO_RESULTS = "NO_RESULTS";
    private static final String FOUND_JOBS = "FOUND_JOBS";

    private final Project myProject;
    private final JobsWorker myWorker;
    private final PerforceJobSpecification mySpecification;

    private final JPanel myMainPanel;

    private final JobSearchParametersPanel mySearchParametersPanel;
    private final JobsMasterDetails myTable;
    private @Nullable List<PerforceJob> mySelectedJobs;

    private final P4Connection myConnection;
    private final ConnectionKey myKey;

    private final JPanel myResultsPanel;
    private final JComponent myTableComponent;
    private final JLabel myLimitExceededLabel;

    @RequiredUIAccess
    public AddJobToChangeListDialog(
        Project project,
        boolean canBeParent,
        JobsWorker worker,
        PerforceJobSpecification specification,
        P4Connection connection,
        ConnectionKey key
    ) {
        super(project, canBeParent);
        myProject = project;
        myWorker = worker;
        mySpecification = specification;
        myConnection = connection;
        myKey = key;

        myMainPanel = new JPanel(new GridBagLayout());
        mySearchParametersPanel = new JobSearchParametersPanel();
        myTable = new JobsMasterDetails(myProject);
        myResultsPanel = new JPanel(new CardLayout());
        myTableComponent = myTable.createComponent();
        myLimitExceededLabel = new JLabel();
        createUI();
        setTitle(PerforceLocalize.jobLinkToChangelist());

        init();
        setOKActionEnabled(myTableComponent.isVisible() && !myTable.getSelectedJobs().isEmpty());
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return mySearchParametersPanel.getPreferredFocusTarget();
    }

    @Override
    protected @Nullable String getHelpId() {
        return "reference.vcs.perforce.searchJob";
    }

    @Override
    protected @Nullable String getDimensionServiceKey() {
        return "org.jetbrains.idea.perforce.perforce.jobs.AddJobToChangeListDialog";
    }

    private void createUI() {
        GridBagConstraints gb = DefaultGb.create();

        ++gb.gridy;
        gb.gridx = 0;
        gb.anchor = GridBagConstraints.WEST;
        JLabel paramsLabel = new JLabel(PerforceLocalize.specifySearchParams().get());
        paramsLabel.setFont(paramsLabel.getFont().deriveFont(Font.BOLD));
        Insets insets = gb.insets;
        gb.insets = new Insets(2, 2, 7, 2);
        myMainPanel.add(paramsLabel, gb);
        gb.insets = insets;

        ++gb.gridy;
        myMainPanel.add(mySearchParametersPanel.getComponent(), gb);

        ++gb.gridx;
        gb.anchor = GridBagConstraints.NORTHWEST;
        JButton searchBtn = new JButton(PerforceLocalize.search().get());
        myMainPanel.add(searchBtn, gb);

        JPanel noResultsPanel = new JPanel(new GridBagLayout());
        GridBagConstraints noResultsGb = DefaultGb.create();
        noResultsGb.anchor = GridBagConstraints.CENTER;
        noResultsPanel.add(new JLabel(PerforceLocalize.nothingFound().get()), noResultsGb);

        myResultsPanel.add(noResultsPanel, NO_RESULTS);
        myResultsPanel.add(myTableComponent, FOUND_JOBS);
        ((CardLayout)myResultsPanel.getLayout()).show(myResultsPanel, NO_RESULTS);

        gb.gridx = 0;
        ++gb.gridy;
        gb.anchor = GridBagConstraints.WEST;
        JLabel resultsLabel = new JLabel(PerforceLocalize.searchResults().get());
        gb.insets = new Insets(7, 2, 2, 2);
        resultsLabel.setFont(resultsLabel.getFont().deriveFont(Font.BOLD));
        myMainPanel.add(resultsLabel, gb);

        myLimitExceededLabel.setForeground(JBColor.RED);
        ++gb.gridx;
        gb.insets = new Insets(2, 2, 2, 2);
        myMainPanel.add(myLimitExceededLabel, gb);

        gb.gridx = 0;
        gb.gridwidth = 2;
        ++gb.gridy;
        gb.fill = GridBagConstraints.BOTH;
        gb.weightx = 1;
        gb.weighty = 1;
        gb.anchor = GridBagConstraints.CENTER;
        myMainPanel.add(myResultsPanel, gb);

        searchBtn.setMnemonic(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.ALT_DOWN_MASK).getKeyCode());

        searchBtn.addActionListener(e -> {
            JobsSearchSpecificator specificator = mySearchParametersPanel.createSpecificator();
            ProgressManager.getInstance().runProcessWithProgressSynchronously(
                () -> {
                    try {
                        List<PerforceJob> jobs = myWorker.getJobs(mySpecification, specificator, myConnection, myKey);
                        Application.get().invokeLater(() -> {
                            myLimitExceededLabel.setText("");
                            if (!jobs.isEmpty()) {
                                ((CardLayout)myResultsPanel.getLayout()).show(myResultsPanel, FOUND_JOBS);
                            }
                            else {
                                ((CardLayout)myResultsPanel.getLayout()).show(myResultsPanel, NO_RESULTS);
                            }
                            int maxCount = specificator.getMaxCount();
                            List<PerforceJob> toShow = jobs;
                            if ((maxCount > 0) && (maxCount < jobs.size())) {
                                toShow = jobs.subList(0, maxCount);
                                myLimitExceededLabel.setText(PerforceLocalize.perforceJobsSearchLimitExceededWarning(maxCount).get());
                            }
                            myTable.fillTree(toShow, toShow.isEmpty() ? null : toShow.get(0));
                        });
                    }
                    catch (VcsException e1) {
                        String[] params = specificator.addParams(ArrayUtil.EMPTY_STRING_ARRAY);
                        StringBuilder sb = new StringBuilder(PerforceLocalize.jobSearchingForJobs().get()).append(' ');
                        for (String param : params) {
                            sb.append(param).append(' ');
                        }
                        sb.append("' ");
                        new ErrorReporter(LocalizeValue.of(sb.toString())).report(myProject, e1);
                    }
                },
                PerforceLocalize.jobSearchingJobs(),
                false,
                myProject
            );
        });

        myTable.setSelectionListener(e -> setOKActionEnabled(myTableComponent.isVisible() && !myTable.getSelectedJobs().isEmpty()));
    }

    @Override
    protected void doOKAction() {
        List<PerforceJob> selectedJobs = myTable.getSelectedJobs();
        mySelectedJobs = selectedJobs;
        if (!selectedJobs.isEmpty()) {
            super.doOKAction();
        }
    }

    public List<PerforceJob> getSelectedJobs() {
        List<PerforceJob> selectedJobs = mySelectedJobs;
        return selectedJobs == null ? List.of() : selectedJobs;
    }

    @Override
    protected JComponent createCenterPanel() {
        return myMainPanel;
    }
}

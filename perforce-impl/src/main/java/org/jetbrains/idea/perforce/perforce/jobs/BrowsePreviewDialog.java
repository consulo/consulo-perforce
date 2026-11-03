// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.jobs;

import consulo.perforce.localize.PerforceLocalize;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.JBColor;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.JBUI;
import org.jspecify.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;

public class BrowsePreviewDialog extends DialogWrapper {
    private final Project myProject;
    private final String myJobViewQueryString;
    private final List<PerforceJob> myJobs;
    private final int myMaxCount;

    private final JPanel myMainPanel;

    private final JobsMasterDetails myTable;

    @RequiredUIAccess
    public BrowsePreviewDialog(Project project, String jobViewQueryString, List<PerforceJob> jobs, int maxCount) {
        super(project, true);
        myProject = project;
        myJobViewQueryString = jobViewQueryString;
        myJobs = jobs;
        myMaxCount = maxCount;

        myMainPanel = new JPanel(new GridBagLayout());
        myTable = new JobsMasterDetails(myProject);
        createUI();
        setTitle(PerforceLocalize.dialogTitleLinkJobToChangelist());

        init();
        setOKActionEnabled(!myTable.getSelectedJobs().isEmpty());
    }

    @Override
    protected @Nullable String getDimensionServiceKey() {
        return "org.jetbrains.idea.perforce.perforce.jobs.BrowsePreviewDialog";
    }

    @RequiredUIAccess
    private void createUI() {
        GridBagConstraints gb = DefaultGb.create();

        gb.gridx = 0;
        gb.anchor = GridBagConstraints.WEST;
        JLabel jobViewTitleLabel = new JLabel(PerforceLocalize.jobViewQuery().get());
        jobViewTitleLabel.setFont(jobViewTitleLabel.getFont().deriveFont(Font.BOLD));
        Insets insets = gb.insets;
        gb.insets = JBUI.insets(2, 2, 7, 2);
        myMainPanel.add(jobViewTitleLabel, gb);

        ++gb.gridx;
        JLabel jobView = new JLabel(myJobViewQueryString);
        myMainPanel.add(jobView, gb);

        gb.insets = insets;

        myTable.fillTree(myJobs, null);

        if (myJobs.size() > myMaxCount) {
            JLabel limitExceededLabel = new JLabel(PerforceLocalize.perforceJobsSearchLimitExceededWarning(myMaxCount).get());
            limitExceededLabel.setForeground(JBColor.RED);
            ++gb.gridy;
            gb.gridx = 0;
            gb.gridwidth = 2;
            gb.insets = JBUI.insets(2);
            myMainPanel.add(limitExceededLabel, gb);
        }

        gb.gridx = 0;
        gb.gridwidth = 2;
        ++gb.gridy;
        gb.fill = GridBagConstraints.BOTH;
        gb.weightx = 1;
        gb.weighty = 1;
        gb.anchor = GridBagConstraints.CENTER;
        myMainPanel.add(myTable.createComponent(), gb);

        myTable.setSelectionListener(e -> setOKActionEnabled(!myTable.getSelectedJobs().isEmpty()));
    }

    @Override
    protected void doOKAction() {
        if (!myTable.getSelectedJobs().isEmpty()) {
            super.doOKAction();
        }
    }

    @Override
    protected JComponent createCenterPanel() {
        return myMainPanel;
    }
}

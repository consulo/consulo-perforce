// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.jobs;

import consulo.application.Application;
import consulo.perforce.localize.PerforceLocalize;
import consulo.platform.base.localize.CommonLocalize;
import consulo.project.Project;
import consulo.ui.ModalityState;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.GridBag;
import consulo.ui.ex.awt.UIUtil;
import consulo.util.lang.Pair;
import consulo.versionControlSystem.VcsException;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.ArrayList;
import java.util.List;

class SelfLoadingJobDetailsPanel {
    private final JPanel myMain;
    private final Project myProject;
    private final PerforceJob myJob;
    private final List<Pair<String, String>> myDetails;
    private final JPanel myPanel;

    SelfLoadingJobDetailsPanel(Project project, PerforceJob job) {
        myProject = project;
        myJob = job;
        myDetails = new ArrayList<>();
        myPanel = new JPanel(new BorderLayout());
        myMain = new JPanel(new GridBagLayout());
        createDetailsPanel();
    }

    private void initData() {
        Application application = Application.get();
        application.executeOnPooledThread(() -> {
            if (load()) {
                application.invokeLater(this::displayJobDetails, ModalityState.any());
            }
        });
    }

    @RequiredUIAccess
    private void displayJobDetails() {
        myMain.removeAll();

        GridBag gb = new GridBag();
        for (Pair<String, String> detail : myDetails) {
            JTextArea text = new JTextArea(detail.getSecond().trim());
            text.setEditable(false);
            text.setBackground(UIUtil.getBgFillColor(myMain));

            gb.nextLine();
            myMain.add(new JLabel(detail.getFirst() + ":"), gb.next().fillCellNone().weightx(0));
            myMain.add(text, gb.next().fillCellHorizontally().weightx(1));
        }

        myMain.add(new JPanel(), gb.nextLine().next().coverLine(2).weighty(1));

        myPanel.revalidate();
        myPanel.repaint();
    }

    private void createDetailsPanel() {
        GridBagConstraints gb = DefaultGb.create();
        gb.anchor = GridBagConstraints.CENTER;
        gb.fill = GridBagConstraints.BOTH;
        gb.weightx = gb.weighty = 1;
        myMain.add(new JLabel(CommonLocalize.treeNodeLoading().get()), gb);

        myPanel.add(myMain, BorderLayout.NORTH);

        initData();
    }

    private boolean load() {
        try {
            List<Pair<String, String>> items = new JobDetailsLoader(myProject).load(myJob);
            // remove name
            PerforceJobFieldValue nameValue = myJob.getNameValue();
            if (nameValue != null) {
                String fieldName = nameValue.getField().getName();
                String name = nameValue.getValue();
                items.remove(Pair.create(fieldName, name));
            }

            myDetails.addAll(items);
        }
        catch (VcsException e) {
            new ErrorReporter(PerforceLocalize.jobLoadingDetails()).report(myProject, e);
            return false;
        }
        return true;
    }

    public JPanel getPanel() {
        return myPanel;
    }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.jobs;

import consulo.perforce.localize.PerforceLocalize;
import consulo.ui.ex.awt.UIUtil;
import consulo.util.collection.ArrayUtil;
import org.jspecify.annotations.Nullable;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.List;

public class JobSearchParametersPanel {
    private final JPanel myPanel;
    private final JTextField myJobName;
    private final JTextField myUser;
    private final JTextField myDateBefore;
    private final JTextField myDateAfter;
    private final JTextField myDescription;
    private final StatusComboBox myStatus;

    public JobSearchParametersPanel() {
        myPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gb = DefaultGb.create();
        gb.fill = GridBagConstraints.HORIZONTAL;
        gb.anchor = GridBagConstraints.WEST;

        myPanel.add(new JLabel(PerforceLocalize.jobSearchNamePattern().get()), gb);
        gb.gridx = 1;
        myJobName = new JTextField();
        myPanel.add(myJobName, gb);

        ++gb.gridy;
        gb.gridx = 0;

        myPanel.add(new JLabel(PerforceLocalize.jobSearchStatus().get()), gb);
        gb.gridx = 1;
        myStatus = new StatusComboBox();
        myPanel.add(myStatus, gb);

        ++gb.gridy;
        gb.gridx = 0;
        myPanel.add(new JLabel(PerforceLocalize.jobSearchUserPattern().get()), gb);
        gb.gridx = 1;
        myUser = new JTextField();
        myPanel.add(myUser, gb);

        ++gb.gridy;
        gb.gridx = 0;
        myPanel.add(new JLabel(PerforceLocalize.jobSearchDateBefore().get()), gb);
        gb.gridx = 1;
        myDateBefore = new JTextField();
        myPanel.add(myDateBefore, gb);

        ++gb.gridy;
        gb.gridx = 1;
        JLabel formatLabel = new JLabel(PerforceLocalize.jobSearchDateFormat().get());
        formatLabel.setForeground(UIUtil.getInactiveTextColor());
        myPanel.add(formatLabel, gb);

        ++gb.gridy;
        gb.gridx = 0;
        myPanel.add(new JLabel(PerforceLocalize.jobSearchDateAfter().get()), gb);
        gb.gridx = 1;
        myDateAfter = new JTextField();
        myPanel.add(myDateAfter, gb);

        ++gb.gridy;
        gb.gridx = 1;
        JLabel formatLabel2 = new JLabel(PerforceLocalize.jobSearchDateFormat().get());
        formatLabel2.setForeground(UIUtil.getInactiveTextColor());
        myPanel.add(formatLabel2, gb);

        ++gb.gridy;
        gb.gridx = 0;
        myPanel.add(new JLabel(PerforceLocalize.jobSearchDescription().get()), gb);
        gb.gridx = 1;
        myDescription = new JTextField();
        myPanel.add(myDescription, gb);

        /*myFreeFields = new ArrayList<AnyFieldGroup>();
        for (int i = 0; i < 4; i++) {
          final AnyFieldGroup field = new AnyFieldGroup(freeFields);
          myFreeFields.add(field);
          field.addSelf(myPanel, gb);
        }*/
    }

    public JComponent getPreferredFocusTarget() {
        return myJobName;
    }

    private static boolean notEmpty(JTextField f) {
        String t = f.getText();
        return t != null && !t.trim().isEmpty();
    }

    public JobsSearchSpecificator createSpecificator() {
        FullSearchSpecificator result = new FullSearchSpecificator();
        if (notEmpty(myJobName)) {
            result.addStandardConstraint(FullSearchSpecificator.Parts.jobname, myJobName.getText().trim());
        }
        if (notEmpty(myUser)) {
            result.addStandardConstraint(FullSearchSpecificator.Parts.user, myUser.getText().trim());
        }
        String status = myStatus.getSelectedItem();
        if (status != null && !"*".equals(status)) {
            result.addStandardConstraint(FullSearchSpecificator.Parts.status, status.trim());
        }
        if (notEmpty(myDateBefore)) {
            result.addStandardConstraint(FullSearchSpecificator.Parts.dateBefore, myDateBefore.getText().trim());
        }
        if (notEmpty(myDateAfter)) {
            result.addStandardConstraint(FullSearchSpecificator.Parts.dateAfter, myDateAfter.getText().trim());
        }
        if (notEmpty(myDescription)) {
            result.addStandardConstraint(FullSearchSpecificator.Parts.description, myDescription.getText().trim());
        }
        /*for (AnyFieldGroup field : myFreeFields) {
          if (notEmpty(field.myText)) {
            result.addOtherFieldConstraint((String) field.myNameField.getSelectedItem(), field.mySign.getSelectedItem(), field.getValue());
          }
        }*/
        return result;
    }

    @SuppressWarnings("unused")
    private static final class AnyFieldGroup {
        private final JComboBox<String> myNameField;
        private final SignComboBox mySign;
        private final JTextField myText;

        private AnyFieldGroup(List<String> freeFields) {
            myNameField = new JComboBox<>(ArrayUtil.toStringArray(freeFields));
            myNameField.setEditable(false);
            mySign = new SignComboBox();
            myText = new JTextField();
        }

        public void addSelf(JPanel main, GridBagConstraints gb) {
            gb.gridx = 0;
            ++gb.gridy;
            main.add(myNameField, gb);
            ++gb.gridx;
            gb.fill = GridBagConstraints.NONE;
            main.add(mySign, gb);
            gb.fill = GridBagConstraints.HORIZONTAL;
            ++gb.gridx;
            main.add(myText, gb);
        }

        public void enable(boolean value) {
            myNameField.setEnabled(value);
            mySign.setEnabled(value);
            myText.setEnabled(value);
        }

        public String getValue() {
            return myText.getText();
        }
    }

    public JPanel getComponent() {
        return myPanel;
    }

    private static final class StatusComboBox extends JComboBox<String> {
        private StatusComboBox() {
            super(new String[]{"*", "open", "closed", "suspended"});
            setSelectedIndex(0);
            setEditable(false);
        }

        @Override
        public @Nullable String getSelectedItem() {
            return (String)super.getSelectedItem();
        }
    }

    private static final class SignComboBox extends JComboBox<String> {
        private SignComboBox() {
            super(new String[]{"=", "<", ">", "<=", ">="});
            setSelectedIndex(0);
            setEditable(false);
        }

        @Override
        public @Nullable String getSelectedItem() {
            return (String)super.getSelectedItem();
        }
    }
}

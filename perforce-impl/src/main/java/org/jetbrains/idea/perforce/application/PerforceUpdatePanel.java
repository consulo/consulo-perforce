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
import consulo.localize.LocalizeValue;
import consulo.perforce.localize.PerforceLocalize;
import org.jetbrains.idea.perforce.perforce.PerforceSettings;

import javax.swing.AbstractButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.Insets;

public class PerforceUpdatePanel implements PerforcePanel {
    private final JCheckBox myForceSync;
    private final JCheckBox myAutomaticallyResolve;
    private final JPanel myPanel;
    private final JCheckBox myRevertUnchanged;

    public PerforceUpdatePanel() {
        myPanel = new JPanel();
        myPanel.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
        Spacer spacer1 = new Spacer();
        myPanel.add(spacer1, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
            GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        Spacer spacer2 = new Spacer();
        myPanel.add(spacer2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
            GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(3, 1, new Insets(0, 0, 0, 0), -1, -1));
        myPanel.add(panel1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null,
            0, false));
        myRevertUnchanged = new JCheckBox();
        loadButtonText(myRevertUnchanged, PerforceLocalize.checkboxUpdateOptionsRevertUnchangedFilesBeforeSync());
        panel1.add(myRevertUnchanged, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myForceSync = new JCheckBox();
        loadButtonText(myForceSync, PerforceLocalize.checkboxDoForceSync());
        panel1.add(myForceSync, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myAutomaticallyResolve = new JCheckBox();
        loadButtonText(myAutomaticallyResolve, PerforceLocalize.checkboxRunResolveAfterSync());
        panel1.add(myAutomaticallyResolve, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }

    static void loadButtonText(AbstractButton component, LocalizeValue value) {
        String text = value.get();
        StringBuilder result = new StringBuilder();
        boolean haveMnemonic = false;
        char mnemonic = '\0';
        int mnemonicIndex = -1;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '&' || ch == '_') {
                i++;
                if (i == text.length()) {
                    break;
                }
                if (!haveMnemonic && text.charAt(i) != ch) {
                    haveMnemonic = true;
                    mnemonic = text.charAt(i);
                    mnemonicIndex = result.length();
                }
            }
            result.append(text.charAt(i));
        }
        component.setText(result.toString());
        if (haveMnemonic) {
            component.setMnemonic(mnemonic);
            component.setDisplayedMnemonicIndex(mnemonicIndex);
        }
    }

    public JComponent getRootComponent() {
        return myPanel;
    }

    @Override
    public void updateFrom(PerforceSettings settings) {
        myForceSync.setSelected(settings.SYNC_FORCE);
        myAutomaticallyResolve.setSelected(settings.SYNC_RUN_RESOLVE);
        myRevertUnchanged.setSelected(settings.REVERT_UNCHANGED_FILES);
    }

    @Override
    public void applyTo(PerforceSettings settings) throws ConfigurationException {
        settings.SYNC_FORCE = myForceSync.isSelected();
        settings.SYNC_RUN_RESOLVE = myAutomaticallyResolve.isSelected();
        settings.REVERT_UNCHANGED_FILES = myRevertUnchanged.isSelected();
    }

    @Override
    public boolean isModified(PerforceSettings settings) {
        if (myAutomaticallyResolve.isSelected() != settings.SYNC_RUN_RESOLVE) {
            return true;
        }

        if (myRevertUnchanged.isSelected() != settings.REVERT_UNCHANGED_FILES) {
            return true;
        }

        if (myForceSync.isSelected() != settings.SYNC_FORCE) {
            return true;
        }

        return false;
    }

    @Override
    public JPanel getPanel() {
        return myPanel;
    }
}

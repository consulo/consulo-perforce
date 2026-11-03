// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.changesBrowser;

import consulo.ui.annotation.RequiredUIAccess;
import consulo.versionControlSystem.versionBrowser.ui.awt.StandardVersionFilterComponent;

import javax.swing.JComponent;
import javax.swing.JPanel;

public class PerforceOnlyDatesVersionFilterComponent extends StandardVersionFilterComponent<PerforceChangeBrowserSettings> {
    @RequiredUIAccess
    public PerforceOnlyDatesVersionFilterComponent() {
        super(true);
        disableVersionNumbers();
    }

    @Override
    public JComponent getComponent() {
        return (JPanel) getStandardPanel();
    }
}

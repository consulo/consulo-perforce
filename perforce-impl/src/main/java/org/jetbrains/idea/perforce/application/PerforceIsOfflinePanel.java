// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.application;

import consulo.configurable.ConfigurationException;
import consulo.perforce.localize.PerforceLocalize;
import org.jetbrains.idea.perforce.perforce.PerforceSettings;

import java.awt.BorderLayout;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class PerforceIsOfflinePanel implements PerforcePanel {
  @Override
  public void updateFrom(PerforceSettings settings) {
  }

  @Override
  public void applyTo(PerforceSettings settings) throws ConfigurationException {
  }

  @Override
  public boolean isModified(PerforceSettings settings) {
    return false;
  }

  @Override
  public JPanel getPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    JLabel label = new JLabel(PerforceLocalize.connectionOffline().get());
    label.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
    panel.add(label, BorderLayout.CENTER);
    return panel;
  }
}

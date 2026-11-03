// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.application;

import consulo.perforce.localize.PerforceLocalize;
import consulo.project.Project;
import consulo.ui.ex.JBColor;
import consulo.ui.ex.awt.HyperlinkLabel;
import org.jetbrains.idea.perforce.perforce.PerforceSettings;
import org.jspecify.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.event.HyperlinkEvent;

/**
 * Status component for the Local Changes view: shows that Perforce is offline, with a link to go online.
 * <p>
 * The platform has no extension point to contribute such a component to the changes view, so this class is not registered.
 */
final class PerforceChangesOfflineStatusProvider {
  public @Nullable JComponent getStatusComponent(Project project) {
    PerforceSettings settings = PerforceVcs.getInstance(project).getSettings();
    if (settings.ENABLED) {
      return null;
    }

    HyperlinkLabel label = new HyperlinkLabel();
    label.setForeground(JBColor.RED);
    label.setHyperlinkText(PerforceLocalize.connectionOffline().get() + ' ', PerforceLocalize.connectionGoOnline().get(), "");
    label.addHyperlinkListener(e -> {
      if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
        if (!settings.ENABLED) {
          settings.enable();
        }
      }
    });
    return label;
  }
}

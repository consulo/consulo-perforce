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

import consulo.configurable.Configurable;
import consulo.configurable.ConfigurationException;
import consulo.disposer.Disposable;
import consulo.localize.LocalizeValue;
import consulo.perforce.localize.PerforceLocalize;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import org.jetbrains.idea.perforce.perforce.PerforceSettings;
import org.jspecify.annotations.Nullable;

public abstract class PerforceUpdateConfigurable implements Configurable {
    private final PerforceSettings mySettings;
    private @Nullable PerforcePanel myUpdatePanel;

    public PerforceUpdateConfigurable(PerforceSettings settings) {
        mySettings = settings;
    }

    @Override
    public LocalizeValue getDisplayName() {
        return PerforceLocalize.configurableTitlePerforceSyncSettings();
    }

    @Override
    public @Nullable String getHelpTopic() {
        return null;
    }

    @RequiredUIAccess
    @Override
    public Component createUIComponent(Disposable parentDisposable) {
        PerforcePanel panel = createPanel();
        myUpdatePanel = panel;
        return TargetAWT.wrap(panel.getPanel());
    }

    protected abstract PerforcePanel createPanel();

    @RequiredUIAccess
    @Override
    public boolean isModified() {
        PerforcePanel panel = myUpdatePanel;
        return panel != null && panel.isModified(mySettings);
    }

    @RequiredUIAccess
    @Override
    public void apply() throws ConfigurationException {
        PerforcePanel panel = myUpdatePanel;
        if (panel != null) {
            panel.applyTo(mySettings);
        }
    }

    /**
     * Gives the panel a chance to keep the values it wants to remember even when the dialog is not applied.
     * Called when the UI is disposed: after an apply the panel is not modified, so the call is a no-op then.
     */
    public void cancel() {
        PerforcePanel panel = myUpdatePanel;
        if (panel != null) {
            panel.cancel(mySettings);
        }
    }

    @RequiredUIAccess
    @Override
    public void reset() {
        PerforcePanel panel = myUpdatePanel;
        if (panel != null) {
            panel.updateFrom(mySettings);
        }
    }

    @RequiredUIAccess
    @Override
    public void disposeUIResources() {
        cancel();
        myUpdatePanel = null;
    }
}

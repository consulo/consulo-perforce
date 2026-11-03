// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce;

import consulo.annotation.component.ExtensionImpl;
import consulo.configurable.ConfigurationException;
import consulo.configurable.ProjectConfigurable;
import consulo.configurable.SimpleConfigurableByProperties;
import consulo.configurable.StandardConfigurableIds;
import consulo.disposer.Disposable;
import consulo.localize.LocalizeValue;
import consulo.perforce.localize.PerforceLocalize;
import consulo.project.Project;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

/**
 * "Version Control | Perforce" settings page.
 */
@ExtensionImpl
public final class PerforceConfigurable extends SimpleConfigurableByProperties implements ProjectConfigurable {
    public static final String ID = "vcs.Perforce";

    private final Project myProject;
    private @Nullable PerforceConfigPanel myPanel;

    @Inject
    public PerforceConfigurable(Project project) {
        myProject = project;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public @Nullable String getParentId() {
        return StandardConfigurableIds.VCS_GROUP;
    }

    @Override
    public LocalizeValue getDisplayName() {
        return PerforceLocalize.settingsPerforceOptionGroup();
    }

    @Override
    public @Nullable String getHelpTopic() {
        return "project.propVCSSupport.VCSs.Perforce";
    }

    @RequiredUIAccess
    @Override
    protected Component createLayout(PropertyBuilder propertyBuilder, Disposable uiDisposable) {
        PerforceConfigPanel panel = new PerforceConfigPanel(myProject, uiDisposable);
        myPanel = panel;
        return panel.createPanel(propertyBuilder);
    }

    @RequiredUIAccess
    @Override
    protected void reset(LayoutWrapper component) {
        super.reset(component);
        PerforceConfigPanel panel = myPanel;
        if (panel != null) {
            panel.updateEnv(false);
        }
    }

    @RequiredUIAccess
    @Override
    protected void apply(LayoutWrapper component) throws ConfigurationException {
        super.apply(component);
        PerforceConfigPanel panel = myPanel;
        if (panel != null) {
            panel.updateEnv(true);
        }
    }

    @RequiredUIAccess
    @Override
    protected void disposeUIResources(LayoutWrapper component) {
        super.disposeUIResources(component);
        myPanel = null;
    }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce;

import consulo.annotation.component.ExtensionImpl;
import consulo.configurable.ConfigurationException;
import consulo.configurable.ProjectConfigurable;
import consulo.configurable.SimpleConfigurableByProperties;
import consulo.configurable.StandardConfigurableIds;
import consulo.configurable.search.AfterOptionChange;
import consulo.disposer.Disposable;
import consulo.localize.LocalizeValue;
import consulo.perforce.localize.PerforceLocalize;
import consulo.project.Project;
import consulo.ui.CheckBox;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.layout.VerticalLayout;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

/**
 * "Version Control | Perforce" settings page. Its check boxes are bound to the settings with method references, so
 * they are switched from Find Action too - keep them so.
 */
@ExtensionImpl
public final class PerforceConfigurable extends SimpleConfigurableByProperties implements ProjectConfigurable {
    public static final String ID = "vcs.Perforce";

    private final Project myProject;
    private final PerforceSettings mySettings;
    private @Nullable PerforceConfigPanel myPanel;

    @Inject
    public PerforceConfigurable(Project project, PerforceSettings settings) {
        myProject = project;
        mySettings = settings;
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
        PerforceSettings settings = mySettings;
        VerticalLayout root = VerticalLayout.create();

        if (!myProject.isDefault()) {
            CheckBox enabled = CheckBox.create(PerforceLocalize.checkboxConfigurePerforceIsEnabled());
            propertyBuilder.add(enabled, settings::isEnabled, settings::setEnabled);
            root.add(enabled);
        }

        CheckBox canGoOffline = CheckBox.create(PerforceLocalize.checkboxSwitchOffline());
        propertyBuilder.add(canGoOffline, settings::isCanGoOffline, settings::setCanGoOffline);
        root.add(canGoOffline);

        CheckBox showCmds = CheckBox.create(PerforceLocalize.checkboxConfigurePerforceLogCommands());
        propertyBuilder.add(showCmds, settings::isShowCmds, settings::setShowCmds);
        CheckBox useLogin = CheckBox.create(PerforceLocalize.checkboxConfigurePerforceUseLoginAuthentication());
        propertyBuilder.add(useLogin, settings::isUseLogin, settings::setUseLogin);

        PerforceConfigPanel panel = new PerforceConfigPanel(myProject, uiDisposable, showCmds, useLogin);
        myPanel = panel;
        panel.addConnectionSettings(root, propertyBuilder);

        root.add(showCmds);
        root.add(panel.createDumpFileRow());
        root.add(panel.createLoginRow());
        panel.addExecutables(root, propertyBuilder);

        CheckBox showBranches = CheckBox.create(PerforceLocalize.checkboxConfigurePerforceShowBranchingHistory());
        propertyBuilder.add(showBranches, settings::isShowBranchesHistory, settings::setShowBranchesHistory);
        root.add(showBranches);

        CheckBox showIntegrated = CheckBox.create(PerforceLocalize.checkboxConfigurePerforceShowIntegratedChangelists());
        propertyBuilder.add(showIntegrated, settings::isShowIntegratedInCommittedChanges, settings::setShowIntegratedInCommittedChanges);
        root.add(showIntegrated);

        root.add(panel.createServerTimeoutRow(propertyBuilder));

        CheckBox useJobs = CheckBox.create(PerforceLocalize.perforceUsePerforceJobs());
        propertyBuilder.add(useJobs, settings::isUsePerforceJobs, settings::setUsePerforceJobs);
        root.add(useJobs);

        CheckBox useP4ForIgnore = CheckBox.create(PerforceLocalize.labelConfigurePerforceUseP4ForIgnore());
        propertyBuilder.add(useP4ForIgnore, settings::isUseP4ForIgnore, settings::setUseP4ForIgnore);
        root.add(useP4ForIgnore);

        CheckBox forceSync = CheckBox.create(PerforceLocalize.labelConfigurePerforceForcefullySyncChangelists());
        propertyBuilder.add(forceSync, settings::isForceSyncChangelists, settings::setForceSyncChangelists);
        root.add(forceSync);

        return root;
    }

    /**
     * An option switched from Find Action needs nothing more - {@link #apply} re-reads the P4 environment for the
     * connection settings only.
     */
    @AfterOptionChange
    static void optionChanged() {
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

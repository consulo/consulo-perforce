// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce;

import consulo.annotation.component.ExtensionImpl;
import consulo.component.ComponentManager;
import consulo.localize.LocalizeValue;
import consulo.perforce.localize.PerforceLocalize;
import consulo.project.Project;
import consulo.ui.ex.action.BooleanOptionDescription;
import consulo.ui.ex.action.OptionsTopHitProvider;
import consulo.versionControlSystem.ProjectLevelVcsManager;
import consulo.versionControlSystem.change.VcsDirtyScopeManager;
import org.jetbrains.idea.perforce.application.PerforceVcs;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Boolean Perforce settings available from "Search Everywhere" / "Find Action".
 */
@ExtensionImpl
public final class PerforceOptionsTopHitProvider extends OptionsTopHitProvider {
    @Override
    public String getId() {
        return "vcs";
    }

    @Override
    public Collection<BooleanOptionDescription> getOptions(@Nullable ComponentManager componentManager) {
        if (!(componentManager instanceof Project project) || !isEnabled(project)) {
            return List.of();
        }

        PerforceSettings settings = PerforceSettings.getSettings(project);
        return List.of(
            option(PerforceLocalize.checkboxConfigurePerforceIsEnabled(), () -> settings.ENABLED, enabled -> {
                settings.ENABLED = enabled;
                if (enabled) {
                    settings.enable();
                }
                else {
                    settings.disable(true);
                    VcsDirtyScopeManager.getInstance(project).markEverythingDirty();
                }
            }),
            option(PerforceLocalize.checkboxSwitchOffline(), () -> settings.myCanGoOffline, v -> settings.myCanGoOffline = v),
            option(PerforceLocalize.checkboxTogglePerforceLogCommands(), () -> settings.showCmds, v -> settings.showCmds = v),
            option(PerforceLocalize.checkboxConfigurePerforceUseLoginAuthentication(), () -> settings.USE_LOGIN, v -> settings.USE_LOGIN = v),
            option(PerforceLocalize.checkboxConfigurePerforceShowBranchingHistory(),
                () -> settings.SHOW_BRANCHES_HISTORY, v -> settings.SHOW_BRANCHES_HISTORY = v),
            option(PerforceLocalize.checkboxConfigurePerforceShowIntegratedChangelists(),
                () -> settings.SHOW_INTEGRATED_IN_COMMITTED_CHANGES, v -> settings.SHOW_INTEGRATED_IN_COMMITTED_CHANGES = v),
            option(PerforceLocalize.perforceUsePerforceJobs(), () -> settings.USE_PERFORCE_JOBS, v -> settings.USE_PERFORCE_JOBS = v)
        );
    }

    private static boolean isEnabled(Project project) {
        return !project.isDefault() && ProjectLevelVcsManager.getInstance(project).checkVcsIsActive(PerforceVcs.getKey().getName());
    }

    private static BooleanOptionDescription option(LocalizeValue text, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        String option = PerforceLocalize.settingsP4OptionGroup().get() + ": " + HTML_STRIP.apply(text.get());
        return new BooleanOptionDescription(option, PerforceConfigurable.ID) {
            @Override
            public boolean isOptionEnabled() {
                return getter.get();
            }

            @Override
            public void setOptionState(boolean enabled) {
                setter.accept(enabled);
            }
        };
    }
}

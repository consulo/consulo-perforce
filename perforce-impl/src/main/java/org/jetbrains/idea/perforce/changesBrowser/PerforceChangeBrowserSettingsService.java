// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.changesBrowser;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import consulo.versionControlSystem.versionBrowser.ChangeBrowserSettings;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

@Singleton
@State(name = "RepositoryTabFilterSettings", storages = @Storage("repositoryTabFilterSettings.xml"))
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public final class PerforceChangeBrowserSettingsService
    implements PersistentStateComponent<PerforceChangeBrowserSettingsService.SettingsState> {

    public static final class SettingsState {
        public boolean useClientFilter = false;
        public String client = "";
        public String changeAfter = "";
        public String changeBefore = "";
        public @Nullable String dateAfter = "";
        public @Nullable String dateBefore = "";
        public String user = "";
        public boolean useChangeAfterFilter = false;
        public boolean useChangeBeforeFilter = false;
        public boolean useDateAfterFilter = false;
        public boolean useDateBeforeFilter = false;
        public boolean useUserFilter = false;
        public boolean stopOnCopy = false;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof SettingsState that)) {
                return false;
            }
            return useClientFilter == that.useClientFilter &&
                useChangeAfterFilter == that.useChangeAfterFilter &&
                useChangeBeforeFilter == that.useChangeBeforeFilter &&
                useDateAfterFilter == that.useDateAfterFilter &&
                useDateBeforeFilter == that.useDateBeforeFilter &&
                useUserFilter == that.useUserFilter &&
                stopOnCopy == that.stopOnCopy &&
                client.equals(that.client) &&
                changeAfter.equals(that.changeAfter) &&
                changeBefore.equals(that.changeBefore) &&
                Objects.equals(dateAfter, that.dateAfter) &&
                Objects.equals(dateBefore, that.dateBefore) &&
                user.equals(that.user);
        }

        @Override
        public int hashCode() {
            return Objects.hash(useClientFilter, client, changeAfter, changeBefore, dateAfter, dateBefore, user, useChangeAfterFilter,
                useChangeBeforeFilter, useDateAfterFilter, useDateBeforeFilter, useUserFilter, stopOnCopy);
        }
    }

    private SettingsState myState = new SettingsState();

    public static PerforceChangeBrowserSettingsService getInstance(Project project) {
        return project.getInstance(PerforceChangeBrowserSettingsService.class);
    }

    public PerforceChangeBrowserSettings getSettings() {
        return stateToSettings();
    }

    @Override
    public SettingsState getState() {
        return myState;
    }

    @Override
    public void loadState(SettingsState state) {
        myState = state;
    }

    public void saveSettings(ChangeBrowserSettings settings) {
        SettingsState state = new SettingsState();
        state.changeAfter = settings.CHANGE_AFTER;
        state.changeBefore = settings.CHANGE_BEFORE;
        state.dateAfter = settings.DATE_AFTER;
        state.dateBefore = settings.DATE_BEFORE;
        state.user = settings.USER;
        state.useChangeAfterFilter = settings.USE_CHANGE_AFTER_FILTER;
        state.useChangeBeforeFilter = settings.USE_CHANGE_BEFORE_FILTER;
        state.useDateAfterFilter = settings.USE_DATE_AFTER_FILTER;
        state.useDateBeforeFilter = settings.USE_DATE_BEFORE_FILTER;
        state.useUserFilter = settings.USE_USER_FILTER;
        state.stopOnCopy = settings.STOP_ON_COPY;
        if (settings instanceof PerforceChangeBrowserSettings perforceSettings) {
            state.useClientFilter = perforceSettings.USE_CLIENT_FILTER;
            state.client = perforceSettings.CLIENT;
        }
        myState = state;
    }

    private PerforceChangeBrowserSettings stateToSettings() {
        SettingsState state = myState;
        PerforceChangeBrowserSettings settings = new PerforceChangeBrowserSettings();
        settings.USE_CLIENT_FILTER = state.useClientFilter;
        settings.CLIENT = state.client;
        settings.CHANGE_AFTER = state.changeAfter;
        settings.CHANGE_BEFORE = state.changeBefore;
        settings.DATE_AFTER = StringUtil.notNullize(state.dateAfter);
        settings.DATE_BEFORE = StringUtil.notNullize(state.dateBefore);
        settings.USER = state.user;
        settings.USE_CHANGE_AFTER_FILTER = state.useChangeAfterFilter;
        settings.USE_CHANGE_BEFORE_FILTER = state.useChangeBeforeFilter;
        settings.USE_DATE_AFTER_FILTER = state.useDateAfterFilter;
        settings.USE_DATE_BEFORE_FILTER = state.useDateBeforeFilter;
        settings.USE_USER_FILTER = state.useUserFilter;
        settings.STOP_ON_COPY = state.stopOnCopy;
        return settings;
    }
}

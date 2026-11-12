// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.perforce.perforce;

import consulo.application.Application;
import consulo.credentialStorage.CredentialAttributes;
import consulo.credentialStorage.PasswordSafe;
import consulo.credentialStorage.ui.PasswordSafePromptDialog;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.persist.StoragePathMacros;
import consulo.logging.Logger;
import consulo.application.progress.ProgressManager;
import consulo.project.Project;
import consulo.ui.ex.awt.Messages;
import consulo.util.lang.StringUtil;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.change.Change;
import consulo.versionControlSystem.change.ChangeListManager;
import consulo.versionControlSystem.change.ContentRevision;
import consulo.versionControlSystem.change.VcsDirtyScopeManager;
import consulo.versionControlSystem.change.LocalChangeList;
import consulo.virtualFileSystem.VirtualFile;
import consulo.util.collection.ArrayUtil;
import consulo.util.xml.serializer.XmlSerializerUtil;
import consulo.util.xml.serializer.annotation.MapAnnotation;
import consulo.util.xml.serializer.annotation.Property;
import consulo.util.xml.serializer.annotation.Transient;
import org.jspecify.annotations.Nullable;
import consulo.perforce.localize.PerforceLocalize;
import org.jetbrains.idea.perforce.ServerVersion;
import org.jetbrains.idea.perforce.application.ConnectionKey;
import org.jetbrains.idea.perforce.application.PerforceManager;
import org.jetbrains.idea.perforce.operations.VcsOperationLog;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jetbrains.idea.perforce.perforce.connections.P4EnvHelper;
import org.jetbrains.idea.perforce.perforce.connections.PerforceConnectionManager;
import org.jetbrains.idea.perforce.perforce.login.AttemptsStateMachine;
import org.jetbrains.idea.perforce.perforce.login.PerforceLoginManager;
import org.jetbrains.idea.perforce.perforce.login.PerforceOfflineNotification;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import static org.jetbrains.idea.perforce.application.UpdatePasswordKt.askUpdatePassword;

@Singleton
@State(name = "PerforceDirect.Settings", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public final class PerforceSettings implements PersistentStateComponent<PerforceSettings>,
                                                                                              PerforcePhysicalConnectionParametersI {
    private static final Logger LOG = Logger.getInstance(PerforceSettings.class);
    public static final Class<PerforceOfflineModeExitedListener> OFFLINE_MODE_EXITED = PerforceOfflineModeExitedListener.class;

    private static final String PERFORCE_SETTINGS_PASSWORD_KEY = "PERFORCE_SETTINGS_PASSWORD_KEY";

    private final @Nullable Project myProject;
    private final @Nullable PerforceOfflineNotification myOfflineNotification;

    private @Nullable String myEnvIgnore;

    // ------------------ persistent state start

    @Property(surroundWithTag = false)
    @MapAnnotation(surroundWithTag = false, surroundKeyWithTag = false, surroundValueWithTag = false, entryTagName = "CURRENT_CHANGE_LIST")
    public Map<ConnectionId, ParticularConnectionSettings> myConnectionSettings = new LinkedHashMap<>();

    public boolean useP4CONFIG = true;
    public boolean useP4IGNORE = true;
    public String port = PerforceLocalize.settingsPortPlaceholder().get();
    public String client = "";
    public String user = "";
    public boolean showCmds = false;

    public String pathToExec = "p4";
    public String pathToIgnore = ".p4ignore";
    public String PATH_TO_P4VC = "p4vc";

    public boolean myCanGoOffline = false;

    public boolean SYNC_FORCE = false;
    public boolean SYNC_RUN_RESOLVE = true;
    public boolean REVERT_UNCHANGED_FILES = true;
    public boolean REVERT_UNCHANGED_FILES_CHECKIN = false;
    public boolean REVERT_FILES_AFTER_SHELF = true;
    public String CHARSET = PerforcePhysicalConnectionParametersI.getCharsetNone();
    public boolean SHOW_BRANCHES_HISTORY = true;
    public boolean ENABLED = true;
    public boolean USE_LOGIN = true;
    public boolean INTEGRATE_RUN_RESOLVE = true;
    public boolean INTEGRATE_REVERT_UNCHANGED = true;
    public int SERVER_TIMEOUT = 20000;
    public boolean USE_PERFORCE_JOBS = false;
    public boolean SHOW_INTEGRATED_IN_COMMITTED_CHANGES = true;
    public boolean USE_PATTERN_MATCHING_IGNORE = false;
    public boolean FORCE_SYNC_CHANGELISTS = true;

    //
    // public PerforceSettings methods
    //

    public PerforceSettings() {
        myProject = null;
        myOfflineNotification = null;
    }

    @Inject
    public PerforceSettings(Project project) {
        myProject = project;
        myOfflineNotification = new PerforceOfflineNotification(project);
    }

    public static PerforceSettings getSettings(Project project) {
        return project.getInstance(PerforceSettings.class);
    }

    private Project project() {
        return Objects.requireNonNull(myProject, "settings state bean has no project");
    }

    private PerforceOfflineNotification offlineNotification() {
        return Objects.requireNonNull(myOfflineNotification, "settings state bean has no project");
    }

    public void setEnvP4IgnoreVar(@Nullable String p4Ignore) {
        myEnvIgnore = p4Ignore;
    }

    public PerforcePhysicalConnectionParameters getPhysicalSettings(boolean useP4Ignore) {
        return new PerforcePhysicalConnectionParameters(getPathToExec(), useP4Ignore ? getPathToIgnore() : null, project(), getServerTimeout(), getCharsetName());
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    public String[] getConnectArgs() {
        assert !useP4CONFIG;
        ArrayList<String> args = new ArrayList<>();

        if (!StringUtil.isEmptyOrSpaces(port)) {
            args.add("-p");
            args.add(port);
        }
        if (!StringUtil.isEmptyOrSpaces(client)) {
            args.add("-c");
            args.add(client);
        }
        if (!StringUtil.isEmptyOrSpaces(user)) {
            args.add("-u");
            args.add(user);
        }

        if (!isNoneCharset()) {
            args.add("-C");
            args.add(CHARSET);
        }

        return ArrayUtil.toStringArray(args);
    }

    public @Nullable String getConnectionPassword() {
        if (USE_LOGIN) {
            return null;
        }
        String pass = getPasswd();
        return StringUtil.isEmptyOrSpaces(pass) ? null : pass;
    }

    @Override
    public String getPathToExec() {
        return pathToExec;
    }

    @Override
    public @Nullable String getPathToIgnore() {
        if (useP4IGNORE) {
            String envIgnore = myEnvIgnore;
            if (envIgnore == null) {
                envIgnore = P4EnvHelper.getP4IgnoreFileNameFromEnv();
                myEnvIgnore = envIgnore;
            }
            return envIgnore;
        }

        return pathToIgnore;
    }

    @Override
    public Project getProject() {
        return project();
    }

    @Transient
    public @Nullable String getPasswd() {
        return PasswordSafe.getInstance().getPassword(new CredentialAttributes(getClass().getName(), PERFORCE_SETTINGS_PASSWORD_KEY));
    }

    public @Nullable String requestForPassword(@Nullable P4Connection connection) {
        String prompt = connection == null
                              ? PerforceLocalize.messageTextPerforceCommandFailedEnterPasswordV2().get()
                              : PerforceLocalize.messageTextPerforceCommandFailedWithdirEnterPasswordV2(connection.getWorkingDir()).get();
        String title = PerforceLocalize.dialogTitlePerforceLogin().get();
        return project().getInstance(PasswordSafePromptDialog.class).askPassword(title, prompt, getClass(), PERFORCE_SETTINGS_PASSWORD_KEY, true);
    }

    public boolean requestForPasswordUpdate(AttemptsStateMachine attemptsMachine) {
        return askUpdatePassword(project(), attemptsMachine, new CredentialAttributes(getClass().getName(), PERFORCE_SETTINGS_PASSWORD_KEY));
    }

    public long getServerVersion(@Nullable P4Connection connection) throws VcsException {
        return PerforceManager.getInstance(project()).getServerVersionYear(connection);
    }

    public long getServerVersionCached(@Nullable P4Connection connection) throws VcsException {
        return PerforceManager.getInstance(project()).getServerVersionYearCached(connection);
    }

    public @Nullable ServerVersion getServerFullVersion(@Nullable P4Connection connection) throws VcsException {
        return PerforceManager.getInstance(project()).getServerVersion(connection);
    }

    public void ensureOfflineNotify() {
        if (myCanGoOffline && !ENABLED) {
            offlineNotification().ensureNotify(offlineNotification());
        }
    }

    @Override
    public void disable() {
        disable(false);
    }

    public void disable(boolean byUser) {
        if (!myCanGoOffline && !byUser) {
            return;
        }

        if (byUser) {
            saveUnchangedContentsForModifiedFiles();
        }

        doDisable();

        if (PerforceManager.getInstance(project()).isActive()) {
            offlineNotification().ensureNotify(offlineNotification());
            PerforceManager.getInstance(project()).configurationChanged();
        }
    }

    private void doDisable() {
        if (ENABLED) {
            LOG.info("Perforce going offline");
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("doDisable, " + ENABLED, new Throwable());
        }

        ENABLED = false;
    }

    private void saveUnchangedContentsForModifiedFiles() {
        Application.get().assertIsDispatchThread();
        ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
            for (LocalChangeList list : ChangeListManager.getInstance(project()).getChangeLists()) {
                for (Change change : list.getChanges()) {
                    VirtualFile file = change.getVirtualFile();
                    ContentRevision before = change.getBeforeRevision();
                    if (file != null && before != null && !LastUnchangedContentTracker.hasSavedContent(file)) {
                        try {
                            String content = before.getContent();
                            if (content != null) {
                                LastUnchangedContentTracker.forceSavedContent(file, content);
                            }
                        }
                        catch (VcsException e) {
                            LOG.info(e);
                        }
                    }
                }
            }
        }, PerforceLocalize.fileCachingContents(), true, myProject);
    }

    public void enable() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("enable, " + ENABLED, new Throwable());
        }

        ENABLED = true;
        PerforceManager perforceManager = PerforceManager.getInstance(project());
        if (!perforceManager.isActive()) {
            return;
        }

        PerforceConnectionManager.getInstance(project()).updateConnections();

        if (!PerforceLoginManager.getInstance(project()).checkAndRepairAll() || !ENABLED) {
            LOG.debug("enable failed 1 ");

            doDisable();
            offlineNotification().ensureNotify(offlineNotification());
            return;
        }

        @Nullable VcsException[] exc = {null};

        Runnable process = () -> {
            Collection<P4Connection> allConnections = getAllConnections();
            if (allConnections.isEmpty()) {
                return;
            }
            try {
                perforceManager.getClientRoots(allConnections.iterator().next());
            }
            catch (VcsException e) {
                LOG.info(e);
                exc[0] = e;
            }
        };
        if (ProgressManager.getInstance().runProcessWithProgressSynchronously(process, PerforceLocalize.connectionGoingOnline(), true, myProject)) {
            VcsException exception = exc[0];
            if (exception != null && !Application.get().isUnitTestMode()) {
                Messages.showWarningDialog(myProject, exception.getMessage(), PerforceLocalize.connectionCannotConnect().get());
                doDisable();
                offlineNotification().ensureNotify(offlineNotification());
                return;
            }

            // could have changed when checking authorization
            if (ENABLED) {
                VcsOperationLog.getInstance(project()).replayLog();
                if (!offlineNotification().isEmpty()) {
                    offlineNotification().clear();
                }
                project().getMessageBus().syncPublisher(OFFLINE_MODE_EXITED).offlineModeExited();
            }
            else {
                LOG.debug("enable failed 2");
                offlineNotification().ensureNotify(offlineNotification());
            }
        }
    }

    public @Nullable P4Connection getConnectionForFile(P4File file) {
        return PerforceConnectionManager.getInstance(getProject()).getConnectionForFile(file);
    }

    public @Nullable P4Connection getConnectionForFile(File file) {
        return PerforceConnectionManager.getInstance(getProject()).getConnectionForFile(file);
    }

    public @Nullable P4Connection getConnectionForFile(VirtualFile file) {
        return PerforceConnectionManager.getInstance(getProject()).getConnectionForFile(file);
    }

    public @Nullable P4Connection getConnectionForFile(FilePath filePath) {
        return PerforceConnectionManager.getInstance(getProject()).getConnectionForFile(filePath);
    }

    /**
     * For cases when connection working dir doesn't matter and all connections with the same server/client/user can be treated as one
     */
    public Map<ConnectionKey, P4Connection> getConnectionsByKeys() {
        Map<ConnectionKey, P4Connection> key2connection = new HashMap<>();
        for (P4Connection connection : getAllConnections()) {
            key2connection.put(connection.getConnectionKey(), connection);
        }
        return key2connection;
    }


    public Collection<P4Connection> getAllConnections() {
        return Collections
            .unmodifiableCollection(new HashSet<>(PerforceConnectionManager.getInstance(getProject()).getAllConnections().values()));
    }

    public ParticularConnectionSettings getSettings(P4Connection connection) {
        ConnectionId id = connection.getId();
        return myConnectionSettings.computeIfAbsent(id, k -> new ParticularConnectionSettings());
    }

    public boolean isNoneCharset() {
        return StringUtil.isEmptyOrSpaces(CHARSET) || PerforcePhysicalConnectionParametersI.getCharsetNone().equals(CHARSET);
    }

    @Override
    public PerforceSettings getState() {
        return this;
    }

    @Override
    public void loadState(PerforceSettings object) {
        XmlSerializerUtil.copyBean(object, this);
    }

    // accessors of the settings page - transient, the fields are what is stored

    @Transient
    public boolean isEnabled() {
        return ENABLED;
    }

    /**
     * Goes online or offline - nothing happens when it already is.
     */
    public void setEnabled(boolean enabled) {
        if (enabled == ENABLED) {
            return;
        }
        if (enabled) {
            enable();
        }
        else {
            disable(true);
            VcsDirtyScopeManager.getInstance(project()).markEverythingDirty();
        }
    }

    @Transient
    public boolean isCanGoOffline() {
        return myCanGoOffline;
    }

    public void setCanGoOffline(boolean canGoOffline) {
        myCanGoOffline = canGoOffline;
    }

    @Transient
    public boolean isShowCmds() {
        return showCmds;
    }

    public void setShowCmds(boolean value) {
        showCmds = value;
    }

    @Transient
    public boolean isUseLogin() {
        return USE_LOGIN;
    }

    public void setUseLogin(boolean value) {
        USE_LOGIN = value;
    }

    @Transient
    public boolean isShowBranchesHistory() {
        return SHOW_BRANCHES_HISTORY;
    }

    public void setShowBranchesHistory(boolean value) {
        SHOW_BRANCHES_HISTORY = value;
    }

    @Transient
    public boolean isShowIntegratedInCommittedChanges() {
        return SHOW_INTEGRATED_IN_COMMITTED_CHANGES;
    }

    public void setShowIntegratedInCommittedChanges(boolean value) {
        SHOW_INTEGRATED_IN_COMMITTED_CHANGES = value;
    }

    @Transient
    public boolean isUsePerforceJobs() {
        return USE_PERFORCE_JOBS;
    }

    public void setUsePerforceJobs(boolean value) {
        USE_PERFORCE_JOBS = value;
    }

    /**
     * Whether p4 itself tells the ignored files, rather than the patterns matched here.
     */
    @Transient
    public boolean isUseP4ForIgnore() {
        return !USE_PATTERN_MATCHING_IGNORE;
    }

    public void setUseP4ForIgnore(boolean value) {
        USE_PATTERN_MATCHING_IGNORE = !value;
    }

    @Transient
    public boolean isForceSyncChangelists() {
        return FORCE_SYNC_CHANGELISTS;
    }

    public void setForceSyncChangelists(boolean value) {
        FORCE_SYNC_CHANGELISTS = value;
    }

    @Override
    public int getServerTimeout() {
        return SERVER_TIMEOUT;
    }

    @Override
    public String getCharsetName() {
        return CHARSET;
    }
}

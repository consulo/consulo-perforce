// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.login;

import consulo.util.lang.ref.SimpleReference;
import consulo.application.Application;
import consulo.disposer.Disposable;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.logging.Logger;
import consulo.component.ProcessCanceledException;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.project.Project;
import consulo.ui.ex.awt.Messages;
import consulo.localize.LocalizeValue;
import consulo.util.lang.StringUtil;
import org.jetbrains.idea.perforce.perforce.VcsConnectionProblem;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.change.VcsDirtyScopeManager;
import consulo.virtualFileSystem.VirtualFileManager;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import consulo.perforce.localize.PerforceLocalize;
import org.jetbrains.idea.perforce.perforce.PerforceAuthenticationException;
import org.jetbrains.idea.perforce.perforce.PerforceRunner;
import org.jetbrains.idea.perforce.perforce.PerforceSettings;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jetbrains.idea.perforce.perforce.connections.PerforceConnectionManagerI;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
@Singleton
public final class PerforceLoginManager implements LoginSupport {
    private static final Logger LOG = Logger.getInstance(PerforceLoginManager.class);
    private final AuthNotifier myAuthNotifier;
    private final DisabledLoginNotifier myDisabledLoginNotifier;

    private final Object myMapLock = new Object();
    private final Map<P4Connection, AttemptsStateMachine> myState = new HashMap<>();
    private final PerforceSettings mySettings;
    private final Project myProject;
    private final PerforceConnectionManagerI myConnectionManager;
    private final LoginStateListener myLoginStateListener;
    private final List<Runnable> mySuccessfulLoginListeners = new CopyOnWriteArrayList<>();

    @Inject
    public PerforceLoginManager(Project project, PerforceConnectionManagerI connectionManager) {
        myProject = project;
        myConnectionManager = connectionManager;

        mySettings = PerforceSettings.getSettings(project);
        myAuthNotifier = new AuthNotifier(myProject, this, mySettings);
        myDisabledLoginNotifier = new DisabledLoginNotifier(project, this);
        myLoginStateListener = new LoginStateListener() {
            @Override
            protected void notifyListeners(Set<P4Connection> connections) {
                for (P4Connection connection : connections) {
                    myAuthNotifier.removeLazyNotification(connection);
                    myDisabledLoginNotifier.removeLazyNotification(connection);
                }
                VcsDirtyScopeManager.getInstance(project).markEverythingDirty();
                for (Runnable listener : mySuccessfulLoginListeners) {
                    listener.run();
                }
            }
        };
    }

    public static PerforceLoginManager getInstance(Project project) {
        return project.getInstance(PerforceLoginManager.class);
    }

    public void addSuccessfulLoginListener(Runnable runnable) {
        mySuccessfulLoginListeners.add(runnable);
    }

    public void clearAll() {
        synchronized (myMapLock) {
            myAuthNotifier.clear();
            myState.clear();
        }
    }

    public void refreshLoginState() {
        if (!loginPingAllowed()) {
            return;
        }

        myLoginStateListener.startBatch();
        for (P4Connection connection : mySettings.getAllConnections()) {
            getOrCreate(connection).ensure(true);
        }
        myLoginStateListener.fireBatchFinished();
    }

    private boolean loginPingAllowed() {
        return mySettings.ENABLED && mySettings.USE_LOGIN;
    }

    @Override
    public boolean silentLogin(P4Connection connection) throws VcsException {
        LOG.debug("silent login called");
        if (!loginPingAllowed()) {
            LOG.debug("ping is NOT allowed");
            return false;
        }
        AttemptsStateMachine machine = getOrCreate(connection);
        LoginState state = machine.ensure(true);
        if (state.isSuccess()) {
            myAuthNotifier.removeLazyNotification(connection);
            return true;
        }

        // don't show login error notification when network is down - not a problem of login
        String error = state.getError();
        if (error != null) {
            throw new VcsException(error);
        }
        return false;
    }

    public boolean check(P4Connection connection, boolean forceCheck) throws VcsConnectionProblem {
        if (!Application.get().isUnitTestMode()) {
            LOG.assertTrue(!Application.get().isDispatchThread(), "Must not be called on the dispatch thread");
        }

        if (!loginPingAllowed()) {
            return false;
        }

        AttemptsStateMachine machine = getOrCreate(connection);
        LoginState state = machine.ensure(forceCheck);
        if (state.isSuccess()) {
            myAuthNotifier.removeLazyNotification(connection);
            return true;
        }
        // don't show login error notification when network is down - not a problem of login
        String error = state.getError();
        if (error != null) {
            throw new VcsConnectionProblem(error);
        }

        throw new PerforceAuthenticationException(PerforceLocalize.perforceAuthenticationProblem().get(), connection, myProject);
    }

    public boolean checkAndRepair(P4Connection connection) {
        Application.get().assertIsDispatchThread();

        if (!loginPingAllowed()) {
            return true;
        }

        AttemptsStateMachine machine = getOrCreate(connection);

        // check if already logged in
        {
            LoginState state = runUnderProgress(PerforceLocalize.loginCheckingAuthState(), () -> machine.ensure(true));
            if (state == null) {
                return false;
            }

            if (state.isSuccess()) {
                myAuthNotifier.removeLazyNotification(connection);
                myConnectionManager.updateConnections();
                return true;
            }
            if (reportConnectionError(state)) {
                return false;
            }
        }

        // try to log in with the saved password
        Boolean repaired = runUnderProgress(PerforceLocalize.loginGettingCredentials(), () -> {
            String password = mySettings.getPasswd();
            if (StringUtil.isNotEmpty(password)) {
                LoginState newLoginState = loginUnderProgress(machine, password);
                if (newLoginState == null) {
                    return false;
                }

                if (checkLoginState(newLoginState, connection)) {
                    return true;
                }
            }

            return askUserForPassword(connection, machine);
        });
        return repaired != null && repaired;
    }

    public boolean checkPasswordExpirationAndRepair(P4Connection connection) {
        Application.get().assertIsDispatchThread();

        if (!loginPingAllowed()) {
            return true;
        }

        AttemptsStateMachine machine = getOrCreate(connection);
        LoginState state = runUnderProgress(PerforceLocalize.loginCheckingAuthState(), () -> machine.ensure(true));
        String error = state != null ? state.getError() : null;
        if (error != null && error.contains(PerforceRunner.PASSWORD_EXPIRED)) {
            if (!mySettings.requestForPasswordUpdate(machine)) {
                return false;
            }

            LoginState newLoginState = runUnderProgress(PerforceLocalize.loginCheckingAuthState(), () -> machine.ensure(true));
            if (newLoginState == null) {
                return false;
            }

            if (checkLoginState(newLoginState, connection)) {
                return true;
            }
            if (reportConnectionError(newLoginState)) {
                myAuthNotifier.showPasswordWasOk(false);
                return false;
            }
        }

        return checkAndRepair(connection);
    }

    private boolean askUserForPassword(P4Connection connection, AttemptsStateMachine machine) {
        if (Application.get().isUnitTestMode()) {
            return false;
        }

        while (true) {
            String newPassword = mySettings.requestForPassword(myConnectionManager.isSingletonConnectionUsed() ? null : connection);
            if (newPassword == null) {
                return false;
            }
            if (StringUtil.isEmpty(newPassword)) {
                Messages.showInfoMessage(myProject, PerforceLocalize.loginEmptyPasswordRestricted().get(), PerforceLocalize.loginAuthProblem().get());
                continue;
            }
            LoginState newLoginState = loginUnderProgress(machine, newPassword);
            if (newLoginState == null) {
                return false;
            }
            if (checkLoginState(newLoginState, connection)) {
                return true;
            }
            if (reportConnectionError(newLoginState)) {
                myAuthNotifier.showPasswordWasOk(false);
                return false;
            }
        }
    }

    private boolean checkLoginState(LoginState newLoginState, P4Connection connection) {
        if (newLoginState.isSuccess()) {
            myAuthNotifier.showPasswordWasOk(true);
            myAuthNotifier.removeLazyNotification(connection);
            myConnectionManager.updateConnections();
            return true;
        }

        return false;
    }

    private @Nullable LoginState loginUnderProgress(AttemptsStateMachine machine, String newPassword) {
        return runUnderProgress(PerforceLocalize.login(), () -> machine.login(newPassword));
    }

    private boolean reportConnectionError(LoginState state) {
        if (state.isPasswordInvalid()) {
            return false;
        }

        // don't show login error notification when network is down - not a problem of login
        String error = state.getError();
        if (error != null) {
            if (!Application.get().isUnitTestMode()) {
                Messages.showWarningDialog(myProject, error, PerforceLocalize.connectionCannotConnect().get());
            }

            return true;
        }
        return false;
    }

    public void ensureNotifyAboutDisabledLogin(P4Connection connection) {
        myDisabledLoginNotifier.ensureNotify(connection);
    }

    private <T> @Nullable T runUnderProgress(LocalizeValue progressTitle, Supplier<T> computable) {
        SimpleReference<T> result = SimpleReference.create();
        ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
            ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
            if (indicator != null) {
                indicator.setIndeterminate(true);
            }
            try {
                result.set(computable.get());
            }
            catch (ProcessCanceledException ignored) {
            }
        }, progressTitle, true, myProject);
        return result.get();
    }

    public boolean checkAndRepairAll() {
        Collection<P4Connection> connections = runUnderProgress(PerforceLocalize.loginDeterminingCredentials(),
                                                                () -> mySettings.getConnectionsByKeys().values());
        if (connections == null) {
            return false;
        }

        for (P4Connection connection : connections) {
            if (!checkAndRepair(connection)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void notLogged(P4Connection connection) {
        AttemptsStateMachine machine = getOrCreate(connection);
        machine.failed(false, null);
    }

    private AttemptsStateMachine getOrCreate(P4Connection connection) {
        synchronized (myMapLock) {
            AttemptsStateMachine machine = myState.get(connection);
            if (machine != null) {
                return machine;
            }

            AttemptsStateMachine newMachine = new AttemptsStateMachine(new LoginPerformerImpl(myProject, connection, myConnectionManager),
                                                                             myLoginStateListener);
            myState.put(connection, newMachine);
            return newMachine;
        }
    }

    public void startListening(Disposable parentDisposable) {
        if (PerforceLoginTicketsListener.shouldRegister()) {
            PerforceLoginTicketsListener myPerforceLoginTicketsListener = new PerforceLoginTicketsListener(myProject, this, parentDisposable);
            myPerforceLoginTicketsListener.pingListening();
            VirtualFileManager.getInstance().addVirtualFileListener(myPerforceLoginTicketsListener, parentDisposable);
        }
    }

    public AuthNotifier getNotifier() {
        return myAuthNotifier;
    }
}

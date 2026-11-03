// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.connections;

import consulo.application.Application;
import consulo.project.ui.notification.Notification;
import consulo.project.ui.notification.NotificationAction;
import consulo.project.ui.notification.NotificationType;
import consulo.ui.ModalityState;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.progress.Task;
import consulo.project.Project;
import consulo.ui.ex.awt.Messages;
import consulo.versionControlSystem.VcsException;
import consulo.localize.LocalizeValue;
import org.jetbrains.idea.perforce.util.PerforceGenericNotifier;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import consulo.virtualFileSystem.VirtualFile;
import consulo.util.collection.MultiMap;
import consulo.perforce.localize.PerforceLocalize;
import org.jetbrains.idea.perforce.application.ConnectionDiagnoseRefresher;
import org.jetbrains.idea.perforce.application.P4ConfigConnectionDiagnoseDialog;
import org.jetbrains.idea.perforce.application.P4RootsInformation;
import org.jetbrains.idea.perforce.application.PerforceBaseInfoWorker;
import org.jetbrains.idea.perforce.application.PerforceClientRootsChecker;
import org.jetbrains.idea.perforce.perforce.PerforceSettings;
import org.jetbrains.idea.perforce.perforce.login.PerforceLoginManager;

import java.util.Map;
import java.util.Objects;

@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
@Singleton
public final class PerforceConnectionProblemsNotifier extends PerforceGenericNotifier<Object, Object> {
    private static final char NEW_LINE_CHAR = '\n';
    private volatile boolean myConnectionProblems;
    private volatile boolean myParametersProblems;
    private final PerforceSettings mySettings;

    @Inject
    public PerforceConnectionProblemsNotifier(Project project) {
        super(project, PerforceNotificationGroupContributor.GROUP, PerforceLocalize.connectionProblems(), NotificationType.ERROR);
        myConnectionProblems = myParametersProblems = false;
        mySettings = PerforceSettings.getSettings(myProject);
    }

    @Override
    protected void configureNotification(Notification notification, Object obj) {
        NotificationAction inspectAction = NotificationAction.create(PerforceLocalize.actionInspectText(),
                                                                     (e, n) -> showConnectionState(myProject, false));
        notification.addAction(inspectAction);
        if (myConnectionProblems) {
            NotificationAction retryAction = NotificationAction.create(PerforceLocalize.actionRetryText(),
                                                                             (e, n) -> {
                                                                                 if (PerforceLoginManager.getInstance(myProject)
                                                                                   .checkAndRepairAll()) {
                                                                                     notification.expire();
                                                                                 }
                                                                             });
            notification.addAction(retryAction);
            NotificationAction disableAction =
              NotificationAction.create(PerforceLocalize.actionGoofflineText(),
                                              (e, n) -> {
                                                  mySettings.disable(true);
                                                  notification.expire();
                                              });
            notification.addAction(disableAction);
        }
    }

    @Override
    protected boolean ask(Object obj, String description) {
        return false;
    }

    public static void showConnectionState(Project project, boolean refreshBefore) {
        PerforceSettings settings = PerforceSettings.getSettings(project);
        PerforceBaseInfoWorker perforceBaseInfoWorker = project.getInstance(PerforceBaseInfoWorker.class);
        PerforceConnectionManagerI connectionManager = PerforceConnectionManager.getInstance(project);
        if (settings.useP4CONFIG) {
            ConnectionDiagnoseRefresher refresher = new ConnectionDiagnoseRefresher() {
                @Override
                public void refresh() {
                    //settings.enable();  // assume user also meant this
                    connectionManager.updateConnections();
                    // get to force lazy load, refresh
                    PerforceMultipleConnections object = connectionManager.getMultipleConnectionObject();
                    Map<VirtualFile, P4Connection> allConnections = object != null ? object.getAllConnections() : Map.of();
                    refreshChecker(allConnections, perforceBaseInfoWorker);
                }

                @Override
                public PerforceMultipleConnections getMultipleConnections() {
                    return Objects.requireNonNull(connectionManager.getMultipleConnectionObject());
                }

                @Override
                public P4RootsInformation getP4RootsInformation() {
                    return perforceBaseInfoWorker.getCheckerResults();
                }
            };
            Runnable showDialog = () -> {
                P4ConfigConnectionDiagnoseDialog dialog =
                  new P4ConfigConnectionDiagnoseDialog(project, refresher);
                dialog.show();
            };
            if (refreshBefore) {
                underProgress(project, refresher::refresh, showDialog);
            }
            else {
                showDialog.run();
            }
        }
        else {
            Runnable showResults = () -> {
                P4RootsInformation checkerResults = perforceBaseInfoWorker.getCheckerResults();
                showSingleConnectionState(project, checkerResults);
            };
            if (refreshBefore) {
                underProgress(project, () -> refreshChecker(connectionManager.getAllConnections(), perforceBaseInfoWorker), showResults);
            }
            else {
                showResults.run();
            }
        }
    }

    private static void refreshChecker(Map<VirtualFile, P4Connection> allConnections, PerforceBaseInfoWorker perforceBaseInfoWorker) {
        perforceBaseInfoWorker.scheduleRefresh();
        if (!allConnections.isEmpty()) {
            P4Connection connection = allConnections.values().iterator().next();
            try {
                perforceBaseInfoWorker.getInfo(connection);
            }
            catch (VcsException e) {
                //
            }
        }
    }

    private static void underProgress(Project project, Runnable runnable, Runnable onSuccess) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, PerforceLocalize.connectionRefreshState(), true) {
            @Override
            public void run(ProgressIndicator indicator) {
                runnable.run();
            }

            @Override
            public void onSuccess() {
                onSuccess.run();
            }
        });
    }

    public static void showSingleConnectionState(Project project, P4RootsInformation checkerResults) {
        if (!checkerResults.hasAnyErrors() && !checkerResults.hasNotAuthorized() && !checkerResults.hasNoConnections()) {
            Messages.showMessageDialog(project, PerforceLocalize.connectionSuccessful().get(), PerforceLocalize.connectionStateTitle().get(),
                                       Messages.getInformationIcon());
        }
        else {
            String results = getSingleConnectionState(checkerResults);
            if (results.isBlank()) {
                return;
            }

            Messages.showMessageDialog(results, PerforceLocalize.connectionProblemsTitle().get(), Messages.getErrorIcon());
        }
    }

    public static String getSingleConnectionState(P4RootsInformation checkerResults) {
        if (!checkerResults.hasAnyErrors() && !checkerResults.hasNotAuthorized() && !checkerResults.hasNoConnections()) {
            return "";
        }
        else {
            StringBuilder sb = new StringBuilder(PerforceLocalize.connectionProblemsMessage().get()).append(NEW_LINE_CHAR);
            MultiMap<P4Connection, VcsException> errors = checkerResults.getErrors();
            if (checkerResults.hasNoConnections()) {
                sb.append(PerforceLocalize.connectionNoValidConnections().get()).append(NEW_LINE_CHAR);
            }
            for (VcsException exception : errors.values()) {
                sb.append(exception.getMessage()).append(NEW_LINE_CHAR);
            }
            if (checkerResults.hasNotAuthorized()) {
                sb.append(NEW_LINE_CHAR).append(PerforceLocalize.connectionNotLoggedIn().get());
            }
            Map<P4Connection, PerforceClientRootsChecker.WrongRoots> map = checkerResults.getMap();
            for (PerforceClientRootsChecker.WrongRoots wrongRoots : map.values()) {
                sb.append(NEW_LINE_CHAR)
                  .append(PerforceLocalize.configWrongClientSpec().get()).append(NEW_LINE_CHAR)
                  .append(PerforceLocalize.configClientRoots().get()).append(' ').append(NEW_LINE_CHAR);
                for (String root : wrongRoots.getActualInClientSpec()) {
                    sb.append(root).append(NEW_LINE_CHAR);
                }
                sb.append(PerforceLocalize.configActualRoot().get()).append(' ').append(NEW_LINE_CHAR);
                for (VirtualFile root : wrongRoots.getWrong()) {
                    sb.append(root.getPath()).append(NEW_LINE_CHAR);
                }
            }
            return sb.toString();
        }
    }

    private void recalculateState() {
        if (mySettings.ENABLED && (myConnectionProblems || myParametersProblems)) {
            if (mySettings.myCanGoOffline && !Application.get().isUnitTestMode()) {
                Application.get().invokeLater(() -> mySettings.disable(), ModalityState.nonModal());
            }
            ensureNotify(this);
        }
        else {
            clear();
        }
    }

    public void setProblems(boolean connectionProblems, boolean parametersProblems) {
        myConnectionProblems = connectionProblems;
        myParametersProblems = parametersProblems;
        recalculateState();
    }

    public boolean hasConnectionProblems() {
        return myConnectionProblems;
    }

    public static PerforceConnectionProblemsNotifier getInstance(Project project) {
        return project.getInstance(PerforceConnectionProblemsNotifier.class);
    }

    @Override
    protected Object getKey(Object obj) {
        return obj;
    }

    @Override
    protected LocalizeValue getNotificationContent(Object obj) {
        if (myConnectionProblems) {
            return PerforceLocalize.connectionServerNotAvailable();
        }
        return PerforceLocalize.connectionServerNotAvailableInspect();
    }
}

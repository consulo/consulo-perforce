// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.perforce.perforce.connections;

import consulo.application.progress.ProgressManager;
import consulo.perforce.localize.PerforceLocalize;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import org.jetbrains.idea.perforce.application.ClientRootsCache;
import org.jetbrains.idea.perforce.application.ConnectionDiagnoseRefresher;
import org.jetbrains.idea.perforce.application.ConnectionInfo;
import org.jetbrains.idea.perforce.application.P4RootsInformation;
import org.jetbrains.idea.perforce.application.PerforceClientRootsChecker;
import org.jetbrains.idea.perforce.application.PerforceInfoAndClient;
import org.jetbrains.idea.perforce.perforce.PerforceRunner;
import org.jetbrains.idea.perforce.perforce.PerforceSettings;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public final class PerforceConnectionTester {
    private final Project myProject;
    private final PerforceSettings mySettings;
    private final TestPerforceConnectionManager myConnectionManager;
    private final TestLoginManager myLoginManager;

    public PerforceConnectionTester(Project project, PerforceSettings settings) {
        myProject = project;
        mySettings = settings;
        myConnectionManager = new TestPerforceConnectionManager(project, !settings.useP4CONFIG);
        myLoginManager = new TestLoginManager(project, settings, myConnectionManager);
    }

    public TestConnectionResult testConnection() {
        return testConnection(null, null);
    }

    public TestConnectionResult testConnection(@Nullable P4Connection connection) {
        return testConnection(connection, null);
    }

    /**
     * Tests the Perforce connection.
     * Uses the P4CONFIG mode ({@link P4ConfigConnectionTestDataProvider}) or the existing connection mode depending on
     * {@link PerforceSettings#useP4CONFIG}.
     *
     * @param connection the connection to test (required for the connection mode, ignored for the P4CONFIG mode)
     * @param clientRoot optional directory used to check the connection; the project root is used if not specified
     * @return result with the checker, the refresher (P4CONFIG mode) or the cancellation status
     */
    public TestConnectionResult testConnection(@Nullable P4Connection connection, @Nullable Path clientRoot) {
        if (mySettings.useP4CONFIG) {
            return testConnectionWithP4Config();
        }
        else {
            Objects.requireNonNull(connection, "Connection is required for existing connection mode");
            return testExistingConnection(connection, clientRoot);
        }
    }

    private TestConnectionResult testExistingConnection(P4Connection connection, @Nullable Path clientRoot) {
        myConnectionManager.setSingletonConnection(connection);
        PerforceClientRootsChecker[] checker = new PerforceClientRootsChecker[1];
        PerforceRunner runner = new PerforceRunner(myConnectionManager, mySettings, myLoginManager);

        boolean isSuccess = ProgressManager.getInstance().runProcessWithProgressSynchronously(
            () -> {
                Map<VirtualFile, P4Connection> allConnections = myConnectionManager.getAllConnections();
                VirtualFile clientRootVf = clientRoot == null ? null : VirtualFileManager.getInstance().refreshAndFindFileByNioPath(clientRoot);
                if (allConnections.isEmpty() && clientRootVf != null) {
                    allConnections = Collections.singletonMap(clientRootVf, connection);
                }
                ClientRootsCache cache = clientRoot == null ? ClientRootsCache.getClientRootsCache(myProject) : new ClientRootsCache();
                Map<P4Connection, ConnectionInfo> info = PerforceInfoAndClient.calculateInfos(allConnections.values(), runner, cache);
                checker[0] = new PerforceClientRootsChecker(info, allConnections, clientRootVf);
            },
            PerforceLocalize.connectionTest(),
            true,
            myProject
        );

        PerforceClientRootsChecker result = checker[0];
        return new TestConnectionResult(isSuccess && (result == null || !result.hasAnyErrors()), !isSuccess, result);
    }

    private TestConnectionResult testConnectionWithP4Config() {
        PerforceRunner runner = new PerforceRunner(myConnectionManager, mySettings, myLoginManager);
        P4ConfigConnectionTestDataProvider refresher = new P4ConfigConnectionTestDataProvider(myProject, myConnectionManager, runner);

        boolean isSuccess = ProgressManager.getInstance().runProcessWithProgressSynchronously(
            refresher::refresh,
            PerforceLocalize.connectionTest(),
            true,
            myProject
        );

        P4RootsInformation checker = isSuccess ? refresher.getP4RootsInformation() : null;
        return new TestConnectionResult(isSuccess && (checker == null || !checker.hasAnyErrors()),
                                        !isSuccess,
                                        checker,
                                        isSuccess ? refresher : null,
                                        null);
    }

    public static final class TestConnectionResult {
        private final boolean myIsSuccess;
        private final boolean myIsCancelled;
        private final @Nullable P4RootsInformation myInfo;
        private final @Nullable ConnectionDiagnoseRefresher myRefresher;
        private final @Nullable String myErrorMessage;

        public TestConnectionResult(boolean isSuccess, boolean isCancelled, @Nullable P4RootsInformation info) {
            this(isSuccess, isCancelled, info, null, null);
        }

        public TestConnectionResult(boolean isSuccess,
                                    boolean isCancelled,
                                    @Nullable P4RootsInformation info,
                                    @Nullable ConnectionDiagnoseRefresher refresher,
                                    @Nullable String errorMessage) {
            myIsSuccess = isSuccess;
            myIsCancelled = isCancelled;
            myInfo = info;
            myRefresher = refresher;
            myErrorMessage = errorMessage;
        }

        public boolean isSuccess() {
            return myIsSuccess;
        }

        public boolean isCancelled() {
            return myIsCancelled;
        }

        public @Nullable P4RootsInformation getInfo() {
            return myInfo;
        }

        public @Nullable ConnectionDiagnoseRefresher getRefresher() {
            return myRefresher;
        }

        public @Nullable String getErrorMessage() {
            return myErrorMessage;
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof TestConnectionResult that)) {
                return false;
            }
            return myIsSuccess == that.myIsSuccess &&
                myIsCancelled == that.myIsCancelled &&
                Objects.equals(myInfo, that.myInfo) &&
                Objects.equals(myRefresher, that.myRefresher) &&
                Objects.equals(myErrorMessage, that.myErrorMessage);
        }

        @Override
        public int hashCode() {
            return Objects.hash(myIsSuccess, myIsCancelled, myInfo, myRefresher, myErrorMessage);
        }

        @Override
        public String toString() {
            return "TestConnectionResult(isSuccess=" + myIsSuccess + ", isCancelled=" + myIsCancelled + ", info=" + myInfo +
                ", refresher=" + myRefresher + ", errorMessage=" + myErrorMessage + ")";
        }
    }

    private static final class P4ConfigConnectionTestDataProvider implements ConnectionDiagnoseRefresher {
        private final Project myProject;
        private final TestPerforceConnectionManager myConnectionManager;
        private final PerforceRunner myRunner;
        private volatile PerforceClientRootsChecker myChecker = new PerforceClientRootsChecker();
        private volatile Map<P4Connection, ConnectionInfo> myInfo = Collections.emptyMap();
        private volatile @Nullable PerforceMultipleConnections myMc;

        private P4ConfigConnectionTestDataProvider(Project project, TestPerforceConnectionManager connectionManager, PerforceRunner runner) {
            myProject = project;
            myConnectionManager = connectionManager;
            myRunner = runner;
        }

        @Override
        public void refresh() {
            P4ConnectionCalculator calculator = new P4ConnectionCalculator(myProject);
            calculator.execute();
            PerforceMultipleConnections mc = calculator.getMultipleConnections();
            myMc = mc;
            Map<VirtualFile, P4Connection> map = mc.getAllConnections();
            myConnectionManager.setMultipleConnectionObject(mc);
            myInfo = PerforceInfoAndClient.recalculateInfos(myInfo, map.values(), myRunner, ClientRootsCache.getClientRootsCache(myProject)).newInfo;
            myChecker = new PerforceClientRootsChecker(myInfo, map);
        }

        @Override
        public PerforceMultipleConnections getMultipleConnections() {
            return Objects.requireNonNull(myMc);
        }

        @Override
        public P4RootsInformation getP4RootsInformation() {
            return myChecker;
        }
    }
}

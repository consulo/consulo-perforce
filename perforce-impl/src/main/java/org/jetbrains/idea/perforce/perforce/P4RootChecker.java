// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.perforce.perforce;

import consulo.annotation.component.ExtensionImpl;
import consulo.logging.Logger;
import consulo.process.ProcessNotCreatedException;
import consulo.project.Project;
import consulo.util.io.FileUtil;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.VcsKey;
import consulo.versionControlSystem.VcsRootChecker;
import consulo.virtualFileSystem.StandardFileSystems;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import org.jetbrains.idea.perforce.application.PerforceManager;
import org.jetbrains.idea.perforce.application.PerforceVcs;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jetbrains.idea.perforce.perforce.connections.P4ConnectionParameters;
import org.jetbrains.idea.perforce.perforce.connections.P4ParametersConnection;
import org.jetbrains.idea.perforce.perforce.connections.P4ParamsCalculator;
import org.jetbrains.idea.perforce.perforce.connections.PerforceConnectionManager;
import org.jetbrains.idea.perforce.perforce.connections.PerforceConnectionManagerI;
import org.jetbrains.idea.perforce.perforce.connections.PerforceExternalConfigTracker;
import org.jetbrains.idea.perforce.perforce.connections.PerforceLocalConnection;
import org.jetbrains.idea.perforce.perforce.connections.PerforceWorkspaceConfigurator;
import org.jetbrains.idea.perforce.perforce.connections.SingletonConnection;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Perforce has no marker directory, so a Perforce root can't be recognized by looking at a path.
 * <p>
 * Mappings are detected by asking the server for the client root ({@code p4 info}) and selecting the project roots under it,
 * see {@link #detectProjectMappings(Project, Collection, Set)}.
 */
@ExtensionImpl
public final class P4RootChecker extends VcsRootChecker {
    private static final Logger LOG = Logger.getInstance(P4RootChecker.class);

    @Override
    public VcsKey getSupportedVcs() {
        return PerforceVcs.getKey();
    }

    @Override
    public boolean isRoot(String path) {
        return false;
    }

    @Override
    public boolean validateRoot(String path) {
        return true;
    }

    public boolean shouldAlwaysRunInitialDetection() {
        return true;
    }

    public Collection<VirtualFile> detectProjectMappings(Project project,
                                                         Collection<VirtualFile> projectRoots,
                                                         Set<VirtualFile> mappedDirs) throws VcsException {
        Collection<PerforceWorkspaceConfigurator.P4Config> configs =
            project.getInstance(PerforceWorkspaceConfigurator.class).configure(projectRoots, false);
        trackConfigs(project, configs);

        Collection<VirtualFile> mappedRoots = doDetectMappings(project, projectRoots, mappedDirs);

        if (mappedRoots.isEmpty()) {
            configs = project.getInstance(PerforceWorkspaceConfigurator.class).configure(projectRoots, true);
            if (!configs.isEmpty()) {
                trackConfigs(project, configs);
                mappedRoots = doDetectMappings(project, projectRoots, mappedDirs);
            }
        }

        if (PerforceManager.getInstance(project).isActive()) {
            PerforceConnectionManager.getInstance(project).updateConnections();
        }

        return mappedRoots;
    }

    private static Collection<VirtualFile> doDetectMappings(Project project,
                                                            Collection<VirtualFile> projectRoots,
                                                            Set<VirtualFile> mappedDirs) throws VcsException {
        PerforceConnectionManagerI connectionManager = PerforceConnectionManager.getInstance(project);
        if (connectionManager.isSingletonConnectionUsed()) {
            LOG.debug("detecting for singleton connection");
            return detectSingletonConnectionMappings(project, projectRoots, mappedDirs);
        }
        else {
            LOG.debug("detecting for context connection");
            return detectContextConnectionMappings(project, projectRoots, mappedDirs);
        }
    }

    private static void trackConfigs(Project project, Collection<PerforceWorkspaceConfigurator.P4Config> configs) {
        if (configs.isEmpty()) {
            return;
        }

        PerforceExternalConfigTracker configTracker = project.getInstance(PerforceExternalConfigTracker.class);
        configTracker.startTracking();
        Set<String> paths = new HashSet<>();
        for (PerforceWorkspaceConfigurator.P4Config config : configs) {
            paths.add(config.getConfigFile().getPath());
        }
        configTracker.addConfigsToTrack(paths);
    }

    private static Collection<VirtualFile> detectSingletonConnectionMappings(Project project,
                                                                             Collection<VirtualFile> projectRoots,
                                                                             Set<VirtualFile> mappedDirs) throws VcsException {
        List<VirtualFile> unmappedRoots = collectUnmappedDirs(mappedDirs, projectRoots);
        if (unmappedRoots.isEmpty()) {
            return Collections.emptyList();
        }

        PerforceSettings settings = PerforceSettings.getSettings(project);
        P4Connection connection = SingletonConnection.getInstance(project);

        return detectMappingsForConnection(connection, settings, unmappedRoots);
    }

    private static Collection<VirtualFile> detectContextConnectionMappings(Project project,
                                                                           Collection<VirtualFile> projectRoots,
                                                                           Set<VirtualFile> mappedDirs) throws VcsException {
        String baseDir = project.getBasePath();
        if (baseDir == null) {
            return Collections.emptyList();
        }

        List<VirtualFile> unmappedRoots = collectUnmappedDirs(mappedDirs, projectRoots);
        if (unmappedRoots.isEmpty()) {
            return Collections.emptyList();
        }

        PerforceSettings settings = PerforceSettings.getSettings(project);
        P4ConnectionParameters parameters = detectEnvConnectionParametersFor(baseDir, settings);
        LOG.debug("detected connection parameters: " + parameters);

        P4ParametersConnection testConnection = new P4ParametersConnection(parameters, new ConnectionId(null, baseDir));
        return detectMappingsForConnection(testConnection, settings, unmappedRoots);
    }

    private static P4ConnectionParameters detectEnvConnectionParametersFor(String baseDir, PerforceSettings settings) throws VcsException {
        P4Connection localConnection = new PerforceLocalConnection(baseDir);
        ExecResult p4SetOut = localConnection.runP4CommandLine(settings, new String[]{"set"}, null);

        P4ConnectionParameters defaultParameters = new P4ConnectionParameters();
        P4ConnectionParameters parameters = new P4ConnectionParameters();
        P4ParamsCalculator.parseSetOutput(defaultParameters, parameters, p4SetOut.getStdout());
        return parameters;
    }

    // todo: use p4 clients?
    private static List<VirtualFile> detectMappingsForConnection(P4Connection connection,
                                                                 PerforceSettings settings,
                                                                 List<VirtualFile> unmappedRoots) throws VcsException {
        ExecResult p4InfoOut = connection.runP4CommandLine(settings, new String[]{"info"}, null);
        String root = parseP4Info(p4InfoOut.getStdout());
        if (root == null) {
            String stderr = p4InfoOut.getStderr();
            Throwable processException = p4InfoOut.getException();
            if (p4InfoOut.getExitCode() < 0 && processException instanceof ProcessNotCreatedException) {
                // p4 not present
                return Collections.emptyList();
            }
            else if (processException != null) {
                throw new VcsException(processException);
            }
            else if (!stderr.isBlank()) {
                throw new VcsException(stderr);
            }
            else {
                return Collections.emptyList();
            }
        }
        VirtualFile rootFile = StandardFileSystems.local().findFileByPath(root);
        if (rootFile == null) {
            return Collections.emptyList();
        }
        LOG.debug("found root file: " + rootFile.getPath());

        return unmappedRoots.stream().filter(it -> VirtualFileUtil.isAncestor(rootFile, it, false)).toList();
    }

    private static @Nullable String parseP4Info(String output) {
        String result = null;
        for (String line : output.lines().toList()) {
            if (line.startsWith(PerforceRunner.CLIENT_ROOT)) {
                String path = FileUtil.toSystemIndependentName(line.substring(PerforceRunner.CLIENT_ROOT.length()).trim());
                if (result != null) {
                    LOG.warn("unexpected: two roots in output: '" + result + "' and '" + path + "'");
                    return null;
                }
                result = path;
            }
        }
        LOG.debug("parseP4Info - root path: " + result);
        return result;
    }

    private static List<VirtualFile> collectUnmappedDirs(Set<VirtualFile> mappedDirs, Collection<VirtualFile> projectRoots) {
        return projectRoots.stream().filter(root -> {
            for (VirtualFile parent = root; parent != null; parent = parent.getParent()) {
                if (mappedDirs.contains(parent)) {
                    return false;
                }
            }
            return true;
        }).toList();
    }
}

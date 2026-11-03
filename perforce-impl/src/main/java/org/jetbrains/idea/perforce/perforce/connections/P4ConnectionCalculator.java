// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.connections;

import consulo.application.Application;
import consulo.logging.Logger;
import consulo.versionControlSystem.FilePathComparator;
import consulo.project.Project;
import consulo.util.lang.Comparing;
import consulo.versionControlSystem.AbstractFilterChildren;
import consulo.versionControlSystem.ProjectLevelVcsManager;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.idea.perforce.application.PerforceVcs;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class P4ConnectionCalculator {
    private final Project myProject;
    private static final Logger LOG = Logger.getInstance(P4ConnectionCalculator.class);
    private @Nullable PerforceMultipleConnections myMultipleConnections;

    public P4ConnectionCalculator(Project project) {
        myProject = project;
    }

    public void execute() {
        ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);
        PerforceVcs vcs = PerforceVcs.getInstance(myProject);

        P4EnvHelper p4EnvHelper = P4EnvHelper.getConfigHelper(myProject);
        p4EnvHelper.reset();

        String p4ConfigFileName = p4EnvHelper.getP4Config();
        LOG.debug("Using p4config file name: " + p4ConfigFileName);

        List<VirtualFile> detailedVcsMappings = new ArrayList<>(Arrays.asList(vcsManager.getRootsUnderVcs(vcs)));

        Map<VirtualFile, File> configsMap = p4ConfigFileName == null ? Collections.emptyMap()
                                                                           : fillConfigsMap(detailedVcsMappings, p4ConfigFileName);
        Map<VirtualFile,P4ConnectionParameters> connectionSettings = new HashMap<>();
        P4ConnectionParameters defaultParameters = p4EnvHelper.getDefaultParams();

        Application.get().runReadAction(() -> {
            // find files
            filterByConfigFiles(configsMap, detailedVcsMappings);

            for (VirtualFile mapping : detailedVcsMappings) {
                File configParentDir = configsMap.get(mapping);
                P4ConnectionParameters value;
                if (configParentDir == null) {
                    // empty parameters
                    value = new P4ConnectionParameters();
                    value.setNoConfigFound(true);
                }
                else {
                    assert p4ConfigFileName != null;
                    // todo: working incorrect when nested p4configs
                    value = P4ParamsCalculator.getParametersFromConfig(configParentDir, p4ConfigFileName);
                }
                value.setConfigFileName(p4ConfigFileName);
                p4EnvHelper.fillDefaultValues(value);

                LOG.debug("Using " + value + " for " + mapping);
                connectionSettings.put(mapping, value);
            }
        });

        // filter what left
        filterSimilarConfigFiles(connectionSettings, detailedVcsMappings);

        //copy what left
        connectionSettings.keySet().retainAll(detailedVcsMappings);
        configsMap.keySet().retainAll(detailedVcsMappings);

        myMultipleConnections = new PerforceMultipleConnections(p4ConfigFileName, defaultParameters, connectionSettings, configsMap);
    }

    /**
     * Available after {@link #execute()}.
     */
    public PerforceMultipleConnections getMultipleConnections() {
        return Objects.requireNonNull(myMultipleConnections, "execute() was not called");
    }

    private static void filterSimilarConfigFiles(Map<VirtualFile, P4ConnectionParameters> connectionSettings,
                                                 List<VirtualFile> detailedVcsMappings) {
        AbstractFilterChildren<VirtualFile> filter = new AbstractFilterChildren<>() {
            @Override
            protected void sortAscending(List<VirtualFile> list) {
                list.sort(FilePathComparator.getInstance());
            }

            @Override
            protected boolean isAncestor(VirtualFile parent, VirtualFile child) {
                P4ConnectionParameters parentSettings = connectionSettings.get(parent);
                P4ConnectionParameters childSettings = connectionSettings.get(child);

                if (parentSettings == null || childSettings == null || parentSettings.hasProblems() || childSettings.hasProblems()) {
                    return false;
                }
                if (!VirtualFileUtil.isAncestor(parent, child, false)) {
                    return false;
                }

                return parentSettings.equals(childSettings);
            }
        };

        filter.doFilter(detailedVcsMappings);
    }

    private static void filterByConfigFiles(Map<VirtualFile, File> configsMap, List<VirtualFile> detailedVcsMappings) {
        AbstractFilterChildren<VirtualFile> filter = new AbstractFilterChildren<>() {
            @Override
            protected void sortAscending(List<VirtualFile> list) {
                list.sort(FilePathComparator.getInstance());
            }

            @Override
            protected boolean isAncestor(VirtualFile parent, VirtualFile child) {
                if (!Comparing.equal(configsMap.get(parent), configsMap.get(child))) {
                    return false;
                }
                if (!VirtualFileUtil.isAncestor(parent, child, false)) {
                    return false;
                }
                return true;
            }
        };

        filter.doFilter(detailedVcsMappings);
    }

    private static Map<VirtualFile, File> fillConfigsMap(List<VirtualFile> detailedVcsMappings, String p4ConfigFileName) {
        P4ConfigHelper p4ConfigHelper = new P4ConfigHelper();
        Map<VirtualFile, File> result = new HashMap<>();
        for (VirtualFile vcsMapping : detailedVcsMappings) {
            result.put(vcsMapping, p4ConfigHelper.findDirWithP4ConfigFile(vcsMapping, p4ConfigFileName));
        }
        return result;
    }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.connections;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.io.FileUtil;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.idea.perforce.perforce.PerforceSettings;
import org.jetbrains.idea.perforce.perforce.connections.P4ClientParser.PerforceClient;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.jetbrains.idea.perforce.perforce.connections.P4ConfigFields.P4CLIENT;
import static org.jetbrains.idea.perforce.perforce.connections.P4ConfigFields.P4IGNORE;
import static org.jetbrains.idea.perforce.perforce.connections.P4ConfigFields.P4PORT;
import static org.jetbrains.idea.perforce.perforce.connections.P4ConfigFields.P4USER;

@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
@Singleton
public class PerforceWorkspaceConfigurator {
    private static final Logger LOG = Logger.getInstance(PerforceWorkspaceConfigurator.class);

    public static final String P4CONFIG_NAME = "p4config.txt";
    public static final String P4IGNORE_NAME = ".p4ignore.txt";

    /**
     * .gitignore exists by default in all .idea directories and already lists project configuration files which should be ignored
     */
    public static final String GITIGNORE_NAME = ".gitignore";

    private final Project myProject;

    @Inject
    public PerforceWorkspaceConfigurator(Project project) {
        myProject = project;
    }

    public static PerforceWorkspaceConfigurator getInstance(Project project) {
        return project.getInstance(PerforceWorkspaceConfigurator.class);
    }

    /**
     * Must be called on a background thread: it runs p4 commands.
     */
    public Collection<P4Config> configure(Collection<VirtualFile> projectRoots, boolean forceCreateConfig) {
        if (projectRoots.isEmpty()) {
            return Collections.emptyList();
        }

        P4ClientParser p4ClientParser = P4ClientParser.getInstance(myProject);
        Set<P4ConnectionParameters> parametersSet = new LinkedHashSet<>();
        for (P4ConnectionParametersProvider provider : P4ConnectionParametersProvider.getProviders()) {
            parametersSet.addAll(provider.getConnectionParameters(myProject));
        }

        if (parametersSet.isEmpty()) {
            return Collections.emptyList();
        }

        Map<VirtualFile, PerforceClient> clients = p4ClientParser.getPerforceClients(myProject, parametersSet, projectRoots);
        List<P4Config> configs = new ArrayList<>();
        for (Map.Entry<VirtualFile, PerforceClient> entry : clients.entrySet()) {
            PerforceClient client = entry.getValue();
            File workspaceRoot = new File(client.getWorkspaceRootPath());
            File config = findOrGenerateP4Config(workspaceRoot, client, forceCreateConfig);
            if (config != null) {
                configs.add(new P4Config(config, entry.getKey()));
            }
        }

        return configs;
    }

    private @Nullable File findOrGenerateP4Config(File workspaceRoot, PerforceClient client, boolean forceCreateConfig) {
        if (!workspaceRoot.exists()) {
            LOG.info("Workspace root doesn't exist: " + workspaceRoot);
            return null;
        }
        File configFile = new File(workspaceRoot, getP4ConfigFileName());
        if (configFile.exists() || !forceCreateConfig) {
            return configFile;
        }

        P4ConnectionParameters parameters = client.getParameters();
        String configContent = String.join("\n",
                                           P4PORT.name() + "=" + parameters.getServer(),
                                           P4USER.name() + "=" + parameters.getUser(),
                                           P4CLIENT.name() + "=" + client.getClientName(),
                                           P4IGNORE.name() + "=" + P4IGNORE_NAME + ";" + GITIGNORE_NAME);

        try {
            FileUtil.writeToFile(configFile, configContent);
            if (PerforceSettings.getSettings(myProject).useP4CONFIG) {
                PerforceConnectionManager.getInstance(myProject).updateConnections();
            }
        }
        catch (IOException e) {
            LOG.warn("Unable to generate p4config for " + workspaceRoot, e);
        }

        return configFile;
    }

    private String getP4ConfigFileName() {
        String p4Config = P4EnvHelper.getConfigHelper(myProject).getP4Config();
        return p4Config != null ? p4Config : P4CONFIG_NAME;
    }

    public static final class P4Config {
        private final File myConfigFile;
        private final VirtualFile myContentRoot;

        public P4Config(File configFile, VirtualFile contentRoot) {
            myConfigFile = configFile;
            myContentRoot = contentRoot;
        }

        public File getConfigFile() {
            return myConfigFile;
        }

        public VirtualFile getContentRoot() {
            return myContentRoot;
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof P4Config that)) {
                return false;
            }
            return myConfigFile.equals(that.myConfigFile) && myContentRoot.equals(that.myContentRoot);
        }

        @Override
        public int hashCode() {
            return Objects.hash(myConfigFile, myContentRoot);
        }

        @Override
        public String toString() {
            return "P4Config(configFile=" + myConfigFile + ", contentRoot=" + myContentRoot + ")";
        }
    }
}

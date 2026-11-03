// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.perforce.perforce.connections;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.io.FileUtil;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.util.VcsFileUtil;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.inject.Singleton;
import org.jetbrains.idea.perforce.perforce.ConnectionId;
import org.jetbrains.idea.perforce.perforce.PerforcePhysicalConnectionParametersI;
import org.jetbrains.idea.perforce.perforce.PerforceSettings;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
@Singleton
public class P4ClientParser {
    private static final Logger LOG = Logger.getInstance(P4ClientParser.class);
    private static final Pattern CLIENT_REGEX = Pattern.compile("Client (\\S+)\\s+(\\d+/\\d+/\\d+)\\s+root (\\S+) '(.*)'");

    public static P4ClientParser getInstance(Project project) {
        return project.getInstance(P4ClientParser.class);
    }

    public Map<VirtualFile, PerforceClient> getPerforceClients(Project project,
                                                               Set<P4ConnectionParameters> parametersSet,
                                                               Collection<VirtualFile> contentRoots) {
        PerforcePhysicalConnectionParametersI settings = PerforceSettings.getSettings(project).getPhysicalSettings(false);
        for (P4ConnectionParameters parameters : parametersSet) {
            List<PerforceClient> perforceClients = getPerforceClients(project, settings, parameters);
            // TODO ensure format is "yyyy/mm/dd", otherwise will not work
            List<PerforceClient> sortedClients = new ArrayList<>(perforceClients);
            sortedClients.sort(Comparator.comparing(PerforceClient::getLastUpdate).reversed());

            Map<VirtualFile, PerforceClient> perforceClientsForRoots = new LinkedHashMap<>();
            for (VirtualFile root : contentRoots) {
                String contentRootPath = FileUtil.toSystemDependentName(root.getPath());
                Optional<PerforceClient> client = sortedClients.stream()
                    .filter(c -> VcsFileUtil.isAncestor(c.getWorkspaceRootPath(), contentRootPath, false))
                    .findFirst();
                client.ifPresent(c -> perforceClientsForRoots.put(root, c));
            }

            if (!perforceClientsForRoots.isEmpty()) {
                return perforceClientsForRoots;
            }
        }

        return Collections.emptyMap();
    }

    private List<PerforceClient> getPerforceClients(Project project,
                                                    PerforcePhysicalConnectionParametersI settings,
                                                    P4ConnectionParameters parameters) {
        List<PerforceClient> clients = new ArrayList<>();
        try {
            String user = parameters.getUser();
            if (user == null) {
                return Collections.emptyList();
            }
            String workingDir = project.getBasePath();
            if (workingDir == null) {
                return Collections.emptyList();
            }
            String stdout = new P4ParametersConnection(parameters, new ConnectionId(null, workingDir))
                .runP4CommandLine(settings, toConnectArgs(parameters), new String[]{"clients", "-u", user}, null, parameters.getPassword())
                .getStdout();
            stdout.lines().forEach(line -> {
                PerforceClient client = parseClientLine(line, parameters);
                if (client != null) {
                    clients.add(client);
                }
            });
        }
        catch (VcsException e) {
            LOG.warn("Error while getting perforce clients: ", e);
        }

        return clients;
    }

    private static String[] toConnectArgs(P4ConnectionParameters parameters) {
        List<String> args = new ArrayList<>();
        String server = parameters.getServer();
        if (server != null && !server.isBlank()) {
            args.add("-p");
            args.add(server);
        }
        String client = parameters.getClient();
        if (client != null && !client.isBlank()) {
            args.add("-c");
            args.add(client);
        }
        String user = parameters.getUser();
        if (user != null && !user.isBlank()) {
            args.add("-u");
            args.add(user);
        }
        return args.toArray(String[]::new);
    }

    public @Nullable PerforceClient parseClientLine(String line, P4ConnectionParameters parameters) {
        Matcher matcher = CLIENT_REGEX.matcher(line);
        if (!matcher.find()) {
            return null;
        }

        return new PerforceClient(matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(4), parameters);
    }

    public static final class PerforceClient {
        private final String myClientName;
        private final String myLastUpdate;
        private final String myWorkspaceRootPath;
        private final String myDescription;
        private final P4ConnectionParameters myParameters;

        public PerforceClient(String clientName,
                              String lastUpdate,
                              String workspaceRootPath,
                              String description,
                              P4ConnectionParameters parameters) {
            myClientName = clientName;
            myLastUpdate = lastUpdate;
            myWorkspaceRootPath = workspaceRootPath;
            myDescription = description;
            myParameters = parameters;
        }

        public String getClientName() {
            return myClientName;
        }

        public String getLastUpdate() {
            return myLastUpdate;
        }

        public String getWorkspaceRootPath() {
            return myWorkspaceRootPath;
        }

        public String getDescription() {
            return myDescription;
        }

        public P4ConnectionParameters getParameters() {
            return myParameters;
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof PerforceClient that)) {
                return false;
            }
            return myClientName.equals(that.myClientName) &&
                myLastUpdate.equals(that.myLastUpdate) &&
                myWorkspaceRootPath.equals(that.myWorkspaceRootPath) &&
                myDescription.equals(that.myDescription) &&
                myParameters.equals(that.myParameters);
        }

        @Override
        public int hashCode() {
            return Objects.hash(myClientName, myLastUpdate, myWorkspaceRootPath, myDescription, myParameters);
        }

        @Override
        public String toString() {
            return "PerforceClient(clientName=" + myClientName + ", lastUpdate=" + myLastUpdate +
                ", workspaceRootPath=" + myWorkspaceRootPath + ", description=" + myDescription + ", parameters=" + myParameters + ")";
        }
    }
}

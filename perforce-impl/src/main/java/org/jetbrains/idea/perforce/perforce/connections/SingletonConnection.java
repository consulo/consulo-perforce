// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.perforce.perforce.connections;

import consulo.logging.Logger;
import consulo.versionControlSystem.FilePathComparator;
import consulo.project.Project;
import consulo.util.dataholder.Key;
import consulo.versionControlSystem.FilterDescendantVirtualFileConvertible;
import consulo.versionControlSystem.ProjectLevelVcsManager;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;
import org.jetbrains.idea.perforce.application.ConnectionKey;
import org.jetbrains.idea.perforce.application.PerforceVcs;
import org.jetbrains.idea.perforce.perforce.ConnectionId;
import org.jetbrains.idea.perforce.perforce.ExecResult;
import org.jetbrains.idea.perforce.perforce.PerforcePhysicalConnectionParametersI;
import org.jetbrains.idea.perforce.perforce.PerforceSettings;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.function.Function.identity;

public class SingletonConnection extends AbstractP4Connection implements PerforceConnectionMapper {
    private static final Logger LOG = Logger.getInstance(SingletonConnection.class);

    private static final Key<SingletonConnection> KEY_IN_PROJECT = new Key<>("Connection per project");
    public static final ConnectionId SINGLETON_CONNECTION_ID = new ConnectionId();
    public static final File CURR_DIR = new File(".");
    private final PerforceSettings mySettings;
    private final Project myProject;

    private SingletonConnection(Project project) {
        this(project, PerforceSettings.getSettings(project));
    }

    public SingletonConnection(Project project, PerforceSettings settings) {
        myProject = project;
        mySettings = settings;
    }

    public static SingletonConnection getInstance(Project project){
        SingletonConnection result = project.getUserData(KEY_IN_PROJECT);
        if (result == null) {
            result = new SingletonConnection(project);
            project.putUserData(KEY_IN_PROJECT, result);
        }
        return result;
    }

    @Override
    public void runP4Command(PerforcePhysicalConnectionParametersI parameters,
                             String[] p4args,
                             ExecResult retVal,
                             @Nullable StringBuffer inputStream) {
        runP4CommandImpl(parameters, mySettings.getConnectArgs(), p4args, retVal, inputStream, mySettings.getConnectionPassword());
    }

    @Override
    public File getWorkingDirectory() {
        VirtualFile[] roots = ProjectLevelVcsManager.getInstance(myProject).getRootsUnderVcs(PerforceVcs.getInstance(myProject));
        return roots.length > 0 ? VirtualFileUtil.virtualToIoFile(roots[0]) : CURR_DIR;
    }

    @Override
    public ConnectionKey getConnectionKey() {
        return new ConnectionKey(mySettings.port, mySettings.client, mySettings.user);
    }

    @Override
    public ConnectionId getId() {
        return SINGLETON_CONNECTION_ID;
    }

    @Override
    public boolean handlesFile(File file) {
        return true;
    }

    @Override
    public P4Connection getConnection(VirtualFile file) {
        if (!file.isInLocalFileSystem()) {
            LOG.warn("Trying to get connection for non-local file " + file.getClass() + " " + file);
        }
        // todo check directories?
        // todo don't forget this optimization point
        return this;
    }

    @Override
    public Map<VirtualFile, P4Connection> getAllConnections() {
        Project project = mySettings.getProject();
        List<VirtualFile> files = ProjectLevelVcsManager.getInstance(project).getRootsUnderVcsWithoutFiltering(PerforceVcs.getInstance(project));
        new FilterDescendantVirtualFileConvertible<>(identity(), FilePathComparator.getInstance()).doFilter(new ArrayList<>(files));
        HashMap<VirtualFile, P4Connection> map = new HashMap<>();
        for (VirtualFile file : files) {
            map.put(file, this);
        }
        return map;
    }
}

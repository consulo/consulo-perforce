// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.connections;

import consulo.application.Application;
import consulo.annotation.component.ServiceImpl;
import consulo.disposer.Disposable;
import consulo.logging.Logger;
import consulo.module.content.layer.event.ModuleRootEvent;
import consulo.module.content.layer.event.ModuleRootListener;
import consulo.project.Project;
import consulo.util.io.FileUtil;
import consulo.versionControlSystem.ProjectLevelVcsManager;
import consulo.virtualFileSystem.StandardFileSystems;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import consulo.util.collection.ContainerUtil;
import consulo.component.messagebus.MessageBusConnection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.jetbrains.idea.perforce.application.PerforceManager;
import org.jetbrains.idea.perforce.perforce.P4File;
import org.jetbrains.idea.perforce.perforce.PerforceSettings;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

@ServiceImpl
@Singleton
public class PerforceConnectionManager implements PerforceConnectionManagerI {
    private static final Logger LOG = Logger.getInstance(PerforceConnectionManager.class);

    private final PerforceSettings mySettings;
    private final Project myProject;

    private @Nullable PerforceConnectionMapper myConnectionMapper;

    private final Object myLock = new Object();
    private final PerforceConnectionProblemsNotifier myNotifier;
    private boolean isInitializing;

    @Inject
    public PerforceConnectionManager(Project project) {
        myNotifier = PerforceConnectionProblemsNotifier.getInstance(project);
        mySettings = PerforceSettings.getSettings(project);
        myProject = project;
    }

    @Override
    public boolean isInitialized() {
        synchronized (myLock) {
            return myConnectionMapper != null;
        }
    }

    private @Nullable PerforceConnectionMapper getConnectionMapper() {
        synchronized (myLock) {
            return myConnectionMapper;
        }
    }

    private void initialize() {
        PerforceConnectionMapper mapper;
        if (isSingletonConnectionUsed()) {
            mapper = SingletonConnection.getInstance(myProject);
        }
        else {
            P4ConnectionCalculator calculator = new P4ConnectionCalculator(myProject);
            calculator.execute();
            mapper = calculator.getMultipleConnections();
        }
        synchronized (myLock) {
            if (myConnectionMapper == null) {
                myConnectionMapper = mapper;
                if (mapper instanceof PerforceMultipleConnections) {
                    myNotifier.setProblems(((PerforceMultipleConnections)mapper).hasAnyErrors(), false);
                }
            }
        }
    }

    @Override
    public @Nullable PerforceMultipleConnections getMultipleConnectionObject() {
        PerforceConnectionMapper mapper = getConnectionMapper();
        return mapper instanceof PerforceMultipleConnections ? (PerforceMultipleConnections)mapper : null;
    }

    public static PerforceConnectionManagerI getInstance(Project project) {
        return project.getInstance(PerforceConnectionManagerI.class);
    }

    @Override
    public Map<VirtualFile, P4Connection> getAllConnections() {
        PerforceConnectionMapper mapper = getConnectionMapper();
        if (mapper == null) {
            return Collections.emptyMap();
        }

        return mapper.getAllConnections();
    }

    public void scheduleInitialization() {
        synchronized (myLock) {
            if (myConnectionMapper != null || isInitializing) {
                return;
            }

            isInitializing = true;
        }

        Application.get().executeOnPooledThread(() -> {
            if (myProject.isDisposed()) {
                synchronized (myLock) {
                    isInitializing = false;
                }
                return;
            }

            initialize();
            synchronized (myLock) {
                isInitializing = false;
            }
        });
    }

    private Project getProject() {
        return myProject;
    }

    public static @Nullable VirtualFile findNearestLiveParentFor(File ioFile) {
        File current = ioFile;
        while (true) {
            VirtualFile parent = StandardFileSystems.local().findFileByPath(current.getAbsolutePath());
            if (parent != null) {
                return parent;
            }
            current = current.getParentFile();
            if (current == null) {
                return null;
            }
        }
    }

    @Override
    public @Nullable P4Connection getConnectionForFile(File file) {
        PerforceConnectionMapper mapper = getConnectionMapper();
        if (mapper == null) {
            return null;
        }

        if (mapper instanceof SingletonConnection) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("getConnectionForFile(" + file + ") returning singleton");
            }
            return (P4Connection)mapper;
        }
        VirtualFile vFile = findNearestLiveParentFor(file);
        if (vFile == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("getConnectionForFile(" + file + ") found no live parent");
            }
            return null;
        }
        return mapper.getConnection(vFile);
    }

    @Override
    public @Nullable P4Connection getConnectionForFile(P4File file) {
        return getConnectionForFile(file.getLocalFile());
    }

    @Override
    public @Nullable P4Connection getConnectionForFile(VirtualFile file) {
        // todo : also use client roots information
        PerforceConnectionMapper mapper = getConnectionMapper();
        if (mapper == null) {
            return null;
        }

        return mapper.getConnection(file);
    }

    @Override
    public boolean isSingletonConnectionUsed() {
        return !mySettings.useP4CONFIG;
    }

    public void startListening(Disposable parentDisposable) {
        MessageBusConnection busConnection = getProject().getMessageBus().connect(parentDisposable);
        busConnection.subscribe(ModuleRootListener.class, new MyContentRootChangeListener());
        busConnection.subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, this::updateConnections);

        VirtualFileManager.getInstance()
          .addVirtualFileListener(new PerforceP4ConfigVirtualFileListener(this, myProject, parentDisposable), parentDisposable);
        updateConnections();
    }

    @Override
    public void updateConnections() {
        synchronized (myLock) {
            myConnectionMapper = null;
        }
        scheduleInitialization();
        PerforceManager manager = PerforceManager.getInstance(getProject());
        manager.configurationChanged();
    }

    @Override
    public boolean isUnderProjectConnections(File file) {
        PerforceConnectionMapper mapper = getConnectionMapper();
        if (mapper == null) {
            return false;
        }

        Set<VirtualFile> allRoots = mapper.getAllConnections().keySet();
        return ContainerUtil.or(allRoots, root -> FileUtil.isAncestor(VirtualFileUtil.virtualToIoFile(root), file, false));
    }

    private class MyContentRootChangeListener implements ModuleRootListener {
        @Override
        public void rootsChanged(ModuleRootEvent event) {
            updateConnections();
        }
    }
}

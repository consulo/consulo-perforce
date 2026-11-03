// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.connections;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.disposer.Disposable;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.lang.Pair;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.Watchable;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.function.Function;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

/**
 * Watches p4 config files located outside of the VFS (e.g. above the project roots) with a {@link WatchService}
 * and publishes {@link P4ConfigListener} events when they change.
 */
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
@Singleton
public final class PerforceExternalConfigTracker implements Disposable {
    private static final Logger LOG = Logger.getInstance(PerforceExternalConfigTracker.class);

    private final Project myProject;
    private final Set<Pair<String, WatchKey>> myTrackedConfigFiles = new HashSet<>();
    private volatile @Nullable WatchService myWatchService;
    private @Nullable Future<?> myTrackJob;

    @Inject
    public PerforceExternalConfigTracker(Project project) {
        myProject = project;
    }

    public static PerforceExternalConfigTracker getInstance(Project project) {
        return project.getInstance(PerforceExternalConfigTracker.class);
    }

    public void startTracking() {
        synchronized (myTrackedConfigFiles) {
            Future<?> runningTrackJob = myTrackJob;
            if (runningTrackJob != null && !runningTrackJob.isDone()) {
                return;
            }

            myWatchService = createWatchService();

            myTrackJob = Application.get().executeOnPooledThread(this::trackConfigs);
        }
    }

    @Override
    public void dispose() {
        stopTracking();
    }

    private void stopTracking() {
        synchronized (myTrackedConfigFiles) {
            try {
                WatchService watchService = myWatchService;
                if (watchService != null) {
                    watchService.close();
                }
                Future<?> trackJob = myTrackJob;
                if (trackJob == null) {
                    return;
                }
                if (!trackJob.isDone()) {
                    trackJob.cancel(true);
                }
                myWatchService = null;
                myTrackJob = null;
            }
            catch (IOException e) {
                LOG.warn("Unable to stop tracking", e);
            }
        }
    }

    public void addConfigsToTrack(Set<String> configPaths) {
        synchronized (myTrackedConfigFiles) {
            Set<Pair<String, WatchKey>> registeredConfigs = myTrackedConfigFiles;
            WatchService watchService = myWatchService;
            if (watchService == null) {
                return;
            }

            for (String configPathString : configPaths) {
                Path configPath = toPathSafe(configPathString);
                if (configPath == null) {
                    continue;
                }
                Path configDir = configPath.getParent();
                if (configDir == null) {
                    continue;
                }
                Path configFileName = configPath.getFileName();
                if (configFileName == null) {
                    continue;
                }
                String configName = configFileName.toString();

                boolean alreadyRegistered = false;
                for (Pair<String, WatchKey> registered : registeredConfigs) {
                    if (registered.getFirst().equals(configName) && registered.getSecond().watchable().equals(configDir)) {
                        alreadyRegistered = true;
                        break;
                    }
                }
                if (!alreadyRegistered) {
                    try {
                        WatchKey watchKey = configDir.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
                        registeredConfigs.add(Pair.create(configName, watchKey));
                    }
                    catch (IOException e) {
                        LOG.warn("Unable to track " + configPath, e);
                    }
                }
            }
        }
    }

    private static @Nullable WatchService createWatchService() {
        try {
            return FileSystems.getDefault().newWatchService();
        }
        catch (IOException e) {
            LOG.warn("Unable to create watch service", e);
        }
        return null;
    }

    private void trackConfigs() {
        WatchService watchService = myWatchService;
        if (watchService == null) {
            return;
        }
        try {
            while (true) {
                WatchKey watchKey = watchService.take();

                for (WatchEvent<?> event : watchKey.pollEvents()) {
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }

                    Object context = event.context();
                    if (context == null) {
                        continue;
                    }
                    String fileName = context.toString();
                    boolean configFileChanged = processTracked(tracked -> {
                        for (Pair<String, WatchKey> pair : tracked) {
                            if (pair.getFirst().equals(fileName)) {
                                return true;
                            }
                        }
                        return false;
                    });
                    if (configFileChanged) {
                        String configDir = toPath(watchKey);
                        String configPath = configDir + File.separator + fileName;
                        notifyConfigChanged(configPath);
                    }
                }
                watchKey.reset();
                WatchService current = myWatchService;
                if (current == null) {
                    break;
                }
                watchService = current;
            }
        }
        catch (ClosedWatchServiceException | InterruptedException ignored) {
        }
    }

    private void notifyConfigChanged(String configPath) {
        if (myProject.isDisposed()) {
            return;
        }
        myProject.getMessageBus().syncPublisher(P4ConfigListener.TOPIC).notifyConfigChanged(configPath);
    }

    private static @Nullable Path toPathSafe(String path) {
        try {
            return Path.of(path);
        }
        catch (InvalidPathException e) {
            LOG.warn("Unable to track invalid path " + path, e);
        }

        return null;
    }

    private static String toPath(WatchKey watchKey) {
        Watchable watchable = watchKey.watchable();
        return watchable instanceof Path path ? path.toString() : watchable.toString();
    }

    private <R> R processTracked(Function<Set<Pair<String, WatchKey>>, R> processor) {
        synchronized (myTrackedConfigFiles) {
            return processor.apply(myTrackedConfigFiles);
        }
    }
}

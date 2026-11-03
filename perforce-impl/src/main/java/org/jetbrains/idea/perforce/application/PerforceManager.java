// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.perforce.application;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.application.progress.ProgressManager;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.logging.Logger;
import consulo.perforce.localize.PerforceLocalize;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.io.FileUtil;
import consulo.util.lang.Comparing;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.change.VcsDirtyScopeManager;
import consulo.virtualFileSystem.StandardFileSystems;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import consulo.virtualFileSystem.VirtualFileSystem;
import consulo.virtualFileSystem.event.VirtualFileEvent;
import consulo.virtualFileSystem.event.VirtualFileListener;
import consulo.virtualFileSystem.event.VirtualFilePropertyEvent;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.idea.perforce.ClientVersion;
import org.jetbrains.idea.perforce.ServerVersion;
import org.jetbrains.idea.perforce.perforce.OutputMessageParser;
import org.jetbrains.idea.perforce.perforce.P4Command;
import org.jetbrains.idea.perforce.perforce.P4File;
import org.jetbrains.idea.perforce.perforce.PerforceRunner;
import org.jetbrains.idea.perforce.perforce.PerforceSettings;
import org.jetbrains.idea.perforce.perforce.View;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jetbrains.idea.perforce.perforce.connections.PerforceConnectionManager;
import org.jetbrains.idea.perforce.perforce.login.PerforceLoginManager;
import org.jetbrains.idea.perforce.util.tracer.LongCallsParameters;
import org.jetbrains.idea.perforce.util.tracer.TracerManager;
import org.jetbrains.idea.perforce.util.tracer.TracerParameters;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Supplier;

@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
@Singleton
public final class PerforceManager  {
  private final Project myProject;
  private final PerforceLoginManager myLoginManager;

  private static final Logger LOG = Logger.getInstance(PerforceManager.class);
  private static final Logger LOG_RELATIVE_PATH = Logger.getInstance("#Log_relative_path");
  private static final Logger TRACER_LOG = Logger.getInstance("#org.jetbrains.idea.perforce.application.PerforceManager_TRACER");

  private final Map<P4Connection, PerforceClient> myClientMap = new HashMap<>();

  private static final boolean ourTraceCalls = Boolean.getBoolean("perforce.trace.calls");
  private static final @Nullable String ourTracerProperties = System.getProperty("perforce.trace.calls.properties");
  private @Nullable TracerManager<P4Command> myTracer;

  private final ClientRootsCache myClientRootsCache;
  private final PerforceBaseInfoWorker myPerforceBaseInfoWorker;

  private final VirtualFileListener myListener;

  private volatile @Nullable ClientVersion myClientVersion;
  private volatile boolean myActive;
  private final VirtualFileSystem myLfs;
  private final PerforceShelf myShelf;

  public static PerforceManager getInstance(Project project) {
    return project.getInstance(PerforceManager.class);
  }

  @Inject
  public PerforceManager(Project project, ClientRootsCache clientRootsCache, PerforceBaseInfoWorker perforceBaseInfoWorker) {
    myProject = project;
    myLoginManager = PerforceLoginManager.getInstance(project);
    myListener = new VirtualFileListener() {
      @Override
      public void propertyChanged(VirtualFilePropertyEvent event) {
        if (!event.isFromRefresh()) {
          return;
        }
        if (event.getPropertyName().equals(VirtualFile.PROP_WRITABLE)) {
          boolean wasWritable = Boolean.TRUE.equals(event.getOldValue());
          if (wasWritable) {
            event.getFile().putUserData(P4File.KEY, null);
          }
        }
      }

      @Override
      public void contentsChanged(VirtualFileEvent event) {
        if (!event.isFromRefresh()) {
          return;
        }
        if (!event.getFile().isWritable()) {
          event.getFile().putUserData(P4File.KEY, null);
        }
      }
    };

    myClientRootsCache = clientRootsCache;
    myPerforceBaseInfoWorker = perforceBaseInfoWorker;
    if (ourTraceCalls) {
      myTracer = createTracer();
    }
    myLfs = StandardFileSystems.local();
    myShelf = new PerforceShelf(project);
  }

  private static TracerManager<P4Command> createTracer() {
    String tracerProperties = ourTracerProperties;
    if (tracerProperties != null) {
      Properties properties = new Properties();
      try (FileInputStream stream = new FileInputStream(tracerProperties)) {
        properties.load(stream);

        boolean logAverageTimes = Boolean.parseBoolean(properties.getProperty(TracerProperties.GATHER_AVERAGE_TIMES));
        TracerParameters averageParameters;
        if (logAverageTimes) {
          averageParameters = new TracerParameters(TracerProperties.averageTimesInterval.getValue(properties),
                                                   (int) TracerProperties.averageTimesQueueSize.getValue(properties));
        } else {
          averageParameters = null;
        }
        boolean logConcurrentThreads = Boolean.parseBoolean(properties.getProperty(TracerProperties.GATHER_CONCURRENT_THREADS));
        TracerParameters concurrentThreadsParameters;
        if (logConcurrentThreads) {
          concurrentThreadsParameters = new TracerParameters(TracerProperties.numberConcurrentThreadsInterval.getValue(properties),
                                                             (int) TracerProperties.numberConcurrentThreadsQueueSize.getValue(properties));
        } else {
          concurrentThreadsParameters = null;
        }
        boolean logLongCalls = Boolean.parseBoolean(properties.getProperty(TracerProperties.GATHER_LONG_CALLS));
        LongCallsParameters longCallsParameters;
        if (logLongCalls) {
          longCallsParameters = new LongCallsParameters(TracerProperties.longCallsInterval.getValue(properties),
                                                        (int) TracerProperties.longCallsQueueSize.getValue(properties),
                                                        (int) TracerProperties.longCallsMaxKept.getValue(properties),
                                                        TracerProperties.longCallsLowerBound.getValue(properties));
        } else {
          longCallsParameters = null;
        }
        return new TracerManager<>(averageParameters, concurrentThreadsParameters, longCallsParameters, TRACER_LOG,
                                   TracerProperties.outputInterval.getValue(properties));
      }
      catch (IOException e) {
        //
      }
    }
    return new TracerManager<>(new TracerParameters(TracerProperties.averageTimesInterval.getDefault(),
                                                    (int)TracerProperties.averageTimesQueueSize.getDefault()),
                               new TracerParameters(TracerProperties.numberConcurrentThreadsInterval.getDefault(),
                                                    (int)TracerProperties.numberConcurrentThreadsQueueSize.getDefault()),
                               new LongCallsParameters(TracerProperties.longCallsInterval.getDefault(),
                                                       (int)TracerProperties.longCallsQueueSize.getDefault(),
                                                       (int)TracerProperties.longCallsMaxKept.getDefault(),
                                                       TracerProperties.longCallsLowerBound.getDefault()),
                               TRACER_LOG, TracerProperties.outputInterval.getDefault());
  }

  public @Nullable ClientVersion getClientVersion() {
    ClientVersion version = myClientVersion;
    if (version == null) {
      myClientVersion = version = PerforceRunner.getInstance(myProject).getClientVersion();
    }
    return version;
  }

  public void startListening(Disposable parentDisposable) {
    myActive = true;
    VirtualFileManager.getInstance().addVirtualFileListener(myListener, parentDisposable);
    myLoginManager.startListening(parentDisposable);
    myPerforceBaseInfoWorker.start();

    Disposer.register(parentDisposable, () -> {
      myActive = false;
      myPerforceBaseInfoWorker.stop();
    });
  }

  Map<String, List<String>> getCachedInfo(@Nullable P4Connection connection) throws VcsException {
    Map<String, List<String>> info = myPerforceBaseInfoWorker.getInfo(connection);
    if (info == null) {
      ProgressManager.checkCanceled();
      throw new VcsException(PerforceLocalize.errorInfoIsNotAvailable());
    }
    return info;
  }

  ClientData getCachedClients(@Nullable P4Connection connection) throws VcsException {
    ClientData client = myPerforceBaseInfoWorker.getClient(connection);
    if (client == null) {
      ProgressManager.checkCanceled();
      throw new VcsException(PerforceLocalize.errorInfoIsNotAvailable());
    }
    return client;
  }

  private @Nullable Map<String, List<String>> getInfoOnlyCached(@Nullable P4Connection connection) throws VcsException {
    return myPerforceBaseInfoWorker.getCachedInfo(connection);
  }

  @Nullable ClientData getClientOnlyCached(@Nullable P4Connection connection) throws VcsException {
    return myPerforceBaseInfoWorker.getCachedClient(connection);
  }

  // todo: wrong. we should take all roots, since we can belong to any
  public @Nullable String getClientRoot(@Nullable P4Connection connection) throws VcsException {
    return ContainerUtil.getFirstItem(getClientRoots(connection));
  }

  public List<String> getClientRoots(@Nullable P4Connection connection) throws VcsException {
    return ContainerUtil.filter(getCachedClients(connection).getAllRoots(), mainRootValue -> {
      File file = new File(mainRootValue);
      VirtualFile vf = myLfs.findFileByPath(file.getAbsolutePath());
      return vf != null && vf.isDirectory() || PerforceClientRootsChecker.isDirectory(file);
    });
  }

  public long getServerVersionYear(@Nullable P4Connection connection) throws VcsException {
    Map<String, List<String>> map = getCachedInfo(connection);
    List<String> serverVersions = map.get(PerforceRunner.SERVER_VERSION);
    if (serverVersions == null || serverVersions.isEmpty()) {
      return -1;
    }
    return OutputMessageParser.parseServerVersion(serverVersions.get(0)).getVersionYear();
  }

  public long getServerVersionYearCached(@Nullable P4Connection connection) throws VcsException {
    Map<String, List<String>> map = getInfoOnlyCached(connection);
    if (map == null) {
      return -1;
    }
    List<String> serverVersions = map.get(PerforceRunner.SERVER_VERSION);
    if (serverVersions == null || serverVersions.isEmpty()) {
      return -1;
    }
    return OutputMessageParser.parseServerVersion(serverVersions.get(0)).getVersionYear();
  }

  public @Nullable ServerVersion getServerVersion(@Nullable P4Connection connection) throws VcsException {
    List<String> serverVersions = getCachedInfo(connection).get(PerforceRunner.SERVER_VERSION);
    if (serverVersions == null || serverVersions.isEmpty()) {
      return null;
    }
    return OutputMessageParser.parseServerVersion(serverVersions.get(0));
  }

  public boolean isUnderPerforceRoot(VirtualFile virtualFile) throws VcsException {
    P4Connection connection = PerforceSettings.getSettings(myProject).getConnectionForFile(virtualFile);
    return getClientRoots(connection).stream().anyMatch(path -> isUnderClientRoot(virtualFile, path));
  }

  private boolean isUnderClientRoot(VirtualFile virtualFile, String path) {
    Application application = Application.get();
    return application.runReadAction((Supplier<Boolean>)() -> {
      VirtualFile root = myLfs.findFileByPath(new File(path).getAbsolutePath());
      if (root != null && ((Comparing.equal(root, virtualFile)) || VirtualFileUtil.isAncestor(root, virtualFile, false))) {
        return true;
      }
      return false;
    });
  }

  private static @Nullable String getRelativePath(String filePath, PerforceClient client) throws VcsException {
    return View.getRelativePath(P4File.unescapeWildcards(filePath), client.getName(), client.getViews());
  }

  public static @Nullable File getFileByDepotName(String depotPath, PerforceClient client) throws VcsException {
    int revNumStart = depotPath.indexOf("#");

    String relativePath;

    if (revNumStart >= 0) {
      relativePath = getRelativePath(depotPath.substring(0, revNumStart), client);

    }
    else {
      relativePath = getRelativePath(depotPath, client);
    }

    if (relativePath == null)  {
      if (LOG.isDebugEnabled()) {
        LOG.debug(missingLocalFileDiagnostics(depotPath, client));
      }

      return null;
    }

    Project project = client.getProject();
    List<String> roots = client.getRoots();
    List<File> resultCandidates = roots.isEmpty() ? Collections.singletonList(new File(FileUtil.toSystemDependentName(relativePath.trim())))
                                                  : ContainerUtil.map(roots, clientRoot -> new File(clientRoot, relativePath.trim()));

    File resultInProject = ContainerUtil.find(resultCandidates, result -> project == null ||
                                                                          PerforceConnectionManager.getInstance(project)
                                                                            .isUnderProjectConnections(result));
    if (LOG_RELATIVE_PATH.isDebugEnabled()) {
      LOG_RELATIVE_PATH.debug(
        "depot: '" + depotPath + "' result: '" + resultInProject + "'" + (resultInProject == null ? " checked " + resultCandidates : ""));
    }

    return resultInProject;
  }

  private static String missingLocalFileDiagnostics(String depotPath, PerforceClient client) throws VcsException {
    StringBuilder message = new StringBuilder();
    for (View view : client.getViews()) {
      message.append('\n');
      message.append("View ");
      message.append(view.toString());
    }
    message.append('\n');
    message.append("Cannot find local file for depot path: ").append(depotPath);
    return message.toString();
  }

  public synchronized PerforceClient getClient(P4Connection connection) {
    PerforceClient client = myClientMap.get(connection);
    if (client == null) {
      client = new PerforceClientImpl(myProject, connection);
      myClientMap.put(connection, client);
    }
    return client;
  }

  public void configurationChanged() {
    //noinspection SynchronizeOnThis
    synchronized (this) {
      myClientMap.clear();
    }

    if (!myActive) {
      return;
    }
    myLoginManager.clearAll();
    clearInfoClientCache();
    Application.get().invokeLater(() -> {
      P4File.invalidateFstat(myProject);
      VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    }, myProject.getDisposed());
  }

  public void clearInfoClientCache() {
    myPerforceBaseInfoWorker.scheduleRefresh();
  }

  public @Nullable String getRawRoot(@Nullable String convertedRoot) {
    return myClientRootsCache.getRaw(convertedRoot);
  }

  public String convertP4ParsedPath(@Nullable String convertedClientRoot, String s) {
    String result = myClientRootsCache.convertPath(convertedClientRoot, s);
    LOG_RELATIVE_PATH.debug("convertion, s: '" + s + "' converted: '" + result + "' convertedRoot: '" + convertedClientRoot + "'");
    return result;
  }

  public @Nullable Object traceEnter(P4Command command, String commandPresentation) {
    TracerManager<P4Command> tracer = myTracer;
    if (tracer != null) {
      return tracer.start(command, commandPresentation);
    }
    return null;
  }

  public void traceExit(@Nullable Object context, P4Command command, String commandPresentation) {
    TracerManager<P4Command> tracer = myTracer;
    if (tracer != null && context != null) {
      tracer.stop(context, command, commandPresentation);
    }
  }

  public boolean isTraceEnabled() {
    return ourTraceCalls;
  }

  public void resetClientVersion() {
    myClientVersion = null;
  }

  public boolean isActive() {
    return myActive;
  }

  public static void ensureValidClient(Project project, P4Connection connection) throws VcsException {
    PerforceClient client = getInstance(project).getClient(connection);
    if (client.getName() == null) {
      throw new VcsException(PerforceLocalize.errorMissingPerforceWorkspace());
    }
    if (client.getUserName() == null) {
      throw new VcsException(PerforceLocalize.errorMissingPerforceUserName());
    }
    if (client.getServerPort() == null) {
      throw new VcsException(PerforceLocalize.errorMissingPerforceServerPort());
    }
  }

  public PerforceShelf getShelf() {
    return myShelf;
  }
}

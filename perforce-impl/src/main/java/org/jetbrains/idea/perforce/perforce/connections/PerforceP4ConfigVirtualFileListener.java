// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.connections;

import consulo.disposer.Disposable;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.lang.Pair;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.event.VirtualFileEvent;
import consulo.virtualFileSystem.event.VirtualFileListener;
import consulo.virtualFileSystem.event.VirtualFileMoveEvent;
import consulo.virtualFileSystem.event.VirtualFilePropertyEvent;
import org.jetbrains.idea.perforce.application.PerforceVcs;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * Listen P4 config files changes located in the project content roots and thus available in VFS.
 * <p/>
 * For listening config file changes located outside project roots consider to use {@link PerforceExternalConfigTracker}
 */
public class PerforceP4ConfigVirtualFileListener implements VirtualFileListener {
    private final PerforceConnectionManagerI myConnectionManager;
    private final Project myProject;
    private static final Logger LOG = Logger.getInstance(PerforceP4ConfigVirtualFileListener.class);
    private final P4EnvHelper myP4EnvHelper;

    public PerforceP4ConfigVirtualFileListener(PerforceConnectionManagerI connectionManager,
                                               Project project,
                                               Disposable parentDisposable) {
        myConnectionManager = connectionManager;
        myProject = project;
        myP4EnvHelper = P4EnvHelper.getConfigHelper(myProject);
        project.getMessageBus().connect(parentDisposable).subscribe(P4ConfigListener.TOPIC, new MyPerforceConfigListener());
    }

    @Override
    public void propertyChanged(VirtualFilePropertyEvent event) {
        processFileEvent(event);
    }

    @Override
    public void contentsChanged(VirtualFileEvent event) {
        processFileEvent(event);
    }

    @Override
    public void fileCreated(VirtualFileEvent event) {
        processFileEvent(event);
    }

    @Override
    public void fileDeleted(VirtualFileEvent event) {
        processFileEvent(event);
    }

    @Override
    public void fileMoved(VirtualFileMoveEvent event) {
        processFileEvent(event);
    }

    @Override
    public void beforePropertyChange(VirtualFilePropertyEvent event) {
        processFileEvent(event);
    }

    @Override
    public void beforeContentsChange(VirtualFileEvent event) {
        processFileEvent(event);
    }

    @Override
    public void beforeFileDeletion(VirtualFileEvent event) {
        processFileEvent(event);
    }

    @Override
    public void beforeFileMovement(VirtualFileMoveEvent event) {
        processFileEvent(event);
    }

    private boolean isConfigFileName(String fileName) {
        PerforceMultipleConnections multipleConnections = myConnectionManager.getMultipleConnectionObject();
        if (multipleConnections != null) {
            for (Pair<VirtualFile, P4ConnectionParameters> parameters : multipleConnections.getAllConnectionParameters()) {
                if (Objects.equals(parameters.second.getConfigFileName(), fileName)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isIgnoredFileName(String fileName) {
        if (fileName.equals(myP4EnvHelper.getP4Ignore())) {
            return true;
        }

        PerforceMultipleConnections multipleConnections = myConnectionManager.getMultipleConnectionObject();
        if (multipleConnections != null) {
            for (Pair<VirtualFile, P4ConnectionParameters> parameters : multipleConnections.getAllConnectionParameters()) {
                if (Objects.equals(parameters.second.getIgnoreFileName(), fileName)) {
                    return true;
                }
            }
        }

        return false;
    }

    private void processFileEvent(VirtualFileEvent event) {
        String fileName = event.getFileName();
        processFileChanged(fileName);
    }

    private void processFileChanged(String fileName) {
        if (isIgnoredFileName(fileName)) {
            LOG.debug("p4ignore file changed");
            PerforceVcs.getInstance(myProject).getOnlineChangeProvider().discardCache();
        }

        if (isConfigFileName(fileName)) {
            LOG.debug("p4config file changed");
            myConnectionManager.updateConnections();
        }
    }

     private class MyPerforceConfigListener implements P4ConfigListener {

         @Override
         public void notifyConfigChanged(String configPath) {
             try {
                 Path path = Paths.get(configPath);
                 Path fileNamePath = path.getFileName();

                 if (fileNamePath != null) {
                     processFileChanged(fileNamePath.toString());
                 }
                 else {
                     LOG.warn("Invalid config path: " + configPath);
                 }
             }
             catch (InvalidPathException e) {
                 LOG.warn("Invalid config path: " + configPath, e);
             }
         }
     }
}

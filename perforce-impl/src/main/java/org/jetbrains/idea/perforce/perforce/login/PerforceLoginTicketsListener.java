// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.login;

import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.progress.Task;
import consulo.project.ui.notification.NotificationService;
import consulo.disposer.Disposable;
import consulo.project.Project;
import consulo.application.util.SystemInfo;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import consulo.ui.ex.awt.util.Alarm;
import consulo.virtualFileSystem.StandardFileSystems;
import consulo.virtualFileSystem.event.VirtualFileCopyEvent;
import consulo.virtualFileSystem.event.VirtualFileEvent;
import consulo.virtualFileSystem.event.VirtualFileListener;
import consulo.virtualFileSystem.event.VirtualFileMoveEvent;
import consulo.virtualFileSystem.event.VirtualFilePropertyEvent;
import consulo.util.collection.ArrayUtil;
import consulo.process.local.EnvironmentUtil;
import org.jspecify.annotations.Nullable;
import consulo.perforce.localize.PerforceLocalize;
import org.jetbrains.idea.perforce.perforce.connections.PerforceNotificationGroupContributor;
import org.jetbrains.idea.perforce.perforce.connections.P4ConfigFields;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

public class PerforceLoginTicketsListener implements VirtualFileListener {
    // null -> listener disabled
    private static final @Nullable String ourP4TicketsFile = calculateAndNotify();
    private static final int REFRESH_DELAY = 300;

    private final AtomicReference<@Nullable String> myLoadedText;
    private final PerforceLoginManager myLoginManager;
    private final Project myProject;
    // collapses bursts of ticket file events into a single background login state refresh
    private final Alarm myRefreshAlarm;

    private static @Nullable String calculateAndNotify() {
        String value = calculatePath();
        if (value == null) {
            NotificationService.getInstance()
                .newError(PerforceNotificationGroupContributor.GROUP)
                .title(PerforceLocalize.loginMonitorFailure())
                .content(PerforceLocalize.loginMonitorFailureMsg())
                .notify(null);
            return null;
        }
        try {
            return new File(value).getCanonicalPath().replace('\\', '/');
        }
        catch (IOException e) {
            return value.replace('\\', '/');
        }
    }

    /**
     * Perforce client programs store tickets in the file specified by the P4TICKETS environment variable.
     * If this variable is not set, tickets are stored in %USERPROFILE%\p4tickets.txt on Windows
     * and in $HOME/.p4tickets on UNIX.
     */
    private static @Nullable String calculatePath() {
        String ticketsFilePath = EnvironmentUtil.getValue(P4ConfigFields.P4TICKETS);
        if (!StringUtil.isEmptyOrSpaces(ticketsFilePath)) {
            return ticketsFilePath;
        }

        if (SystemInfo.isWindows) {
            String userProfile = EnvironmentUtil.getValue("USERPROFILE");
            return StringUtil.isEmptyOrSpaces(userProfile) ? null : new File(userProfile, "p4tickets.txt").getPath();
        }
        else {
            String home = EnvironmentUtil.getValue("HOME");
            return StringUtil.isEmptyOrSpaces(home) ? null : new File(home, ".p4tickets").getPath();
        }
    }

    public void pingListening() {
        String ticketsFile = ourP4TicketsFile;
        if (ticketsFile != null) {
            StandardFileSystems.local().refreshAndFindFileByPath(new File(ticketsFile).getAbsolutePath());
        }
    }

    public PerforceLoginTicketsListener(Project project, PerforceLoginManager loginManager, Disposable parentDisposable) {
        myProject = project;
        myLoginManager = loginManager;
        myLoadedText = new AtomicReference<>();
        myRefreshAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, parentDisposable);
    }

    @Override
    public void propertyChanged(VirtualFilePropertyEvent event) {
        processEvent(event);
    }

    @Override
    public void contentsChanged(VirtualFileEvent event) {
        processEvent(event);
    }

    @Override
    public void fileCreated(VirtualFileEvent event) {
        processEvent(event);
    }

    @Override
    public void fileDeleted(VirtualFileEvent event) {
        processEvent(event);
    }

    @Override
    public void fileMoved(VirtualFileMoveEvent event) {
        processEvent(event);
    }

    @Override
    public void fileCopied(VirtualFileCopyEvent event) {
        processEvent(event);
    }

    public static boolean shouldRegister() {
        return ourP4TicketsFile != null;
    }

    private void processEvent(VirtualFileEvent event) {
        String ticketsFile = ourP4TicketsFile;
        if (ticketsFile == null) {
            return;
        }
        String path = event.getFile().getPath();
        if (SystemInfo.isWindows && path.equalsIgnoreCase(ticketsFile) || path.equals(ticketsFile)) {
            try {
                File file = new File(ticketsFile);
                char[] text = file.exists() ? FileUtil.loadFileText(file) : ArrayUtil.EMPTY_CHAR_ARRAY;
                String asString = String.valueOf(text);
                String was = myLoadedText.getAndSet(asString);
                if (! asString.equals(was)) {
                    requestRefresh();
                }
            }
            catch (IOException e) {
                //
            }
        }
    }

    private void requestRefresh() {
        myRefreshAlarm.cancelAllRequests();
        myRefreshAlarm.addRequest(() -> {
            if (myProject.isDisposed()) {
                return;
            }
            ProgressManager.getInstance().run(new Task.Backgroundable(myProject, PerforceLocalize.loginRefreshAuthState(), false) {
                @Override
                public void run(ProgressIndicator indicator) {
                    myLoginManager.refreshLoginState();
                }
            });
        }, REFRESH_DELAY);
    }
}

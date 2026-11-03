// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.perforce.perforce.connections;

import consulo.application.Application;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.ProcessNotCreatedException;
import consulo.process.util.CapturingProcessUtil;
import consulo.disposer.Disposable;
import consulo.logging.Logger;
import consulo.component.ProcessCanceledException;
import consulo.application.progress.ProgressManager;
import consulo.project.Project;
import consulo.ui.ex.awt.Messages;
import consulo.disposer.Disposer;
import consulo.application.util.SystemInfo;
import consulo.util.io.FileUtil;
import org.jetbrains.idea.perforce.util.process.InterruptibleProcess;
import consulo.util.lang.StringUtil;
import consulo.versionControlSystem.ProjectLevelVcsManager;
import consulo.versionControlSystem.VcsException;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import consulo.perforce.localize.PerforceLocalize;
import org.jetbrains.idea.perforce.application.PerforceManager;
import org.jetbrains.idea.perforce.application.PerforceVcs;
import org.jetbrains.idea.perforce.perforce.ExecResult;
import org.jetbrains.idea.perforce.perforce.P4Command;
import org.jetbrains.idea.perforce.perforce.PerforcePhysicalConnectionParametersI;
import org.jetbrains.idea.perforce.perforce.PerforceTimeoutException;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public abstract class AbstractP4Connection implements P4Connection {
    private static final Logger LOG = Logger.getInstance(AbstractP4Connection.class);
    public static final int TIMEOUT_EXIT_CODE = -2;
    public static final String CONNECT_REFUSED = "Connect to server failed";
    private volatile boolean myNotConnected = false;
    private static Map<String, String> ourTestEnvironment = new HashMap<>();
    private static final Consumer<String> EMPTY_CALLBACK = s -> {
    };
    private static Consumer<? super String> ourCommandCallback = EMPTY_CALLBACK;

    @Override
    public ExecResult runP4CommandLine(PerforcePhysicalConnectionParametersI settings,
                                       String[] strings,
                                       @Nullable StringBuffer stringBuffer) throws VcsException {
        ExecResult result = new ExecResult();
        try {
            runP4Command(settings, strings, result, stringBuffer);
        }
        catch (PerforceTimeoutException | InterruptedException | IOException e) {
            throw new VcsException(e);
        }
        return result;
    }

    @Override
    public ExecResult runP4CommandLine(PerforcePhysicalConnectionParametersI settings,
                                       String[] conArgs,
                                       String[] p4args,
                                       @Nullable StringBuffer stringBuffer) throws VcsException {
        return runP4CommandLine(settings, conArgs, p4args, stringBuffer, null);
    }

    public ExecResult runP4CommandLine(PerforcePhysicalConnectionParametersI settings,
                                       String[] conArgs,
                                       String[] p4args,
                                       @Nullable StringBuffer stringBuffer,
                                       @Nullable String password) throws VcsException {
        ExecResult result = new ExecResult();
        try {
            runP4CommandImpl(settings, conArgs, p4args, result, stringBuffer, password);
        }
        catch (RuntimeException e) {
            throw new VcsException(e);
        }
        return result;
    }

    protected void runP4CommandImpl(PerforcePhysicalConnectionParametersI parameters,
                                    String[] connArgs,
                                    String[] p4args,
                                    ExecResult retVal,
                                    @Nullable StringBuffer inputStream) {
        runP4CommandImpl(parameters, connArgs, p4args, retVal, inputStream, null);
    }

    protected void runP4CommandImpl(PerforcePhysicalConnectionParametersI parameters,
                                    String[] connArgs,
                                    String[] p4args,
                                    ExecResult retVal,
                                    @Nullable StringBuffer inputStream,
                                    @Nullable String password) {
        try {
            runCmdLine(parameters, connArgs, p4args, retVal, inputStream, password);
        }
        catch (ProcessCanceledException e) {
            throw e;
        }
        catch (Exception e) {
            //noinspection InstanceofCatchParameter
            if (e instanceof ProcessNotCreatedException) {
                String message = e.getMessage();
                if (message != null && message.contains("No such file or directory")) {
                    retVal.setException(new ProcessNotCreatedException(
                      PerforceLocalize.dialogMessageInvalidPerforceExecutableName(String.valueOf(e.getMessage())).get(), e.getCause(), ((ProcessNotCreatedException)e).getCommandLine()));
                    return;
                }
            }


            retVal.setException(e);
        }
    }

    private void runCmdLine(PerforcePhysicalConnectionParametersI perforceSettings,
                            String[] connArgs,
                            String[] p4args,
                            ExecResult retVal,
                            @Nullable StringBuffer inputData,
                            @Nullable String password) throws Exception {
        ProgressManager.checkCanceled();
        Project project = perforceSettings.getProject();

        File cwd = getWorkingDirectory();
        Charset charset = perforceSettings.getConsoleCharset();
        retVal.setCharset(charset);
        GeneralCommandLine cmd = fillCmdLine(perforceSettings, connArgs, p4args, password);
        cmd.setWorkDirectory(cwd);
        cmd.setCharset(charset);
        setEnvironment(cwd, cmd.getEnvironment());

        CommandDebugInfoWrapper debugInfoWrapper = new CommandDebugInfoWrapper(cmd);
        Tracer tracer = new Tracer(project, p4args.length > 0 ? p4args[0] : "", debugInfoWrapper);

        debugCmd(cwd, debugInfoWrapper, cmd.getEnvironment());

        int rc = -42;
        @Nullable Process proc = null;
        @Nullable MyInterruptibleProcess worker = null;
        @Nullable PerforceProcessWaiter processWaiter = null;
        @Nullable String processList = null;
        try {
            tracer.start();
            proc = cmd.createProcess();
            if (inputData != null) {
                passInputToProcess(inputData.toString(), proc, perforceSettings);
            }

            worker = new MyInterruptibleProcess(project, proc, perforceSettings.getServerTimeout());

            processWaiter = new PerforceProcessWaiter();
            worker.setOnBeforeInterrupt(processWaiter::cancelListeners);
            rc = processWaiter.execute(worker, perforceSettings.getServerTimeout());
        }
        catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        }
        catch (TimeoutException e) {
            rc = TIMEOUT_EXIT_CODE;
        }
        finally {
            if (rc == TIMEOUT_EXIT_CODE && Application.get().isUnitTestMode()) {
                processList = ProcessHandle.allProcesses().map(h -> h.pid() + ": " + h.info()).collect(Collectors.joining("\n"));
            }

            tracer.stop();
            if (worker != null) {
                worker.closeProcess();
            }
            else if (proc != null) {
                InterruptibleProcess.close(proc);
            }
        }

        // a return code is only computed after the waiter and the worker have been created
        PerforceProcessWaiter waiter = Objects.requireNonNull(processWaiter);
        if (rc == 0) {
            MyInterruptibleProcess finishedWorker = Objects.requireNonNull(worker);
            retVal.setExitCode(finishedWorker.getExitCode());
            retVal.setOutputGobbler(Objects.requireNonNull(waiter.getInStreamListener()));
            retVal.setErrorGobbler(Objects.requireNonNull(waiter.getErrStreamListener()));

            // checked here, since in PerforceRunner we deal with more high-level commands (that require authentication)
            // but this class can be used solely, without PerforceRunner wrapping
            if (finishedWorker.getExitCode() != 0 && retVal.getException() == null && retVal.getStderr().contains(CONNECT_REFUSED)) {
                notConnected();
                return;
            }
            connected();
        }
        else if (rc == TIMEOUT_EXIT_CODE) {
            retVal.setOutputGobbler(Objects.requireNonNull(waiter.getInStreamListener()));
            retVal.setErrorGobbler(Objects.requireNonNull(waiter.getErrStreamListener()));
            LOG.info("Perforce real timeout: " + perforceSettings.getServerTimeout());
            LOG.info("stdout: " + retVal.getStdout());
            LOG.info("stderr: " + retVal.getStderr());
            retVal.setStdout("");
            retVal.setStderr(PerforceLocalize.exceptionTextPerforceIntegrationDisconnected().get());
            if (LOG.isDebugEnabled()) {
                LOG.debug("process list:\n" + processList);
            }
            if (Application.get().isUnitTestMode()) {
                System.out.println("======================");
                GeneralCommandLine sysInfo = new GeneralCommandLine(SystemInfo.isWindows ? "systeminfo" : SystemInfo.isMac ? "vm_stat" : "free").withRedirectErrorStream(true);
                System.out.println("system info:\n" + CapturingProcessUtil.execAndGetOutput(sysInfo).getStdout());
            }
            waiter.clearGobblers();
            throw new PerforceTimeoutException();
        }
        else {
            LOG.info("Perforce unreal timeout: " + perforceSettings.getServerTimeout() + "; rc=" + rc);
            notConnected();
            perforceSettings.disable();
            waiter.clearGobblers();
            throw new PerforceTimeoutException();
        }
    }

    public static void setTestEnvironment(Map<String, String> env, Disposable parentDisposable) {
        if (!ourTestEnvironment.isEmpty()) {
            ourTestEnvironment.putAll(env);
            return;
        }

        ourTestEnvironment = new HashMap<>(env);
        Disposer.register(parentDisposable, new Disposable() {
            @Override
            public void dispose() {
                //noinspection AssignmentToStaticFieldFromInstanceMethod
                ourTestEnvironment = Collections.emptyMap();
            }
        });
    }

    public static Map<String, String> getTestEnvironment() {
        return ourTestEnvironment;
    }

    public static List<String> dumpCommands(Disposable parentDisposable) {
        CopyOnWriteArrayList<String> list = new CopyOnWriteArrayList<>();
        setCommandCallback(list::add, parentDisposable);
        return list;
    }

    public static void setCommandCallback(Consumer<? super String> callback, Disposable parentDisposable) {
        assert ourCommandCallback == EMPTY_CALLBACK;
        ourCommandCallback = callback;
        Disposer.register(parentDisposable, new Disposable() {
            @Override
            public void dispose() {
                //noinspection AssignmentToStaticFieldFromInstanceMethod
                ourCommandCallback = EMPTY_CALLBACK;
            }
        });
    }

    private static GeneralCommandLine fillCmdLine(PerforcePhysicalConnectionParametersI perforceSettings, String[] connArgs, String[] p4args,
                                                  @Nullable String password) {
        GeneralCommandLine cmd = new GeneralCommandLine(perforceSettings.getPathToExec());
        String cmdName = p4args.length == 0 ? null : p4args[0];
        if (ourCommandCallback != EMPTY_CALLBACK) {
            ourCommandCallback.accept(StringUtil.join(p4args, " "));
        }
        boolean addZProg = "true".equals(System.getProperty("perforce.specify.zprog", "true"));
        if (addZProg) {
            cmd.addParameter("-zprog=Consulo_" + cmdName);
        }
        cmd.addParameters(connArgs);
        cmd.addParameters(p4args);
        if (perforceSettings.getPathToIgnore() != null) {
            cmd.withEnvironment(P4ConfigFields.P4IGNORE.getName(), perforceSettings.getPathToIgnore());
        }
        if (password != null && !password.isBlank()) {
            cmd.withEnvironment(P4ConfigFields.P4PASSWD.getName(), password);
        }
        return cmd;
    }

    private static void setEnvironment(File cwd, Map<String, String> env) {
        // On Unix, Perforce relies on the "PWD" variable to determine its current working directory
        // for finding .p4config.  We need to make sure it matches the directory we want to use.
        env.put("PWD", cwd.getAbsolutePath());
        env.putAll(ourTestEnvironment);
    }

    private static void passInputToProcess(String inputData, Process proc, PerforcePhysicalConnectionParametersI perforceSettings) throws IOException {
        OutputStream outputStream = proc.getOutputStream();

        String charsetName = perforceSettings.getCharsetName();
        byte[] bytes;
        try {
            //noinspection SSBasedInspection
            bytes = !PerforcePhysicalConnectionParametersI.getCharsetNone().equals(charsetName) ? inputData.getBytes(charsetName) : inputData.getBytes(
              StandardCharsets.UTF_8);
        }
        catch (UnsupportedEncodingException e) {
            LOG.info(e);
            //noinspection SSBasedInspection
            bytes = inputData.getBytes(StandardCharsets.UTF_8);
        }
        outputStream.write(bytes);
        // must close or p4 won't read input
        outputStream.close();
    }

    private static final class CommandDebugInfoWrapper {
        private final GeneralCommandLine myCmd;
        private @Nullable String myPresentation;

        private CommandDebugInfoWrapper(GeneralCommandLine cmd) {
            myCmd = cmd;
        }

        String getPresentation() {
            String result = myPresentation;
            if (result == null) {
                StringBuilder presentation = new StringBuilder();

                String option = P4ConfigFields.P4PASSWD.getFlag();
                List<String> args = myCmd.getParametersList().getList();
                for (int i = 0; i < args.size(); i++) {
                    String s = args.get(i);
                    presentation.append(" ").append(s);

                    if (option.equals(s.trim())) {
                        if ((i + 1) < args.size()) {
                            presentation.append(" *****");  // hide password
                            //noinspection AssignmentToForLoopParameter
                            ++ i;
                        }
                    }
                }

                result = presentation.toString();
                myPresentation = result;
            }
            return result;
        }
    }

    private void debugCmd(File cwd, CommandDebugInfoWrapper wrapper, Map<String, String> env) {
        if (LOG.isDebugEnabled()) {
            Map<String, String> safeEnv = new HashMap<>(env);
            safeEnv.computeIfPresent(P4ConfigFields.P4PASSWD.getName(), (k, v) -> "*****");
            LOG.debug("[Perf Execute:] " + wrapper.getPresentation() + "\n [cwd] " + cwd + "\n env=" + safeEnv + "\n connection=" + this);
        }
    }

    private static final class MyInterruptibleProcess extends InterruptibleProcess {
        private final boolean myNeedStallDialog;
        private final Project myProject;
        // todo think & refactor
        // to be able to notify gobblers that streams are closed by parent
        private @Nullable Runnable myOnBeforeInterrupt;

        private MyInterruptibleProcess(Project project, Process process, long timeout) {
            super(process, timeout, TimeUnit.MILLISECONDS);
            myProject = project;
            myNeedStallDialog = Application.get().isDispatchThread() || ProgressManager.getInstance().hasModalProgressIndicator();
        }

        @Override
        public void closeProcess() {
            Runnable onBeforeInterrupt = myOnBeforeInterrupt;
            if (onBeforeInterrupt != null) {
                onBeforeInterrupt.run();
            }
            super.closeProcess();
        }

        public void setOnBeforeInterrupt(Runnable onBeforeInterrupt) {
            myOnBeforeInterrupt = onBeforeInterrupt;
        }

        @Override
        protected int processTimeout() {
            if (myProject.isDisposed()) {
                return TIMEOUT_EXIT_CODE;
            }

            PerforceVcs vcs = PerforceVcs.getInstance(myProject);
            if (!ProjectLevelVcsManager.getInstance(myProject).checkVcsIsActive(vcs)) {
                return TIMEOUT_EXIT_CODE;
            }

            return Messages
              .showOkCancelDialog(myProject, PerforceLocalize.confirmationTextPerforceServerNotRespondingDisableIntegration().get(),
                                  PerforceLocalize.dialogTitlePerforce().get(),
                                  PerforceLocalize.buttonTextWaitMore().get(),
                                  PerforceLocalize.buttonTextResentAndDisableIntegration().get(),
                                  Messages.getQuestionIcon());
        }

        @Override
        protected int processTimeoutInEDT() {
            if (!myNeedStallDialog || myProject.isDisposed() || Application.get().isUnitTestMode()) {
                return TIMEOUT_EXIT_CODE;
            }
            return super.processTimeoutInEDT();
        }
    }

    private static class Tracer {
        private final PerforceManager myPm;
        private final P4Command myCommand;
        private final CommandDebugInfoWrapper myWrapper;
        private @Nullable Object myContext;

        Tracer(Project project, String commandName, CommandDebugInfoWrapper wrapper) {
            myPm = PerforceManager.getInstance(project);
            myCommand = P4Command.getInstance(commandName);
            myWrapper = wrapper;
        }

        void start() {
            if (myPm.isTraceEnabled()) {
                myContext = myPm.traceEnter(myCommand, myWrapper.getPresentation());
            }
        }

        void stop() {
            if (myPm.isTraceEnabled()) {
                Object context = myContext;
                if (context == null) {
                    LOG.info("Tracing problem: no enter was registered for " + myWrapper.getPresentation());
                    return;
                }
                myPm.traceExit(context, myCommand, myWrapper.getPresentation());
            }
        }
    }

    public void notConnected() {
        myNotConnected = true;
    }

    public void connected() {
        myNotConnected = false;
    }

    @Override
    public boolean isConnected() {
        return ! myNotConnected;
    }

}

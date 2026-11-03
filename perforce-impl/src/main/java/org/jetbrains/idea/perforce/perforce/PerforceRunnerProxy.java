// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce;

import consulo.application.Application;
import consulo.logging.Logger;
import consulo.application.progress.ProgressManager;
import consulo.project.Project;
import consulo.versionControlSystem.VcsException;
import java.util.function.Function;
import consulo.perforce.localize.PerforceLocalize;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

public class PerforceRunnerProxy {
    private static final Logger LOG = Logger.getInstance(PerforceRunnerProxy.class);

    private final Map<String, Function<Object @Nullable [], String>> myMethodName;
    private final PerforceRunnerI myProxy;

    private final Project myProject;

    public PerforceRunnerProxy(Project project, PerforceRunner runner) {
        myProject = project;

        myProxy = (PerforceRunnerI)Proxy.newProxyInstance(PerforceRunnerI.class.getClassLoader(), new Class<?>[]{PerforceRunnerI.class},
                                                                                                              new MyPooledThreadProxy(runner));
        myMethodName = new HashMap<>();
        fillNames();
    }

    private static String assumeFirstParamP4File(Object @Nullable [] o) {
        if (o == null) {
            return "";
        }
        if (o.length > 0 && o[0] instanceof P4File) {
            return ((P4File) o[0]).getLocalPath();
        }
        else {
            return "";
        }
    }

    // further we can possibly use passed parameters
    private void fillNames() {
        myMethodName.put("fstat", o -> PerforceLocalize.activityRetrievingFileInfo(assumeFirstParamP4File(o)).get());
        myMethodName.put("edit", o -> PerforceLocalize.activityOpeningFile(assumeFirstParamP4File(o)).get());
        myMethodName.put("revert", o -> PerforceLocalize.activityRevertingFile(assumeFirstParamP4File(o)).get());
        myMethodName.put("sync", o -> PerforceLocalize.activitySyncView().get());
    }

    public PerforceRunnerI getProxy() {
        return myProxy;
    }

    private class MyPooledThreadProxy implements InvocationHandler {
        private final PerforceRunnerI myDelegate;
        private final ProgressManager myProgressManager;
        private final Application myApplication;

        MyPooledThreadProxy(PerforceRunnerI delegate) {
            myDelegate = delegate;
            myProgressManager = ProgressManager.getInstance();
            myApplication = Application.get();
        }

        @Override
        public @Nullable Object invoke(Object proxy, Method method, Object @Nullable [] args) throws Throwable {
            if (! (PerforceRunnerI.class.isAssignableFrom(method.getDeclaringClass()))) {
                LOG.info("Proxy used for wrong class's method invocation: " + method.getDeclaringClass().getCanonicalName());
                return null;
            }

            String title;
            Function<Object @Nullable [], String> getter = myMethodName.get(method.getName());
            if (getter != null) {
                //noinspection HardCodedStringLiteral
                title = getter.apply(args);
            }
            else {
                title = PerforceLocalize.activityPerforming().get();
            }

            // invoke on delegate
            MyActuallyInvoked invoked = new MyActuallyInvoked(myDelegate, method, args);

            boolean bg = myApplication.isDispatchThread();
            if (bg) {
                // todo what about cancellation?
                myProgressManager.runProcessWithProgressSynchronously(invoked, title, false, myProject);
            }
            else {
                invoked.run();
            }

            VcsException vcsException = invoked.getVcsException();
            if (vcsException != null) {
                throw (vcsException instanceof VcsConnectionProblem || !bg ? vcsException : new VcsException(vcsException));
            }
            RuntimeException runtimeException = invoked.getRuntimeException();
            if (runtimeException != null) {
                throw runtimeException;
            }

            return invoked.getResult();
        }
    }

    // todo more generic??
    private static final class MyActuallyInvoked implements Runnable {
        private final Method myMethod;
        private final Object myProxy;
        private final Object @Nullable [] myArgs;

        private @Nullable VcsException myVcsException;
        private @Nullable RuntimeException myRuntimeException;
        private @Nullable Object myResult;

        private MyActuallyInvoked(Object proxy, Method method, Object @Nullable [] args) {
            super();
            myMethod = method;
            myProxy = proxy;
            myArgs = args;
        }

        @Override
        public void run() {
            try {
                myResult = myMethod.invoke(myProxy, myArgs);
            }
            catch (IllegalAccessException e) {
                myRuntimeException = new RuntimeException(e);
            }
            catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof VcsException) {
                    myVcsException = (VcsException) cause;
                }
                else if (cause instanceof RuntimeException) {
                    myRuntimeException = (RuntimeException) cause;
                }
                else if (cause != null) {
                    myRuntimeException = new RuntimeException(cause);
                }
            }
        }

        public @Nullable VcsException getVcsException() {
            return myVcsException;
        }

        public @Nullable RuntimeException getRuntimeException() {
            return myRuntimeException;
        }

        public @Nullable Object getResult() {
            return myResult;
        }
    }
}

/*
 * Copyright 2013-2026 consulo.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.perforce.util.process;

import consulo.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * Waits for a process to finish; reading from its streams counts as activity for the idle timeout.
 */
public abstract class InterruptibleProcess extends InterruptibleActivity {
    private static final Logger LOG = Logger.getInstance(InterruptibleProcess.class);
    private final Process myProcess;
    private final InputStream myInputStream;
    private final InputStream myErrorStream;
    private volatile int myExitCode;
    private boolean myDestroyed;

    protected InterruptibleProcess(Process process, long timeout, TimeUnit timeUnit) {
        super(timeout, timeUnit);
        myProcess = process;

        myInputStream = new InputStreamWrapper(process.getInputStream());
        myErrorStream = new InputStreamWrapper(process.getErrorStream());
    }

    public final InputStream getErrorStream() {
        return myErrorStream;
    }

    public final InputStream getInputStream() {
        return myInputStream;
    }

    public int getExitCode() {
        return myExitCode;
    }

    @Override
    protected void interrupt() {
        closeProcess();
    }

    public static void close(Process process) {
        ProcessCloseUtil.close(process);
    }

    public void closeProcess() {
        if (myDestroyed) {
            return;
        }
        myDestroyed = true;
        close(myProcess);
    }

    @Override
    protected void start() {
        try {
            myExitCode = myProcess.waitFor();
        }
        catch (InterruptedException e) {
            LOG.debug(e);
        }
    }

    private class InputStreamWrapper extends InputStream {
        private final InputStream myDelegate;

        InputStreamWrapper(InputStream delegate) {
            myDelegate = delegate;
        }

        @Override
        public int read() throws IOException {
            int r = myDelegate.read();
            touch();
            return r;
        }

        @Override
        public int read(byte[] b) throws IOException {
            int r = myDelegate.read(b);
            touch();
            return r;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int r = myDelegate.read(b, off, len);
            touch();
            return r;
        }

        @Override
        public void close() throws IOException {
            myDelegate.close();
        }
    }
}

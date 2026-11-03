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

import consulo.application.util.concurrent.AppExecutorUtil;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public final class ProcessCloseUtil {
    private static final long SYNCHRONOUS_WAIT_TIMEOUT = 1000;
    private static final long ASYNCHRONOUS_WAIT_TIMEOUT = 30 * 1000;

    private ProcessCloseUtil() {
    }

    public static void close(Process process) {
        try {
            if (process.waitFor(SYNCHRONOUS_WAIT_TIMEOUT, TimeUnit.MILLISECONDS)) {
                closeStreams(process);
                return;
            }
        }
        catch (InterruptedException e) {
            closeStreams(process);
            throw new RuntimeException(e);
        }

        process.destroy();

        AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> {
            try {
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
            }
            finally {
                closeStreams(process);
            }
        }, ASYNCHRONOUS_WAIT_TIMEOUT, TimeUnit.MILLISECONDS);
    }

    /**
     * Process streams must be closed: on Windows the process kernel object is not destroyed
     * until all handles to the process are released.
     */
    private static void closeStreams(Process process) {
        try {
            process.getOutputStream().close();
        }
        catch (IOException ignored) {
        }
        try {
            process.getInputStream().close();
        }
        catch (IOException ignored) {
        }
        try {
            process.getErrorStream().close();
        }
        catch (IOException ignored) {
        }
    }
}

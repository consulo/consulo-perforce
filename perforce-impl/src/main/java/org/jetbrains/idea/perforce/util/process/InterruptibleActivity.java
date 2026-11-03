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

import consulo.application.Application;
import consulo.application.progress.ProgressManager;
import consulo.application.util.Semaphore;

import java.util.concurrent.TimeUnit;

/**
 * Runs an activity on a pooled thread and waits for it while the activity keeps reporting progress via {@link #touch()}.
 * When the activity stays idle longer than the timeout, {@link #processTimeout()} decides what to do.
 */
public abstract class InterruptibleActivity {
    private volatile boolean myIsTouched = true;
    private final long myTimeout;
    private final TimeUnit myTimeUnit;

    protected InterruptibleActivity(long timeout, TimeUnit timeUnit) {
        myTimeout = timeout;
        myTimeUnit = timeUnit;
    }

    public final void touch() {
        myIsTouched = true;
    }

    protected abstract void start();

    protected abstract void interrupt();

    protected abstract int processTimeout();

    public final int execute() {
        Application application = Application.get();

        Semaphore semaphore = new Semaphore();
        semaphore.down();
        application.executeOnPooledThread(() -> {
            try {
                start();
            }
            finally {
                semaphore.up();
            }
        });

        int rc = waitForSemaphore(semaphore);
        if (rc != 0) {
            application.executeOnPooledThread(this::interrupt);
        }

        return rc;
    }

    private int waitForSemaphore(Semaphore semaphore) {
        long timeoutMs = myTimeUnit.toMillis(myTimeout);
        long lastActiveMoment = System.currentTimeMillis();
        while (true) {
            long current = System.currentTimeMillis();
            if (myIsTouched) {
                myIsTouched = false;
                lastActiveMoment = current;
            }

            long idleTime = current - lastActiveMoment;
            if (idleTime > timeoutMs) {
                int retCode = processTimeoutInEDT();
                return semaphore.waitFor(0) ? 0 : retCode;
            }

            ProgressManager.checkCanceled();

            if (semaphore.waitFor(Math.min(500, timeoutMs - idleTime))) {
                return 0;
            }
        }
    }

    protected int processTimeoutInEDT() {
        int[] retcode = new int[1];

        Application application = Application.get();
        if (application.isDispatchThread()) {
            retcode[0] = processTimeout();
        }
        else {
            application.invokeAndWait(() -> retcode[0] = processTimeout(), application.getAnyModalityState());
        }

        return retcode[0];
    }
}

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
import org.jetbrains.idea.perforce.CancellableRunnable;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Executes an {@link InterruptibleProcess} while its output and error streams are consumed by listeners on pooled threads.
 */
public abstract class ProcessWaiter<T extends CancellableRunnable> {
    protected @Nullable T myInStreamListener;
    protected @Nullable T myErrStreamListener;

    protected abstract T createStreamListener(InputStream stream);

    protected boolean tryReadStreams(int rc) {
        return true;
    }

    public int execute(InterruptibleProcess worker, long timeout)
        throws IOException, ExecutionException, TimeoutException, InterruptedException {
        T errListener = createStreamListener(worker.getErrorStream());
        T inListener = createStreamListener(worker.getInputStream());
        myErrStreamListener = errListener;
        myInStreamListener = inListener;

        Application app = Application.get();

        int rc;
        try {
            Future<?> errorStreamReadingFuture = app.executeOnPooledThread(errListener);
            Future<?> outputStreamReadingFuture = app.executeOnPooledThread(inListener);
            rc = worker.execute();
            if (tryReadStreams(rc)) {
                errorStreamReadingFuture.get(timeout, TimeUnit.MILLISECONDS);
                outputStreamReadingFuture.get(timeout, TimeUnit.MILLISECONDS);
            }
        }
        finally {
            cancelListeners();
        }

        return rc;
    }

    public void cancelListeners() {
        if (myErrStreamListener != null) {
            myErrStreamListener.cancel();
        }
        if (myInStreamListener != null) {
            myInStreamListener.cancel();
        }
    }

    public @Nullable T getInStreamListener() {
        return myInStreamListener;
    }

    public @Nullable T getErrStreamListener() {
        return myErrStreamListener;
    }
}

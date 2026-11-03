// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.util.tracer;

import java.util.function.Supplier;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;

class IntervalStatistics<Data, T extends Consumer<Data>, U extends Consumer<T>> {
    private static final int ourMaxHistoryQueue = 10;
    private static final int ourDefaultInterval = 600 * 1000;

    private final long myIntervalInMillis;
    private final int myHistoryQueueSize;
    private final Supplier<? extends T> myFactory;

    private final U myAverage;
    private final LinkedList<Timed<T>> myRecentComplete = new LinkedList<>();

    private final Object myLock = new Object();

    private Timed<T> myCurrent;
    private final @Nullable Runnable mySwitchListener;

    protected IntervalStatistics(long intervalInMillis, int historyQueueSize, Supplier<? extends T> factory, Supplier<? extends U> averageFactory,
                                 @Nullable Runnable switchListener) {
        mySwitchListener = switchListener;
        myIntervalInMillis = (intervalInMillis <= 0) ? ourDefaultInterval : intervalInMillis;
        myHistoryQueueSize = historyQueueSize <= 0 ? ourMaxHistoryQueue : historyQueueSize;
        myFactory = factory;

        myAverage = averageFactory.get();
        myCurrent = new Timed<>(myFactory.get());
    }

    protected void step(Data data) {
        long currentTime = System.currentTimeMillis();

        synchronized (myLock) {
            myCurrent.getT().accept(data);
            if ((currentTime - myCurrent.getTime()) < myIntervalInMillis) {
                return;
            }

            addToQueue(myCurrent);
            myAverage.accept(myCurrent.getT());
            myCurrent = new Timed<>(myFactory.get());
        }
        if (mySwitchListener != null) {
            mySwitchListener.run();
        }
    }

    private void addToQueue(Timed<T> timed) {
        if (myRecentComplete.size() == myHistoryQueueSize) {
            myRecentComplete.removeLast();
        }
        myRecentComplete.addFirst(timed);
    }

    public U getAverage() {
        return myAverage;
    }

    public List<Timed<T>> receiveRecentComplete() {
        LinkedList<Timed<T>> result = new LinkedList<>(myRecentComplete);
        myRecentComplete.clear();
        return result;
    }

}

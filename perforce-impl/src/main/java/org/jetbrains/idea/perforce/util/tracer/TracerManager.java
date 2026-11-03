// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.util.tracer;

import consulo.logging.Logger;
import consulo.util.lang.Pair;
import org.jspecify.annotations.Nullable;

import java.sql.Time;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class TracerManager<Kind extends Enum> implements Tracer<Kind, Object> {
    private final List<Pair<Tracer, StatisticsPresentation>> myPresentations;
    private final Logger myLogger;
    private final long myInterval;

    private long myPreviousOutputTime;
    private final Runnable myInnerLogger;

    public TracerManager(@Nullable TracerParameters traceAverageTimes,
                         @Nullable TracerParameters traceNumberConcurrentThreads,
                         @Nullable LongCallsParameters traceLongCalls, Logger logger, long interval) {
        myLogger = logger;
        myInterval = interval;
        myPresentations = new LinkedList<>();

        myInnerLogger = () -> {
            long currentTime = System.currentTimeMillis();
            if ((currentTime - myPreviousOutputTime) < myInterval) {
                return;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Statistics at: ").append(new Time(System.currentTimeMillis())).append('\n');
            for (Pair<Tracer, StatisticsPresentation> presentation : myPresentations) {
                presentation.getSecond().putSelf(sb, presentation.getFirst());
                sb.append('\n');
            }
            myLogger.info(sb.toString());
            myPreviousOutputTime = currentTime;
        };

        if (traceAverageTimes != null) {
            myPresentations.add(new Pair<>(
              new AverageByKindTimeStatistics<Kind>(traceAverageTimes.getIntervalInMillis(), traceAverageTimes.getHistoryQueueSize(),
                                                    myInnerLogger),
              new AverageByKindTimePresentation<Kind>()));
        }
        if (traceNumberConcurrentThreads != null) {
            myPresentations.add(new Pair<>(
              new ConcurrentThreadsStatistics<Kind>(traceNumberConcurrentThreads.getIntervalInMillis(),
                                                    traceNumberConcurrentThreads.getHistoryQueueSize(), myInnerLogger),
              new ConcurrentThreadsPresentation<Kind>()));
        }
        if (traceLongCalls != null) {
            myPresentations.add(new Pair<>(
              new LongCallsStatistics<Kind>(traceLongCalls.getIntervalInMillis(), traceLongCalls.getHistoryQueueSize(), myInnerLogger,
                                            traceLongCalls.getMaxKept(), traceLongCalls.getLowerBound()),
              new LongCallsPresentation<Kind>()));
        }
    }

    @Override
    public Object start(Kind kind, String presentation) {
        List<Object> result = new ArrayList<>();
        for (Pair<Tracer, StatisticsPresentation> presentationPair : myPresentations) {
            result.add(presentationPair.getFirst().start(kind, presentation));
        }
        return result;
    }

    @Override
    public void stop(Object o, Kind kind, String presentation) {
        List<Object> parameters = (List) o;
        for (int i = 0; i < parameters.size(); i++) {
            Object param = parameters.get(i);
            Pair<Tracer, StatisticsPresentation> presentationPair = myPresentations.get(i);
            presentationPair.getFirst().stop(param, kind, presentation);
        }
    }
}

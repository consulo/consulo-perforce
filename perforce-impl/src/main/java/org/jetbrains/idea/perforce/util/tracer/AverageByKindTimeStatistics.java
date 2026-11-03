// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.util.tracer;

import consulo.util.lang.Pair;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

class AverageByKindTimeStatistics<Kind extends Enum> extends
      IntervalStatistics<Pair<Kind, Long>, AverageByKindTimeStatistics.Data<Kind>, AverageByKindTimeStatistics.Average<Kind>> implements Tracer<Kind, Long> {

    // todo do not pass switch listener
    AverageByKindTimeStatistics(long intervalInMillis, int historyQueueSize, @Nullable Runnable switchListener) {
        super(intervalInMillis, historyQueueSize, () -> new Data<>(), () -> new Average<>(), switchListener);
    }

    @Override
    public Long start(Kind kind, String presentation) {
        return System.currentTimeMillis();
    }

    @Override
    public void stop(Long key, Kind kind, String presentation) {
        step(new Pair<>(kind, System.currentTimeMillis() - key));
    }

    public static class Data<Kind> implements Consumer<Pair<Kind, Long>> {
        private final Map<Kind, Pair<Long, Integer>> myMap;

        public Data() {
            myMap = new HashMap<>();
        }

        @Override
        public void accept(Pair<Kind, Long> kindLongPair) {
            Kind kind = kindLongPair.getFirst();
            Pair<Long, Integer> current = myMap.get(kind);
            if (current == null) {
                myMap.put(kind, new Pair<>(kindLongPair.getSecond(), 1));
            }
            else {
                myMap.put(kind, new Pair<>(current.getFirst() + kindLongPair.getSecond(), current.getSecond() + 1));
            }
        }

        public Map<Kind, Pair<Long, Integer>> getMap() {
            return myMap;
        }
    }

    public static class Average<Kind> implements Consumer<Data<Kind>> {
        private final Map<Kind, Pair<BigDecimal, Long>> myMap;

        public Average() {
            myMap = new HashMap<>();
        }

        @Override
        public void accept(Data<Kind> kindData) {
            Map<Kind, Pair<Long, Integer>> comingMap = kindData.getMap();
            for (Kind kind : comingMap.keySet()) {
                Pair<BigDecimal, Long> existing = myMap.get(kind);
                Pair<Long, Integer> comingValue = comingMap.get(kind);
                BigDecimal valueToAdd = new BigDecimal(comingValue.getFirst());

                if (existing == null) {
                    myMap.put(kind, new Pair<>(valueToAdd, comingValue.getSecond().longValue()));
                }
                else {
                    myMap.put(kind, new Pair<>(existing.getFirst().add(valueToAdd), existing.getSecond() + comingValue.getSecond()));
                }
            }
        }

        public Map<Kind, Pair<BigDecimal, Long>> getMap() {
            return Collections.unmodifiableMap(myMap);
        }
    }
}

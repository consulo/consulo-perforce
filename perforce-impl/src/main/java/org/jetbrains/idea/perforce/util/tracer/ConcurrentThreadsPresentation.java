// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.util.tracer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Time;
import java.util.List;

class ConcurrentThreadsPresentation<Kind extends Enum> implements StatisticsPresentation<ConcurrentThreadsStatistics<Kind>> {
    @Override
    public void putSelf(StringBuilder sb, ConcurrentThreadsStatistics<Kind> statistics) {
        sb.append("Number of concurrent threads\nAverage:\n");
        ConcurrentThreadsStatistics.MaxAveragePairAverage average = statistics.getAverage();
        int cnt = average.getCnt();
        sb.append("Maximum number: ").append(average.getMax());
        if (cnt > 0) {
            sb.append(", average: ").append(average.getTotal().divide(new BigDecimal(cnt), RoundingMode.HALF_DOWN));
        }

        sb.append("\nRecently:\n");
        List<Timed<ConcurrentThreadsStatistics.MaxCurrentPair>> list = statistics.receiveRecentComplete();
        for (Timed<ConcurrentThreadsStatistics.MaxCurrentPair> timed : list) {
            sb.append("Maximum at ")
              .append(new Time(timed.getTime()))
              .append(": ")
              .append(timed.getT().getMax())
              .append('\n');
        }
    }
}

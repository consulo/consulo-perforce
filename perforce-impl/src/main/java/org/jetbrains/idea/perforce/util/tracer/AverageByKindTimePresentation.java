// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.util.tracer;

import consulo.util.lang.Pair;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Time;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

class AverageByKindTimePresentation<Kind extends Enum> implements StatisticsPresentation<AverageByKindTimeStatistics<Kind>> {
    private static final NumberFormat ourNumberFormat = new DecimalFormat("0.00");

    @Override
    public void putSelf(StringBuilder sb, AverageByKindTimeStatistics<Kind> statistics) {
        AverageByKindTimeStatistics.Average<Kind> average = statistics.getAverage();
        sb.append("Average execution time by call types\nAverage:\n");
        Map<Kind,Pair<BigDecimal,Long>> averageMap = average.getMap();
        List<Kind> kinds = new ArrayList<>(averageMap.keySet());
        Collections.sort(kinds);

        for (Kind kind : kinds) {
            Pair<BigDecimal, Long> pair = averageMap.get(kind);
            if (pair == null) {
                continue;
            }
            sb.append(kind).append(": ")
              .append(ourNumberFormat.format(pair.getFirst().divide(new BigDecimal(pair.getSecond()), RoundingMode.HALF_DOWN)))
              .append(" ms\n");
        }

        List<Timed<AverageByKindTimeStatistics.Data<Kind>>> list = statistics.receiveRecentComplete();
        sb.append("\nRecent calls times:\n");
        for (Timed<AverageByKindTimeStatistics.Data<Kind>> dataTimed : list) {
            sb.append("Statistics at ").append(new Time(dataTimed.getTime()));
            Map<Kind, Pair<Long, Integer>> map = dataTimed.getT().getMap();
            for (Kind kind : kinds) {
                Pair<Long, Integer> pair = map.get(kind);
                if (pair != null) {
                    sb.append(kind).append(": ")
                      .append(ourNumberFormat.format(pair.getFirst() / pair.getSecond()))
                      .append(" ms\n");
                }
            }
        }
    }
}

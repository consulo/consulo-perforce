// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.util.tracer;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.sql.Time;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

class LongCallsPresentation<Kind extends Enum> implements StatisticsPresentation<LongCallsStatistics<Kind>> {
    @Override
    public void putSelf(StringBuilder sb, LongCallsStatistics<Kind> statistics) {
        LongCallsStatistics.AverageData<Kind> averageData = statistics.getAverage();
        SortedMap<Long, LongCallsStatistics.Data<Kind>> averageMap = averageData.getMap();
        sb.append("Long calls\n")
          .append("Average:\n");
        putMap(sb, averageMap);

        sb.append("Recent calls:\n");
        List<Timed<LongCallsStatistics.IntervalData<Kind>>> list = statistics.receiveRecentComplete();
        for (Timed<LongCallsStatistics.IntervalData<Kind>> timed : list) {
            sb.append("Statistics at ").append(new Time(timed.getTime()));
            putMap(sb, timed.getT().getMap());
        }
    }

    private void putMap(StringBuilder sb, SortedMap<Long, LongCallsStatistics.Data<Kind>> map) {
        for (Map.Entry<Long, LongCallsStatistics.Data<Kind>> entry : map.entrySet()) {
            LongCallsStatistics.Data<Kind> data = entry.getValue();
            sb.append("execution time ")
              .append(entry.getKey())
              .append(" ms\nfor: '")
              .append(data.getPresentation())
              .append("'\nat: ");

            Throwable throwable = data.getStackTraceHolder();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PrintStream stream = new PrintStream(baos);
            throwable.printStackTrace(stream);

            sb.append(baos).append('\n');
        }
    }
}

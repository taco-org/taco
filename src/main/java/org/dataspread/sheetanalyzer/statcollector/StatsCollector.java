package org.dataspread.sheetanalyzer.statcollector;

public interface StatsCollector {
    void collectStats(ColumnPattern columnPattern);
    void writeStats();
}

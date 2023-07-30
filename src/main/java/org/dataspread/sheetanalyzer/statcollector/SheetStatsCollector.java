package org.dataspread.sheetanalyzer.statcollector;

import org.dataspread.sheetanalyzer.parser.POIParser;
import org.dataspread.sheetanalyzer.parser.SpreadsheetParser;
import org.dataspread.sheetanalyzer.util.CellContent;
import org.dataspread.sheetanalyzer.util.Ref;
import org.dataspread.sheetanalyzer.util.SheetData;
import org.dataspread.sheetanalyzer.util.SheetNotSupportedException;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;

public class SheetStatsCollector {

    private final HashMap<String, SheetData> sheetDataMap;
    private final StatsCollector[] statsCollectors;
    private final int threshold;

    public SheetStatsCollector(String filePath,
                               StatsCollector[] statsCollectors,
                               int threshold) throws SheetNotSupportedException {
        SpreadsheetParser parser = new POIParser(filePath);
        sheetDataMap = parser.getSheetData();
        this.statsCollectors = statsCollectors;
        this.threshold = threshold;
    }

    public void collectStats() {
        sheetDataMap.forEach((sheetName, sheetData) -> {
            AtomicReference<ColumnPattern> columnPattern = new AtomicReference<>();
            sheetData.getSortedCellContent().forEach(entry -> {
                Ref dep = entry.getKey();
                CellContent cellContent = entry.getValue();
                if (cellContent.getFormulaTokens() != null) {
                    if (columnPattern.get() == null)
                        columnPattern.set(new ColumnPattern(dep, cellContent.getFormulaTokens()));
                    else {
                        boolean compressible = columnPattern.get().compressOneFormula(dep, cellContent.getFormulaTokens());
                        if (!compressible) {
                            collectStatsHelper(statsCollectors, columnPattern.get());
                            columnPattern.set(new ColumnPattern(dep, cellContent.getFormulaTokens()));
                        }
                    }
                }
            });
            if (columnPattern.get() != null)
                collectStatsHelper(statsCollectors, columnPattern.get());
        });
    }

    private void collectStatsHelper(StatsCollector[] statsCollectors, ColumnPattern columnPattern) {
        if (columnPattern.getNumCells() >= threshold) {
            Arrays.stream(statsCollectors).forEach(statsCollector -> statsCollector.collectStats(columnPattern));
        }
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            String warnings = "To run SheetStatsCollector, we need 3 arguments: \n" +
                    "1) Spreadsheet path \n" +
                    "2) Folder path for the output result \n" +
                    "3) Threshold for recognizing a pattern \n";
            System.out.println(warnings);
            System.exit(-1);
        }

        String filePath = args[0];
        String outputFolder = args[1];
        int threshold = Integer.parseInt(args[2]);

        SheetStatsCollector sheetStatsCollector;

        StatsCollector[] statsCollectors = {
                new FuncDistCollector(outputFolder),
                new FuncDistPerCaseCollector(outputFolder)
        };

        File file = new File(filePath);

        try {
            //s System.out.printf("Processing %s\n", file.getName());
            sheetStatsCollector = new SheetStatsCollector(file.getAbsolutePath(), statsCollectors, threshold);
            sheetStatsCollector.collectStats();
        } catch (SheetNotSupportedException e) {
            System.out.printf("Processing %s failed\n", file.getName());
            e.printStackTrace();
        }

        for (StatsCollector statsCollector : statsCollectors)
            statsCollector.writeStats();

    }

}
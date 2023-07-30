package org.dataspread.sheetanalyzer.statcollector;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.dataspread.sheetanalyzer.statcollector.StatsCollectorUtil.*;


public class FuncDistCollector implements StatsCollector {

    private final HashMap<Integer, Integer> numFunctionsFreq;
    private final HashMap<String, Integer> functionFreq;
    private final String numFunctionsFile = "numFunctionsFreq.csv";
    private final String functionFreqFile = "functionFreq.csv";
    private final String statFolder;

    public FuncDistCollector(String statFolder) {
        this.statFolder = statFolder;
        this.numFunctionsFreq = convertToIntInt(loadCSVtoHashMap(createFilePath(statFolder, numFunctionsFile)));
        this.functionFreq = convertToStrInt(loadCSVtoHashMap(createFilePath(statFolder, functionFreqFile))) ;
    }

    @Override
    public void collectStats(ColumnPattern columnPattern) {
        AtomicInteger numFunctions = new AtomicInteger();
        Arrays.stream(columnPattern.getFormulaTokens()).forEach(formulaToken -> {
            if (!formulaToken.isRef()) {
                int freq = functionFreq.getOrDefault(formulaToken.getFunctionStr(), 0);
                functionFreq.put(formulaToken.getFunctionStr(), freq + 1);
                numFunctions.addAndGet(1);
            }
        });
        int freq = numFunctionsFreq.getOrDefault(numFunctions.get(), 0);
        numFunctionsFreq.put(numFunctions.get(), freq + 1);
    }

    @Override
    public void writeStats() {
        try {
            writeHashMapToCSV(numFunctionsFreq, createFilePath(statFolder, numFunctionsFile));
            writeHashMapToCSV(functionFreq, createFilePath(statFolder, functionFreqFile));
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }
}

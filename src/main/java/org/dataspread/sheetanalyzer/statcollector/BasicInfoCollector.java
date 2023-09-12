package org.dataspread.sheetanalyzer.statcollector;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import static org.dataspread.sheetanalyzer.statcollector.StatsCollectorUtil.createFilePath;

public class BasicInfoCollector implements StatsCollector {

    private final String statFolder;
    private final String spreadsheetFile = "Spreadsheet.csv";
    private final String templateFile = "Template.csv";
    private final String functionFile = "Function.csv";
    private final String refFile = "Reference.csv";

    private final String delimiter = ",";
    private final int startID = 1;
    private final int ssID;

    private final List<ColumnPattern> cpList = new LinkedList<>();

    public BasicInfoCollector(String statFolder,
                              String ssName,
                              String dsName) {
        this.statFolder = statFolder;
        this.ssID = getSSID();
    }

    private int getSSID() {
        File file = new File(createFilePath(statFolder, spreadsheetFile));

        // Check if the file exists
        if (file.exists()) {
            try {
                return readLastSSID(file);
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(-1);
            }
        }
        return startID;
    }

    private int readLastSSID(File file) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            String lastLine = null;
            while ((line = br.readLine()) != null) {
                lastLine = line;
            }
            if (lastLine != null)
                return Integer.parseInt(lastLine.split(delimiter)[0]);
            else
                return startID;
        }
    }

    @Override
    public void collectStats(ColumnPattern columnPattern) {
        cpList.add(columnPattern);
    }

    @Override
    public void writeStats() {

    }
}

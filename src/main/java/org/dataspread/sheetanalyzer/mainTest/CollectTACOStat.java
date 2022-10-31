package org.dataspread.sheetanalyzer.mainTest;

import org.dataspread.sheetanalyzer.SheetAnalyzer;
import org.dataspread.sheetanalyzer.dependency.util.DepGraphType;
import org.dataspread.sheetanalyzer.util.Ref;
import org.dataspread.sheetanalyzer.util.SheetNotSupportedException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;

public class CollectTACOStat {
    static String tacoFile = "tacoStat.csv";

    public static void main(String[] args) {

        if (args.length != 3) {
            System.out.println("Need three arguments: \n" +
                    "1) a xls(x) file \n" +
                    "2) updated cell \n" +
                    "3) a folder for stat output \n");
            System.exit(-1);
        }

        File inputFile = new File(args[0]);
        String cellString = args[1];
        Ref ref = MainTestUtil.cellStringToRef(cellString);
        String statFolder = args[2];
        String tacoStatPath = statFolder + "/" + tacoFile;

        boolean inRowCompression = false;
        DepGraphType depGraphType = DepGraphType.TACO;
        try (PrintWriter statPW = new PrintWriter(new FileWriter(tacoStatPath, true))) {

                String filePath = inputFile.getAbsolutePath();
                try {
                    SheetAnalyzer sheetAnalyzer = new SheetAnalyzer(filePath,
                            inRowCompression, depGraphType);
                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append(sheetAnalyzer.getFileName()).append(",")
                            .append(sheetAnalyzer.getNumEdges()).append(",")
                            .append(sheetAnalyzer.getNumCompEdges()).append(",")
                            .append("\"");

                    HashMap<String, String> tacoBreakdownMap = sheetAnalyzer.getTacoBreakdown();
                    tacoBreakdownMap.forEach((sheetName, breakDownstring) -> {
                        stringBuilder.append(breakDownstring).append(":");
                    });
                    stringBuilder.deleteCharAt(stringBuilder.length() - 1);
                    stringBuilder.append("\",");
                    long numDependents =
                            sheetAnalyzer.getSheetNames().stream().mapToLong(sheetName ->
                                    sheetAnalyzer.getNumDependents(sheetName, ref)).sum();
                    long longestPathLength =
                            sheetAnalyzer.getSheetNames().stream().mapToLong(sheetName ->
                                    sheetAnalyzer.getLongestPathLength(sheetName, ref)).sum();
                    stringBuilder.append(cellString).append(",")
                            .append(numDependents).append(",")
                            .append(longestPathLength).append("\n");

                    statPW.print(stringBuilder.toString());

                } catch (SheetNotSupportedException e) {
                    System.out.println(e.getMessage());
                }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

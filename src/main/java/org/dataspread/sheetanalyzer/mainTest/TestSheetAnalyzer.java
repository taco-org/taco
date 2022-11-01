package org.dataspread.sheetanalyzer.mainTest;

import org.apache.poi.openxml4j.exceptions.OpenXML4JRuntimeException;
import org.dataspread.sheetanalyzer.SheetAnalyzer;
import org.dataspread.sheetanalyzer.dependency.util.DepGraphType;
import org.dataspread.sheetanalyzer.dependency.util.PatternType;
import org.dataspread.sheetanalyzer.util.SheetNotSupportedException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class TestSheetAnalyzer {

    public static void main(String[] args) {

        if (args.length != 5) {
            System.out.println("Need four arguments: \n" +
                    "1) Path of input folder \n" +
                    "2) Path of output result (csv) \n" +
                    "3) TACO or NoComp \n" +
                    "4) isDollar True or False \n" +
                    "5) isGap True or False \n"
            );
            System.exit(-1);
        }

        boolean inRowCompression = false;
        DepGraphType depGraphType = MainTestUtil.fromStringToDepGraphType(args[2]);
        boolean isDollar = args[3].equals("True");
        boolean isGap = args[4].equals("True");
        boolean isTypeSensitive = false;

        String statPath = args[1];
        File inputFile = new File(args[0]);
        File [] fileArray;
        if (inputFile.isDirectory()) {
            fileArray = inputFile.listFiles();
        } else {
            fileArray = new File[] {inputFile};
        }

        if (fileArray != null) {
            int counter = 0;
            try (PrintWriter statPW = new PrintWriter(new FileWriter(statPath, false));) {
                // Write headers in stat
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("fileName").append(",")
                        .append("numFormulae").append(",")
                        .append("numVertices").append(",")
                        .append("numEdges").append(",")
                        .append("numCompVertices").append(",")
                        .append("numCompEdges").append(",")
                        .append("graphBuildTime").append(",");

                if (!inRowCompression && depGraphType == DepGraphType.TACO) {
                    long numType = PatternType.values().length;
                    for (int pIdx = 0; pIdx < numType; pIdx++) {
                        stringBuilder.append(PatternType.values()[pIdx].label + "_Comp").append(",")
                                .append(PatternType.values()[pIdx].label + "_NoComp").append(",");
                    }
                }
                stringBuilder.deleteCharAt(stringBuilder.length() - 1).append("\n");
                statPW.write(stringBuilder.toString());

                for (File file: fileArray) {
                    counter += 1;
                    System.out.println("[" + counter + "/" +
                            fileArray.length + "]: "+ "processing " + file.getName());
                    String filePath = file.getAbsolutePath();
                    try {
                        SheetAnalyzer sheetAnalyzer = new SheetAnalyzer(filePath, inRowCompression,
                                depGraphType, isDollar, isGap, isTypeSensitive);
                        MainTestUtil.writePerSheetStat(sheetAnalyzer, statPW, inRowCompression);
                    } catch (SheetNotSupportedException | OutOfMemoryError | NullPointerException | OpenXML4JRuntimeException e) {
                        System.out.println(e);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

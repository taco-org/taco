package org.dataspread.sheetanalyzer.mainTest;

import org.apache.poi.ss.usermodel.*;
import org.dataspread.sheetanalyzer.SheetAnalyzer;
import org.dataspread.sheetanalyzer.dependency.util.DepGraphType;
import org.dataspread.sheetanalyzer.dependency.util.PatternType;
import org.dataspread.sheetanalyzer.util.SheetNotSupportedException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class SheetTest {
    static String filelistColumnName = "File name";
    static boolean isDollar = true;
    static boolean inRowCompression = false;

    public static void main(String[] args) throws IOException {
        if (!checkArgs(args)) {
            String warning = "To run SheetTest, we need 7 arguments: \n" +
                    "1) Path of a xls(x) file containing 'File name' \n" +
                    "2) SheetName \n" +
                    "3) Path of output result \n" +
                    "4) File directory \n" +
                    "5) File name ('all' for all files in list) \n" +
                    "6) TACO or NoComp or Antifreeze\n" +
                    "7) IsGap True or False \n";
            System.out.println(warning);
            System.exit(-1);
        }

        String inputPath = args[0];
        String sheetName = args[1];
        String outputPath = args[2];
        String fileDir = args[3];
        String targetFileName = args[4];

        DepGraphType depGraphType = MainTestUtil.fromStringToDepGraphType(args[5]);
        boolean isGap = args[6].equals("True");

        List<String> fileNameList = parseInputFile(inputPath, sheetName, filelistColumnName);

        if (fileNameList.size() == 0) {
            System.out.println("Failed to parse " + inputPath);
            System.exit(-1);
        }

        int counter = 0;
        try (PrintWriter statPW = new PrintWriter(new FileWriter(outputPath, true))) {
            // Write headers in stat
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("fileName").append(",")
                    .append("numFormulae").append(",")
                    .append("numVertices").append(",")
                    .append("numEdges").append(",")
                    .append("numCompVertices").append(",")
                    .append("numCompEdges").append(",")
                    .append("graphBuildTime").append(",");
            if (!inRowCompression) {
                if (depGraphType == DepGraphType.TACO) {
                    long numType = PatternType.values().length;
                    for (int pIdx = 0; pIdx < numType; pIdx++) {
                        stringBuilder.append(PatternType.values()[pIdx].label + "_Comp").append(",")
                                .append(PatternType.values()[pIdx].label + "_NoComp").append(",");
                    }
                }
            }
            stringBuilder.deleteCharAt(stringBuilder.length() - 1).append("\n");
            statPW.write(stringBuilder.toString());

            if (!targetFileName.equals("all")) {
                if (fileNameList.contains(targetFileName)) {
                    System.out.println("[1/1]: processing " + targetFileName);
                    String filePath = fileDir + "/" + targetFileName;
                    try {
                        SheetAnalyzer sheetAnalyzer = new SheetAnalyzer(filePath, inRowCompression,
                                depGraphType, isDollar, isGap);
                        MainTestUtil.writePerSheetStat(sheetAnalyzer, statPW, inRowCompression);
                    } catch (SheetNotSupportedException | OutOfMemoryError | NullPointerException e) {
                        System.out.println(e.getMessage());
                    }
                } else {
                    System.out.println("Cannot find target filename in DepRefMap");
                    System.exit(-1);
                }
            } else {
                for (String fileName: fileNameList) {
                    counter += 1;
                    System.out.println("[" + counter + "/" + fileNameList.size() + "]: " + "processing " + fileName);
                    String filePath = fileDir + "/" + fileName;
                    try {
                        SheetAnalyzer sheetAnalyzer = new SheetAnalyzer(filePath, inRowCompression,
                                depGraphType, isDollar, isGap);
                        MainTestUtil.writePerSheetStat(sheetAnalyzer, statPW, inRowCompression);
                    } catch (SheetNotSupportedException | OutOfMemoryError | NullPointerException e) {
                        System.out.println(e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static List<String> parseInputFile(String filePath,
                                               String sheetName,
                                               String filelistColumnName) throws IOException {
        ArrayList<String> fileNameList = new ArrayList<>();

        File inputFile = new File(filePath);
        Workbook workbook = WorkbookFactory.create(inputFile);
        Sheet sheet = workbook.getSheet(sheetName);
        int maxRows = 0;
        int maxCols = 0;
        for (Row row : sheet) {
            for (Cell cell : row)
                if (cell.getColumnIndex() > maxCols) maxCols = cell.getColumnIndex();
            if (row.getRowNum() > maxRows) maxRows = row.getRowNum();
        }

        // Find the column index
        int fileColumnIndex = -1;
        for (int j = 0; j < maxCols; j++) {
            Row row = sheet.getRow(0);
            if (row != null) {
                Cell cell = row.getCell(j);
                if (cell.getCellType() == CellType.STRING) {
                    if (cell.getStringCellValue().equals(filelistColumnName)) {
                        fileColumnIndex = j;
                    }
                }
            }
        }

        if (fileColumnIndex == -1) {
            System.out.println("Cannot find " + filelistColumnName);
            return fileNameList;
        }

        for (int i = 1; i <= maxRows; i++) {
            String fileName = null;
            Row row = sheet.getRow(i);
            if (row != null) {
                Cell fileNameCell = row.getCell(fileColumnIndex);
                if (fileNameCell.getCellType() == CellType.STRING) {
                    fileName = fileNameCell.getStringCellValue();
                }
            }
            if (fileName != null) {
                fileNameList.add(fileName);
            }
        }

        return fileNameList;
    }

    private static boolean checkArgs(String[] args) {
        if (args.length != 7) {
            System.out.println("Incorrect length!");
            return false;
        }

        File inputFile = new File(args[0]);
        File fileDir = new File(args[3]);
        if (!inputFile.exists() || !fileDir.exists()) {
            System.out.println("Wrong file path!");
            return false;
        }

        if (!(args[5].equals("TACO") || args[5].equals("NoComp"))) {
            System.out.println("Wrong model type!");
            return false;
        }

        return true;
    }
}


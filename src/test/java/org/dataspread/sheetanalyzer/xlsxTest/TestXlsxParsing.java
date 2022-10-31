package org.dataspread.sheetanalyzer.xlsxTest;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.dataspread.sheetanalyzer.SheetAnalyzer;
import org.dataspread.sheetanalyzer.util.Ref;
import org.dataspread.sheetanalyzer.util.RefImpl;
import org.dataspread.sheetanalyzer.util.SheetNotSupportedException;
import org.dataspread.sheetanalyzer.util.TestUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class TestXlsxParsing {
    private static SheetAnalyzer sheetAnalyzer;
    private static final String sheetName = "XLSXSheet";
    private static final int maxRows = 2;

    private static File createXLSXSheet() throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet(sheetName);
        int colA = 0, colB = 1;
        for (int i = 0; i < maxRows; i++) {
            Row row = sheet.createRow(i);
            Cell cellA = row.createCell(colA);
            Cell cellB = row.createCell(colB);
            cellA.setCellValue(1);
            cellB.setCellFormula("SUM(A" + (i + 1) + ":" + "A" + (i + 2) + ")");
        }
        Row row = sheet.createRow(maxRows);
        row.createCell(colA);
        row.createCell(colB);
        row = sheet.createRow(maxRows + 1);
        Cell cell = row.createCell(colA);
        cell.setCellValue("Let it parse a string");

        File xlsxTempFile = TestUtil.createXlsxTempFile();
        FileOutputStream outputStream = new FileOutputStream(xlsxTempFile);
        workbook.write(outputStream);
        workbook.close();

        return xlsxTempFile;
    }

    @BeforeAll
    public static void setUp() throws IOException, SheetNotSupportedException {
        File xlsxTempFile = createXLSXSheet();
        sheetAnalyzer = new SheetAnalyzer(xlsxTempFile.getAbsolutePath());
    }

    @Test
    public void verifyDependencyA() {
        int queryRow = 1, queryColumn = 0;
        Ref queryRef = new RefImpl(queryRow, queryColumn);
        Set<Ref> queryResult = sheetAnalyzer.getDependents(sheetName, queryRef);

        Set<Ref> groundTruth = new HashSet<>();
        int firstRow = 0, firstColumn = 1;
        int lastRow = 1, lastColumn = 1;
        groundTruth.add(new RefImpl(firstRow, firstColumn, lastRow, lastColumn));

        Assertions.assertTrue(TestUtil.hasSameRefs(groundTruth, queryResult));
    }
}

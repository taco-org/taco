package org.dataspread.sheetanalyzer.tacoTest;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
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

public class TestFRPattern {

  private static SheetAnalyzer sheetAnalyzer;
    private static final String sheetName = "FRSheet";
    private static final int maxRows = 1000;

    private static File createFRSheet() throws IOException {
        Workbook workbook = new HSSFWorkbook();
        Sheet sheet = workbook.createSheet(sheetName);
        int colA = 0, colB = 1, colC = 2;
        for (int i = 0; i < maxRows; i++) {
            Row row = sheet.createRow(i);
            Cell cellA = row.createCell(colA);
            Cell cellB = row.createCell(colB);
            Cell cellC = row.createCell(colC);
            cellA.setCellValue(i+1);
            cellB.setCellValue(10);
            cellC.setCellFormula("SUM(A1:" + "B" + (i + 1) + ")");
        }
        TestUtil.createAnEmptyRowWithTwoCols(sheet, maxRows, colA, colB);

        File xlsTempFile = TestUtil.createXlsTempFile();
        FileOutputStream outputStream = new FileOutputStream(xlsTempFile);
        workbook.write(outputStream);
        workbook.close();

        return xlsTempFile;
    }

    @BeforeAll
    public static void setUp() throws IOException, SheetNotSupportedException {
        File xlsTempFile = createFRSheet();
        sheetAnalyzer = new SheetAnalyzer(xlsTempFile.getAbsolutePath());
    }

    /**
     * The first row of FR pattern should be referenced by every row of column C.
     */
    @Test
    public void verifyFirstRowDependency() {
        int queryRow = 0, queryColumn = 0;
        Ref queryRef = new RefImpl(queryRow, queryColumn);
        Set<Ref> queryResult = sheetAnalyzer.getDependents(sheetName, queryRef);

        Set<Ref> groundTruth = new HashSet<>();
        int firstRow = 0, firstColumn = 2;
        int lastRow = maxRows - 1, lastColumn = 2;
        groundTruth.add(new RefImpl(firstRow, firstColumn, lastRow, lastColumn));

        Assertions.assertTrue(TestUtil.hasSameRefs(groundTruth, queryResult));
    }

    /**
     * The last row of FR pattern is only referenced by the last row of column C.
     */
    @Test
    public void verifyLastRowDependency() {
        int queryRow = maxRows - 1, queryColumn = 0;
        Ref queryRef = new RefImpl(queryRow, queryColumn);
        Set<Ref> queryResult = sheetAnalyzer.getDependents(sheetName, queryRef);

        Set<Ref> groundTruth = new HashSet<>();
        int firstRow = maxRows - 1, firstColumn = 2;
        groundTruth.add(new RefImpl(firstRow, firstColumn));

        Assertions.assertTrue(TestUtil.hasSameRefs(groundTruth, queryResult));
    }
}

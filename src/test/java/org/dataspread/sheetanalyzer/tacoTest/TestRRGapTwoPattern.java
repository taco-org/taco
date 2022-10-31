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

public class TestRRGapTwoPattern {
    private static SheetAnalyzer sheetAnalyzer;
    private static final String sheetName = "RRGapTwoSheet";
    private static final int maxRows = 1000;
    private static final int gapSize = 2;

    private static File createRRGapTwoSheet() throws IOException {
        Workbook workbook = new HSSFWorkbook();
        Sheet sheet = workbook.createSheet(sheetName);
        int colA = 0, colB = 1;
        for (int i = 0; i < maxRows; i++) {
            Row row = sheet.createRow(i);
            Cell cellA = row.createCell(colA);
            Cell cellB = row.createCell(colB);
            cellA.setCellValue(1);
            if (i % (gapSize + 1) == 0) {
                cellB.setCellFormula("SUM(A" + (i + 1) + ":" + "A" + (i + 2) + ")");
            }
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
        File xlsTempFile = createRRGapTwoSheet();
        sheetAnalyzer = new SheetAnalyzer(xlsTempFile.getAbsolutePath());
    }

    @Test
    public void verifyDependencyA() {
        int queryRow = 1, queryColumn = 0;
        Ref queryRef = new RefImpl(queryRow, queryColumn);
        Set<Ref> queryResult = sheetAnalyzer.getDependents(sheetName, queryRef);

        Set<Ref> groundTruth = new HashSet<>();
        int firstRow = 0, firstColumn = 1;
        int lastRow = 0, lastColumn = 1;
        groundTruth.add(new RefImpl(firstRow, firstColumn, lastRow, lastColumn));

        Assertions.assertTrue(TestUtil.hasSameRefs(groundTruth, queryResult));
    }

    @Test
    public void verifyDependencyB() {
        int queryRow = 0, queryColumn = 0, queryLastRow = 3, queryLastColumn = 0;
        Ref queryRef = new RefImpl(queryRow, queryColumn,
                queryLastRow, queryLastColumn);
        Set<Ref> queryResult = sheetAnalyzer.getDependents(sheetName, queryRef);

        Set<Ref> groundTruth = new HashSet<>();
        int rowFirst = 0, colFirst = 1;
        int rowSecond = 3, colSecond = 1;
        groundTruth.add(new RefImpl(rowFirst, colFirst));
        groundTruth.add(new RefImpl(rowSecond, colSecond));

        Assertions.assertTrue(TestUtil.hasSameRefs(groundTruth, queryResult));
    }

    @Test
    public void verifyDependencyC() {
        int queryRow = 0, queryColumn = 0;
        Ref queryRef = new RefImpl(queryRow, queryColumn);
        Set<Ref> queryResult = sheetAnalyzer.getDependents(sheetName, queryRef);

        Set<Ref> groundTruth = new HashSet<>();
        int firstRow = 0, firstColumn = 1;
        int lastRow = 0, lastColumn = 1;
        groundTruth.add(new RefImpl(firstRow, firstColumn, lastRow, lastColumn));

        Assertions.assertTrue(TestUtil.hasSameRefs(groundTruth, queryResult));
    }
}

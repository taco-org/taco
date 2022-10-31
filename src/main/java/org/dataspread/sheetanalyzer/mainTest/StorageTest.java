package org.dataspread.sheetanalyzer.mainTest;

import org.apache.poi.ss.usermodel.*;
import org.dataspread.sheetanalyzer.SheetAnalyzer;
import org.dataspread.sheetanalyzer.dependency.DependencyGraphTACO;
import org.dataspread.sheetanalyzer.dependency.util.DepGraphType;
import org.dataspread.sheetanalyzer.dependency.util.PatternType;
import org.dataspread.sheetanalyzer.util.Ref;
import org.dataspread.sheetanalyzer.util.RefImpl;
import org.dataspread.sheetanalyzer.util.SheetNotSupportedException;

import java.io.*;
import java.util.HashMap;
import java.util.Set;

public class StorageTest {
    public static void main(String[] args) throws SheetNotSupportedException, IOException {
        boolean inRowCompression = false;
        DepGraphType depGraphType = DepGraphType.TACO;
        boolean isDollar = true;
        boolean isGap = false;
        String targetFile = "./tested_sheets/enron_long1.xls";
        SheetAnalyzer sheetAnalyzer = new SheetAnalyzer(targetFile, inRowCompression,
                depGraphType, isDollar, isGap);
        DependencyGraphTACO depGraph = (DependencyGraphTACO)sheetAnalyzer.getDependencyGraphs().get("Test");
        Ref targetRef = new RefImpl(0, 0, 10, 10);
        HashMap<Ref, Set<Ref>> result = depGraph.getDependents(targetRef, false);
        System.out.println(result);
    }
}

package org.dataspread.sheetanalyzer.parser;

import org.dataspread.sheetanalyzer.util.SheetData;

import java.util.HashMap;

public interface SpreadsheetParser {
    public String getFileName();
    public HashMap<String, SheetData> getSheetData();
    public boolean skipParsing(int threshold);
}

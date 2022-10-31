package org.dataspread.sheetanalyzer.util;

public class SheetNotSupportedException extends Exception {
    public SheetNotSupportedException() {}

    public SheetNotSupportedException(String message) {
        super(message);
    }
}

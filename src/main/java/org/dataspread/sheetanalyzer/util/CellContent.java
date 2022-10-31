package org.dataspread.sheetanalyzer.util;

public class CellContent {
    final String value;
    final String formula;
    final boolean isFormula;

    public CellContent (String value, String formula, boolean isFormula) {
        this.value = value;
        this.formula = formula;
        this.isFormula = isFormula;
    }

    public String getValue() {
        return value;
    }

    public String getFormula() {
        return formula;
    }

    public boolean isFormula() {
        return isFormula;
    }

    public static CellContent getNullCellContent() {
        return new CellContent("", "", false);
    }
}

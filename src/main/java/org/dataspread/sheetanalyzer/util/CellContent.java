package org.dataspread.sheetanalyzer.util;

public class CellContent {
    final String value;
    final String formula;
    final FormulaToken[] formulaTokens;
    final boolean isFormula;

    public CellContent (String value, String formula, FormulaToken[] formulaTokens, boolean isFormula) {
        this.value = value;
        this.formula = formula;
        this.formulaTokens = formulaTokens;
        this.isFormula = isFormula;
    }

    public String getValue() {
        return value;
    }

    public String getFormula() {
        return formula;
    }

    public FormulaToken[] getFormulaTokens() {
        return formulaTokens;
    }

    public boolean isFormula() {
        return isFormula;
    }

    public static CellContent getNullCellContent() {
        return new CellContent("", "", null, false);
    }
}

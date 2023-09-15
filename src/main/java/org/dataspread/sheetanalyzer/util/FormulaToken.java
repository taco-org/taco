package org.dataspread.sheetanalyzer.util;

public class FormulaToken {
    private final Ref ref;
    private final String functionStr;
    private final int numOperands;

    public FormulaToken(Ref ref, String functionStr, int numOperands) {
        this.ref = ref;
        this.functionStr = functionStr;
        this.numOperands = numOperands;
    }

    public Ref getRef() {
        return ref;
    }

    public String getFunctionStr() {
        return functionStr;
    }

    public int getNumOperands() {
        return numOperands;
    }

    public boolean isRef() {
        return ref != null;
    }

    @Override
    public String toString() {
       if (isRef()) {
           if (ref.isConstant()) return ref.getScalarValue();
           else return ref.toString();
       } else {
           return functionStr;
       }
    }

}

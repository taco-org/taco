package org.dataspread.sheetanalyzer.statcollector;

import org.dataspread.sheetanalyzer.dependency.util.*;
import org.dataspread.sheetanalyzer.util.FormulaToken;
import org.dataspread.sheetanalyzer.util.Pair;
import org.dataspread.sheetanalyzer.util.Ref;

import java.util.ArrayList;

import static org.dataspread.sheetanalyzer.dependency.util.PatternTools.*;

public class ColumnPattern {
    private Ref dep;
    private final FormulaToken[] formulaTokens;
    private final EdgeMeta[] edgeMetas;

    public ColumnPattern(Ref dep, FormulaToken[] formulaTokens) {
        this.dep = dep;
        this.formulaTokens = formulaTokens;
        this.edgeMetas = new EdgeMeta[formulaTokens.length];
        for (int i = 0; i < this.formulaTokens.length; i++) {
            if (this.formulaTokens[i].isRef()) {
                Ref ref = this.formulaTokens[i].getRef();

                this.edgeMetas[i] = new EdgeMeta(PatternType.NOTYPE, Offset.noOffset, Offset.noOffset);
                this.edgeMetas[i].isConstant = ref.isConstant();
            }
        }
    }

    public int getNumCells() {
        return dep.getCellCount();
    }

    public FormulaToken[] getFormulaTokens() {
        return formulaTokens;
    }

    public EdgeMeta[] getEdgeMetas() {
        return edgeMetas;
    }

    public boolean compressOneFormula(Ref inputDep, FormulaToken[] inputFormulaTokens) {
        if (this.dep.getColumn() != inputDep.getColumn() || this.dep.getLastRow() + 1 != inputDep.getRow()) {
            return false;
        }

        if (this.formulaTokens.length != inputFormulaTokens.length)
            return false;

        ArrayList<CompressInfo> compressInfos = new ArrayList<>();
        for (int i = 0; i < this.formulaTokens.length; i++) {
            CompressInfo oneCompressInfo = compressOneToken(this.dep, this.formulaTokens[i], this.edgeMetas[i],
                    inputDep, inputFormulaTokens[i]);
            if (oneCompressInfo.compType == PatternType.NOTYPE) return false;
            compressInfos.add(oneCompressInfo);
        }

        this.dep = this.dep.getBoundingBox(inputDep);
        for (int i = 0; i < this.formulaTokens.length; i++) {
            if (this.formulaTokens[i].isRef()) {
                CompressInfo oneCompressInfo = compressInfos.get(i);
                Ref newPrec = oneCompressInfo.prec.getBoundingBox(oneCompressInfo.candPrec);
                Pair<Offset, Offset> offsetPair = computeOffset(newPrec, this.dep, oneCompressInfo.compType);
                EdgeMeta newMeta = new EdgeMeta(oneCompressInfo.compType, offsetPair.first, offsetPair.second);
                newMeta.isConstant = this.edgeMetas[i].isConstant;

                this.formulaTokens[i] = new FormulaToken(newPrec, "", 0);
                this.edgeMetas[i] = newMeta;
            }
        }

        return true;
    }

    private CompressInfo compressOneToken(Ref candDep, FormulaToken formulaToken, EdgeMeta metaData,
                                            Ref inputDep, FormulaToken inputFormulaToken) {
        if (formulaToken.isRef() != inputFormulaToken.isRef())
            return CompressInfo.getUncompressInfo();

        if (!formulaToken.isRef() && !inputFormulaToken.isRef()) {
            if (formulaToken.getFunctionStr().compareToIgnoreCase(inputFormulaToken.getFunctionStr()) == 0) {
                return new CompressInfo(formulaToken.getFunctionStr());
            } else
                return CompressInfo.getUncompressInfo();
        }

        Direction direction = Direction.TODOWN;
        Ref candPrec = formulaToken.getRef();
        Ref inputPrec = inputFormulaToken.getRef();
        PatternType curCompType = metaData.patternType;

        Ref lastCandPrec = findLastPrec(candPrec, candDep, metaData, direction);
        PatternType compressType = PatternType.NOTYPE;

        if (isCompressibleTypeOne(lastCandPrec, inputPrec, direction)) {
            compressType = PatternType.TYPEONE;
        } else if (isCompressibleTypeTwo(lastCandPrec, inputPrec, direction))
            compressType = PatternType.TYPETWO;
        else if (isCompressibleTypeThree(lastCandPrec, inputPrec, direction))
            compressType = PatternType.TYPETHREE;
        else if (isCompressibleTypeFour(lastCandPrec, inputPrec))
            compressType = PatternType.TYPEFOUR;

        PatternType retCompType = PatternType.NOTYPE;
        if (curCompType == PatternType.NOTYPE) retCompType = compressType;
        else if (curCompType == compressType) retCompType = compressType;

        return new CompressInfo(false, direction, retCompType,
                inputPrec, inputDep, candPrec, candDep, metaData);
    }

    public String getTemplateString() {
        StringBuilder sb = new StringBuilder();
        for (FormulaToken ft : this.formulaTokens) {
            sb.append(ft.toString());
        }
        return sb.toString();
    }

    public int getNumFunc() {
        int numFunc = 0;
        for (FormulaToken ft : this.formulaTokens) {
            if (!ft.isRef()) numFunc += 1;
        }
        return numFunc;
    }

}

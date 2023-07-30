package org.dataspread.sheetanalyzer.dependency.util;

import org.dataspread.sheetanalyzer.util.Ref;

public class CompressInfo {
    public Boolean isDuplicate;
    public Direction direction;
    public PatternType compType;
    public Ref prec;
    public Ref dep;
    public Ref candPrec;
    public Ref candDep;
    public EdgeMeta edgeMeta;
    public String functionStr;

    public CompressInfo(Boolean isDuplicate,
                        Direction direction,
                        PatternType compType,
                        Ref prec, Ref dep,
                        Ref candPrec, Ref candDep, EdgeMeta edgeMeta) {
        this.isDuplicate = isDuplicate;
        this.direction = direction;
        this.compType = compType;
        this.prec = prec;
        this.dep = dep;
        this.candPrec = candPrec;
        this.candDep = candDep;
        this.edgeMeta = edgeMeta;
    }

    public CompressInfo(String functionStr) {
        this.functionStr = functionStr;
        this.compType = PatternType.TYPEFOUR;
    }

    public static CompressInfo getUncompressInfo() {
        return new CompressInfo(false, Direction.NODIRECTION, PatternType.NOTYPE,
                null, null, null, null, null);
    }
}

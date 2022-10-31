package org.dataspread.sheetanalyzer.dependency.util;

import com.github.davidmoten.rtree.geometry.Rectangle;
import com.github.davidmoten.rtree.geometry.internal.RectangleFloat;
import org.dataspread.sheetanalyzer.util.Ref;
import org.dataspread.sheetanalyzer.util.RefImpl;

import java.util.*;


public class RefUtils {
    public static boolean isValidRef(Ref ref) {
        return (ref.getRow() >= 0 &&
                ref.getColumn() >=0 &&
                ref.getRow() <= ref.getLastRow() &&
                ref.getColumn() <= ref.getLastColumn());
    }

    public static Rectangle refToRect(Ref ref)
    {
        return RectangleFloat.create(ref.getRow(),ref.getColumn(),
                (float) 0.5 + ref.getLastRow(), (float) 0.5 + ref.getLastColumn());
    }

    public static Ref coordToRef(Ref ref, int firstRow, int firstCol,
                           int lastRow, int lastCol) {
        return new RefImpl(ref.getBookName(), ref.getSheetName(),
                firstRow, firstCol, lastRow, lastCol);
    }

    public static Offset refToOffset(Ref prec, Ref dep, boolean isStart) {
        if (isStart) {
            return new Offset(dep.getRow() - prec.getRow(),
                    dep.getColumn() - prec.getColumn());
        } else {
            return new Offset(dep.getLastRow() - prec.getLastRow(),
                    dep.getLastColumn() - prec.getLastColumn());
        }
    }

    public static Ref fromStringToCell(String cellStr) {
        String[] content = cellStr.split(":");
        String rowAndColumn = content[1];
        StringBuilder rowStr = new StringBuilder();
        StringBuilder colStr = new StringBuilder();
        for (int i = 0; i < rowAndColumn.length(); i++) {
            char s = rowAndColumn.charAt(i);
            if (Character.isDigit(s)) {
                rowStr.append(s);
            } else {
                colStr.append(s);
            }
        }

        String col = colStr.toString();
        int rowIdx = Integer.parseInt(rowStr.toString()) - 1;

        int colIdx = 0;
        char[] colChars = col.toLowerCase().toCharArray();
        for (int i = 0; i < colChars.length; i++) {
            colIdx += (colChars[i] - 'a' + 1) * Math.pow(26, colChars.length - i - 1);
        }
        colIdx -= 1;
        return new RefImpl(rowIdx, colIdx);
    }

    public static Set<Ref> postProcessRefSet(Set<Ref> result) {
        Set<Ref> newColumnResult = new HashSet<>();
        Set<Ref> newRowResult = new HashSet<>();

        ArrayList<Ref> resultArray = new ArrayList<>(result);
        mergeRefArray(resultArray, newColumnResult, false);
        resultArray = new ArrayList<>(newColumnResult);
        mergeRefArray(resultArray, newRowResult, true);

        return newRowResult;
    }

    private static void mergeRefArray(ArrayList<Ref> refArr, Set<Ref> refSet, boolean isHorizontal) {
        if (!isHorizontal) {
            Collections.sort(refArr, new Comparator<Ref>() {
                @Override
                public int compare(Ref o1, Ref o2) {
                    if (o1.getRow() != o2.getRow()) {
                        return o1.getRow() - o2.getRow();
                    } else {
                        return o1.getColumn() - o2.getColumn();
                    }
                }
            });
        } else {
            Collections.sort(refArr, new Comparator<Ref>() {
                @Override
                public int compare(Ref o1, Ref o2) {
                    if (o1.getColumn() != o2.getColumn()) {
                        return o1.getColumn() - o2.getColumn();
                    } else {
                        return o1.getRow() - o2.getRow();
                    }
                }
            });
        }

        int idx = 0;
        while (idx < refArr.size()) {
            Ref mergedRef = refArr.get(idx);
            int nextIdx = idx + 1;
            while (nextIdx < refArr.size()) {
                Ref refNext = refArr.get(nextIdx);
                if (isHorizontal) {
                    if (!isHorizontalMergable(mergedRef, refNext)) {
                        break;
                    }
                } else {
                    if (!isVerticalMergable(mergedRef, refNext)) {
                        break;
                    }
                }
                mergedRef = mergeRef(mergedRef, refNext);
                nextIdx += 1;
            }
            refSet.add(mergedRef);
            idx = nextIdx;
        }
    }

    private static Ref mergeRef(Ref ref, Ref refNext) {
        int newLastRow = Math.max(ref.getLastRow(), refNext.getLastRow());
        int newLastColumn = Math.max(ref.getLastColumn(), refNext.getLastColumn());

        Ref newRef = new RefImpl(ref.getBookName(), ref.getSheetName(),
                ref.getRow(), ref.getColumn(), newLastRow, newLastColumn);

        if (ref.getPrecedents() != null) {
            for (Ref r: ref.getPrecedents()) {
                newRef.addPrecedent(r);
            }
        }
        if (refNext.getPrecedents() != null) {
            for (Ref r: refNext.getPrecedents()) {
                ref.addPrecedent(r);
            }
        }
        return newRef;
    }

    private static boolean isVerticalMergable(Ref ref, Ref refNext) {
        if (!ref.getSheetName().equals(refNext.getSheetName())) {
            return false;
        }

        // Same column
        if (ref.getColumn() == refNext.getColumn() && ref.getLastColumn() == refNext.getLastColumn()) {
            if (refNext.getRow() <= ref.getLastRow() + 1) {
                return true;
            }
        }
        return false;
    }

    private static boolean isHorizontalMergable(Ref ref, Ref refNext) {
        if (!ref.getSheetName().equals(refNext.getSheetName())) {
            return false;
        }

        // Same row
        if (ref.getRow() == refNext.getRow() && ref.getLastRow() == refNext.getLastRow()) {
            if (refNext.getColumn() <= ref.getLastColumn() + 1) {
                return true;
            }
        }
        return false;
    }
}

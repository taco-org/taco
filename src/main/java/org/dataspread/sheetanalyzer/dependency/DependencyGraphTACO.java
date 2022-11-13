package org.dataspread.sheetanalyzer.dependency;

import com.github.davidmoten.rtree.Entry;
import com.github.davidmoten.rtree.RTree;
import com.github.davidmoten.rtree.geometry.Rectangle;
import com.google.common.base.Function;
import com.google.common.collect.Iterators;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.dataspread.sheetanalyzer.dependency.util.*;
import org.dataspread.sheetanalyzer.util.Pair;
import org.dataspread.sheetanalyzer.util.Ref;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.dataspread.sheetanalyzer.dependency.util.PatternTools.*;

public class DependencyGraphTACO implements DependencyGraph, Serializable {

    /** Map<dependant, precedent> */
    protected HashMap<Ref, List<RefWithMeta>> precToDepList = new HashMap<>();
    protected HashMap<Ref, List<RefWithMeta>> depToPrecList = new HashMap<>();

    private RTree<Ref, Rectangle> _rectToRef = RTree.create();

    private boolean doCompression = true;
    private boolean inRowCompression = false;
    private boolean dollar_signed = false;
    private boolean isGap = true;
    private boolean isTypeSensitive = false;

    private final CompressInfoComparator compressInfoComparator = new CompressInfoComparator();
    private final DollarSignedCompressInfoComparator dollarSignedCompressInfoComparator =
            new DollarSignedCompressInfoComparator();

    public HashMap<Ref, List<RefWithMeta>> getCompressedGraph() {
        return precToDepList;
    }

    public HashMap<Ref, List<RefWithMeta>> getDependents(Ref precedent, boolean isDirectDep) {
        HashMap<Ref, List<RefWithMeta>> results = new HashMap<>();
        if (RefUtils.isValidRef(precedent)) {
            results = getDependentsInternal(precedent, isDirectDep);
        }
        return results;
    }

    private HashMap<Ref, List<RefWithMeta>> getDependentsInternal(Ref precedent, boolean isDirectDep) {
        HashMap<Ref, Set<RefWithMeta>> results = new HashMap<>();
        AtomicReference<RTree<Ref, Rectangle>> resultSet = new AtomicReference<>(RTree.create());
        Queue<Ref> updateQueue = new LinkedList<>();
        updateQueue.add(precedent);
        while (!updateQueue.isEmpty()) {
            Ref updateRef = updateQueue.remove();
            Iterator<Ref> refIter = findOverlappingRefs(updateRef);
            Set<Ref> visited = new HashSet<>();
            while (refIter.hasNext()) {
                Ref overlappingPrecRef = refIter.next();
                if (!visited.contains(overlappingPrecRef)) {
                    visited.add(overlappingPrecRef);
                    Ref realUpdateRef = updateRef.getOverlap(overlappingPrecRef);
                    findDeps(overlappingPrecRef).forEach(depRefWithMeta -> {
                        Set<RefWithMeta> depUpdateRefSet = findUpdateDepRefWithMeta(overlappingPrecRef,
                                depRefWithMeta, realUpdateRef);
                        List<RefWithMeta> tempResults = new ArrayList<>();
                        for (RefWithMeta depRef : depUpdateRefSet) {
                            tempResults.add(depRef);
                            if (!isDirectDep) {
                                LinkedList<Ref> nonOverlapDepRef = getNonOverlapRef(resultSet.get(), depRef.getRef());
                                Set<Ref> depRefSet = new HashSet<>(nonOverlapDepRef);
                                for (Ref nolref: depRefSet) {
                                    resultSet.set(resultSet.get().add(nolref, RefUtils.refToRect(nolref)));
                                    updateQueue.add(nolref);
                                }
                            }
                        }
                        if (!tempResults.isEmpty()) {
                            Set<RefWithMeta> oldResults = results.getOrDefault(updateRef, new HashSet<>());
                            oldResults.addAll(tempResults);
                            results.put(updateRef, oldResults);
                        }
                    });
                }
            }
        }
        HashMap<Ref, List<RefWithMeta>> finalResult = new HashMap<>();
        for (Ref ref: results.keySet()) {
            List<RefWithMeta> depList = new ArrayList<>(results.get(ref));
            finalResult.put(ref, depList);
        }
        return finalResult;
    }

    public HashMap<Ref, List<RefWithMeta>> getPrecedents(Ref dependent, boolean isDirectPrec) {
        HashMap<Ref, List<RefWithMeta>> results = new HashMap<>();
        if (RefUtils.isValidRef(dependent)) {
            results = getPrecedentsInternal(dependent, isDirectPrec);
        }
        return results;
    }

    private HashMap<Ref, List<RefWithMeta>> getPrecedentsInternal(Ref dependent, boolean isDirectPrec) {
        HashMap<Ref, Set<RefWithMeta>> results = new HashMap<>();
        AtomicReference<RTree<Ref, Rectangle>> resultSet = new AtomicReference<>(RTree.create());
        Queue<Ref> updateQueue = new LinkedList<>();
        updateQueue.add(dependent);
        while (!updateQueue.isEmpty()) {
            Ref updateRef = updateQueue.remove();
            Iterator<Ref> refIter = findOverlappingRefs(updateRef);
            Set<Ref> visited = new HashSet<>();
            while (refIter.hasNext()) {
                Ref overlappingDepRef = refIter.next();
                if (!visited.contains(overlappingDepRef)) {
                    visited.add(overlappingDepRef);
                    Ref realUpdateRef = updateRef.getOverlap(overlappingDepRef);
                    findPrecs(overlappingDepRef).forEach(precRefWithMeta -> {
                        RefWithMeta precUpdateRef = findUpdatePrecRefWithMeta(overlappingDepRef, precRefWithMeta,
                                realUpdateRef, isDirectPrec);
                        if (!isDirectPrec) {
                            LinkedList<Ref> nonOverlapPrecRef = getNonOverlapRef(resultSet.get(), precUpdateRef.getRef());
                            Set<Ref> precRefSet = new HashSet<>(nonOverlapPrecRef);
                            for (Ref nolref: precRefSet) {
                                resultSet.set(resultSet.get().add(nolref, RefUtils.refToRect(nolref)));
                                updateQueue.add(nolref);
                            }
                        }
                        Set<RefWithMeta> oldResults = results.getOrDefault(updateRef, new HashSet<>());
                        oldResults.add(precUpdateRef);
                        results.put(updateRef, oldResults);
                    });
                }
            }
        }
        HashMap<Ref, List<RefWithMeta>> finalResult = new HashMap<>();
        for (Ref ref: results.keySet()) {
            List<RefWithMeta> precList = new ArrayList<>(results.get(ref));
            finalResult.put(ref, precList);
        }
        return finalResult;
    }

    private Boolean isContained(RTree<Ref, Rectangle> resultSet, Ref input) {
        return resultSet.search(getRectangleFromRef(input)).exists(entry -> {
                    Ref ref = entry.value();
                    return isSubsume(ref, input);
        }).toBlocking().single();
    }

    private LinkedList<Ref> getNonOverlapRef(RTree<Ref, Rectangle> resultSet, Ref input) {
        LinkedList<Ref> retRefList = new LinkedList<>();
        retRefList.addLast(input);
        resultSet.search(getRectangleFromRef(input)).forEach(refRectangleEntry -> {
            Ref ref = refRectangleEntry.value();
            int length = retRefList.size();
            for (int i = 0; i < length; i++) {
                Ref inputRef = retRefList.removeFirst();
                inputRef.getNonOverlap(ref).forEach(retRefList::addLast);
            }
        });
        return retRefList;
    }

    public long getNumEdges() {
        AtomicLong numEdges = new AtomicLong(0);
        depToPrecList.forEach((dep, precSet) -> {
            numEdges.addAndGet(precSet.size());
        });
        return numEdges.get();
    }

    public long getNumVertices() {
        HashSet<Ref> refSet = new HashSet<>();
        depToPrecList.forEach((dep, precSet) -> {
            refSet.add(dep);
            precSet.forEach(refWithMeta -> {
                refSet.add(refWithMeta.getRef());
            });
        });
        return refSet.size();
    }

    public void add(Ref precedent, Ref dependent) {
        LinkedList<CompressInfo> compressInfoList = new LinkedList<>();
        if (doCompression) {
            compressInfoList = findCompressInfo(precedent, dependent);
        }
        if (compressInfoList.isEmpty()) {
            insertMemEntry(precedent, dependent,
                    new EdgeMeta(PatternType.NOTYPE, Offset.noOffset, Offset.noOffset));
        } else {
            if (dollar_signed) {
                CompressInfo selectedInfo =
                        Collections.min(compressInfoList, dollarSignedCompressInfoComparator);
                updateOneCompressEntry(selectedInfo);
            } else {
                CompressInfo selectedInfo =
                        Collections.min(compressInfoList, compressInfoComparator);
                updateOneCompressEntry(selectedInfo);
            }

        }
    }

    public void clearDependents(Ref delDep) {
        assert (delDep.getRow() == delDep.getLastRow() &&
                delDep.getColumn() == delDep.getLastColumn());

        Iterator<Ref> refIter = findOverlappingRefs(delDep);
        HashSet<Ref> depRangeSet = new HashSet<>();
        while (refIter.hasNext()) {
            Ref depRange = refIter.next();
            if (!depRangeSet.contains(depRange)) {
                depRangeSet.add(depRange);
                Iterator<RefWithMeta> precIterWithMeta = findPrecs(depRange).iterator();
                List<RefWithMeta> precList = new ArrayList<>();
                while (precIterWithMeta.hasNext()) {
                    precList.add(precIterWithMeta.next());
                }
                HashSet<Ref> precRangeSet = new HashSet<>();
                for (int i = 0; i < precList.size(); i++) {
                    RefWithMeta precRangeWithMeta = precList.get(i);
                    Ref precRange = precRangeWithMeta.getRef();
                    if (!precRangeSet.contains(precRange)) {
                        precRangeSet.add(precRange);
                        EdgeMeta edgeMeta = precRangeWithMeta.getEdgeMeta();
                        List<Pair<Ref, RefWithMeta>> newEdges =
                                deleteOneCell(precRange, depRange, edgeMeta, delDep);
                        deleteMemEntry(precRange, depRange, edgeMeta);
                        for (Pair<Ref, RefWithMeta> pair: newEdges) {
                            Ref newPrec = pair.first;
                            Ref newDep = pair.second.getRef();
                            EdgeMeta newEdgeMeta = pair.second.getEdgeMeta();
                            if (newDep.getType() == Ref.RefType.CELL) {
                                add(newPrec, newDep);
                            } else {
                                insertMemEntry(newPrec, newDep, newEdgeMeta);
                            }
                        }
                    }
                }
            }
        }
    }

    public void addBatch(List<Pair<Ref, Ref>> edgeBatch) {
        edgeBatch.forEach(oneEdge -> {
            Ref prec = oneEdge.first;
            Ref dep = oneEdge.second;
            add(prec, dep);
        });
    }

    public void setInRowCompression(boolean inRowCompression) {
        this.inRowCompression = inRowCompression;
    }

    public void setDoCompression(boolean doCompression) {
        this.doCompression = doCompression;
    }

    public void setIsDollar(boolean isDollar) {
        this.dollar_signed = isDollar;
    }

    public void setIsGap(boolean isGap) {
        this.isGap = isGap;
    }

    public void setIsTypeSensitive(boolean isTypeSensitive) {
        this.isTypeSensitive = isTypeSensitive;
    }

    private void updateOneCompressEntry(CompressInfo selectedInfo) {
        if (selectedInfo.isDuplicate) return;
        EdgeType type = selectedInfo.candPrec.getEdgeType();
        deleteMemEntry(selectedInfo.candPrec, selectedInfo.candDep, selectedInfo.edgeMeta);

        Ref newPrec = selectedInfo.prec.getBoundingBox(selectedInfo.candPrec);
        Ref newDep = selectedInfo.dep.getBoundingBox(selectedInfo.candDep);
        Pair<Offset, Offset> offsetPair = computeOffset(newPrec, newDep, selectedInfo.compType);
        if (this.isTypeSensitive) {
            newPrec.setEdgeType(type);
        }
        insertMemEntry(newPrec, newDep, new EdgeMeta(selectedInfo.compType, offsetPair.first, offsetPair.second));
    }

    private void insertMemEntry(Ref prec,
                                Ref dep,
                                EdgeMeta edgeMeta) {
        List<RefWithMeta> depList = precToDepList.getOrDefault(prec, new LinkedList<>());
        depList.add(new RefWithMeta(dep, edgeMeta));
        precToDepList.put(prec, depList);

        List<RefWithMeta> precList = depToPrecList.getOrDefault(dep, new LinkedList<>());
        precList.add(new RefWithMeta(prec, edgeMeta));
        depToPrecList.put(dep, precList);
        _rectToRef = _rectToRef.add(prec, RefUtils.refToRect(prec));
        _rectToRef = _rectToRef.add(dep, RefUtils.refToRect(dep));
    }

    private void deleteMemEntry(Ref prec,
                                Ref dep,
                                EdgeMeta edgeMeta) {
        List<RefWithMeta> depList = precToDepList.get(prec);
        if (depList != null) {
            depList.remove(new RefWithMeta(dep, edgeMeta));
            if (depList.isEmpty()) precToDepList.remove(prec);
        }

        List<RefWithMeta> precList = depToPrecList.get(dep);
        if (precList != null) {
            precList.remove(new RefWithMeta(prec, edgeMeta));
            if (precList.isEmpty()) {
                depToPrecList.remove(dep);
            }
        }

        _rectToRef = _rectToRef.delete(prec, RefUtils.refToRect(prec));
        _rectToRef = _rectToRef.delete(dep, RefUtils.refToRect(dep));
    }

    private Iterator<Ref> findOverlappingRefs(Ref updateRef) {
        Iterator<Ref> refIter = null;

        if (updateRef == null) {
            return new Iterator<Ref>() {
                @Override
                public boolean hasNext() {
                    return false;
                }

                @Override
                public Ref next() {
                    return null;
                }
            };
        }

        Iterator<Entry<Ref, Rectangle>> entryIter =
                _rectToRef.search(getRectangleFromRef(updateRef))
                        .toBlocking().getIterator();
        refIter = Iterators.transform(entryIter, new Function<Entry<Ref, Rectangle>, Ref>() {
            @Override
            public @Nullable Ref apply(@Nullable Entry<Ref, Rectangle> refRectangleEntry) {
                return refRectangleEntry.value();
            }
        });

        return refIter;
    }

    private Iterable<RefWithMeta> findPrecs(Ref dep) {
        List<RefWithMeta> precIter;
        precIter = depToPrecList.getOrDefault(dep, new LinkedList<>());
        return precIter;
    }

    private Iterable<RefWithMeta> findDeps(Ref prec) {
        List<RefWithMeta> depIter;
        depIter = precToDepList.getOrDefault(prec, new LinkedList<>());
        return depIter;
    }

    private List<Pair<Ref, RefWithMeta>> deleteOneCell(Ref prec, Ref dep,
                                                       EdgeMeta edgeMeta,
                                                       Ref delDep) {
        List<Pair<Ref, RefWithMeta>> ret = new LinkedList<>();
        boolean isDirectPrec = true;
        splitRangeByOneCell(dep, delDep).forEach(splitDep -> {
            Ref newSplitDep = splitDep;
            PatternType patternType = edgeMeta.patternType;
            if (patternType.ordinal() >= PatternType.TYPEFIVE.ordinal()
                    && patternType.ordinal() <= PatternType.TYPEELEVEN.ordinal()) {
                int gapSize = patternType.ordinal() - PatternType.TYPEFIVE.ordinal() + 1;
                newSplitDep = findValidGapRef(dep, splitDep, gapSize);
            }
            if (newSplitDep != null) {
                Ref splitPrec = findUpdatePrecRef(prec, dep, edgeMeta, newSplitDep, isDirectPrec);
                ret.add(new Pair<>(splitPrec, new RefWithMeta(splitDep, edgeMeta)));
            }
        });
        // if (ret.isEmpty()) ret.add(new Pair<>(prec, new RefWithMeta(dep, edgeMeta)));
        return ret;
    }

    private List<Ref> splitRangeByOneCell(Ref dep, Ref delDep) {
        int firstRow = dep.getRow();
        int firstCol = dep.getColumn();
        int lastRow = dep.getLastRow();
        int lastCol = dep.getLastColumn();

        int delRow = delDep.getRow();
        int delCol = delDep.getColumn();

        assert(firstRow == lastRow || firstCol == lastCol);
        List<Ref> refList = new LinkedList<>();

        // This range is actually a cell
        if (firstRow == lastRow && firstCol == lastCol) return refList;

        if (firstRow == lastRow) { // Row range
            if (delCol != firstCol)
                refList.add(RefUtils.coordToRef(dep, firstRow, firstCol, lastRow, delCol - 1));
            if (delCol != lastCol)
                refList.add(RefUtils.coordToRef(dep, firstRow, delCol + 1, lastRow, lastCol));
        } else { // Column range
            if (delRow != firstRow)
                refList.add(RefUtils.coordToRef(dep, firstRow, firstCol, delRow - 1, lastCol));
            if (delRow != lastRow)
                refList.add(RefUtils.coordToRef(dep, delRow + 1, firstCol, lastRow, lastCol));
        }

        return refList;
    }

    private CompressInfo findCompressionPatternWithGap(Ref prec, Ref dep,
                                                       Ref candPrec, Ref candDep, EdgeMeta metaData,
                                                       int gapSize, PatternType patternType) {
        if (dep.getColumn() == candDep.getColumn() && candDep.getLastRow() - dep.getRow() == -(gapSize + 1)) {
            if (metaData.patternType == PatternType.NOTYPE) {
                Offset offsetStartA = RefUtils.refToOffset(prec, dep, true);
                Offset offsetStartB = RefUtils.refToOffset(candPrec, candDep, true);

                Offset offsetEndA = RefUtils.refToOffset(prec, dep, false);
                Offset offsetEndB = RefUtils.refToOffset(candPrec, candDep, false);

                if (offsetStartA.equals(offsetStartB) &&
                        offsetEndA.equals(offsetEndB)) {
                    return new CompressInfo(false, Direction.TODOWN, patternType,
                            prec, dep, candPrec, candDep, metaData);
                }
            } else if (metaData.patternType == patternType) {
                Offset offsetStartA = RefUtils.refToOffset(prec, dep, true);
                Offset offsetEndA = RefUtils.refToOffset(prec, dep, false);

                if (offsetStartA.equals(metaData.startOffset) &&
                        offsetEndA.equals(metaData.endOffset)) {
                    return new CompressInfo(false, Direction.TODOWN, patternType,
                            prec, dep, candPrec, candDep, metaData);
                }
            }
        } else if (dep.getRow() == candDep.getRow() && candDep.getLastColumn() - dep.getColumn() == -(gapSize + 1)) {
            if (metaData.patternType == PatternType.NOTYPE) {
                Offset offsetStartA = RefUtils.refToOffset(prec, dep, true);
                Offset offsetStartB = RefUtils.refToOffset(candPrec, candDep, true);

                Offset offsetEndA = RefUtils.refToOffset(prec, dep, false);
                Offset offsetEndB = RefUtils.refToOffset(candPrec, candDep, false);

                if (offsetStartA.equals(offsetStartB) &&
                        offsetEndA.equals(offsetEndB)) {
                    return new CompressInfo(false, Direction.TORIGHT, patternType,
                            prec, dep, candPrec, candDep, metaData);
                }
            } else if (metaData.patternType == patternType) {
                Offset offsetStartA = RefUtils.refToOffset(prec, dep, true);
                Offset offsetEndA = RefUtils.refToOffset(prec, dep, false);

                if (offsetStartA.equals(metaData.startOffset) &&
                        offsetEndA.equals(metaData.endOffset)) {
                    return new CompressInfo(false, Direction.TORIGHT, patternType,
                            prec, dep, candPrec, candDep, metaData);
                }
            }
        }
        return new CompressInfo(false, Direction.NODIRECTION, PatternType.NOTYPE,
                prec, dep, candPrec, candDep, metaData);
    }

    private LinkedList<CompressInfo> findCompressInfo(Ref prec, Ref dep) {
        LinkedList<CompressInfo> compressInfoList = new LinkedList<>();
        findOverlapAndAdjacency(dep, 0).forEach(candDep -> {
            findPrecs(candDep).forEach(candPrecWithMeta -> {
                PatternType patternType = candPrecWithMeta.getPatternType();
                // RR-chain, RR, RF, FR, FF, NoComp
                if (patternType.ordinal() < PatternType.TYPEFIVE.ordinal() ||
                        patternType.ordinal() > PatternType.TYPEELEVEN.ordinal()) {
                    CompressInfo compRes = findCompressionPattern(prec, dep,
                            candPrecWithMeta.getRef(), candDep, candPrecWithMeta.getEdgeMeta());
                    addToCompressionInfoList(compressInfoList, compRes);
                }
            });
        });

        if (!inRowCompression && isGap) {
            for (int i = 0; i < PatternType.NOTYPE.ordinal()
                    - PatternType.TYPEFIVE.ordinal(); i++) {
                int gapSize = i + 1;
                PatternType patternType =
                        PatternType.values()[PatternType.TYPEFIVE.ordinal() + i];
                if (compressInfoList.isEmpty()) {
                    findOverlapAndAdjacency(dep, gapSize).forEach(candDep -> {
                        findPrecs(candDep).forEach(candPrecWithMeta -> {
                            CompressInfo compRes = findCompressionPatternWithGap(prec, dep,
                                    candPrecWithMeta.getRef(), candDep,
                                    candPrecWithMeta.getEdgeMeta(), gapSize, patternType);
                            addToCompressionInfoList(compressInfoList, compRes);
                        });
                    });
                } else break;
            }
        }

        return compressInfoList;
    }

    private void addToCompressionInfoList(LinkedList<CompressInfo> compressInfoList,
                                          CompressInfo compRes) {
        Boolean isDuplicate = compRes.isDuplicate;
        PatternType compType = compRes.compType;
        if (isDuplicate || compType != PatternType.NOTYPE) {
            compressInfoList.add(compRes);
        }
    }

    private CompressInfo findCompressionPattern(Ref prec, Ref dep,
                                                Ref candPrec, Ref candDep, EdgeMeta metaData) {
        PatternType curCompType = metaData.patternType;

        // Check the duplicate edge
        if (isSubsume(candPrec, prec) && isSubsume(candDep, dep))
            return new CompressInfo(true, Direction.NODIRECTION, curCompType,
                    prec, dep, candPrec, candDep, metaData);

        // Otherwise, find the compression type
        // Guarantee the adjacency
        Direction direction = findAdjacencyDirection(dep, candDep, DEAULT_SHIFT_STEP);
        if (direction == Direction.NODIRECTION ||
                (inRowCompression &&
                        (direction == Direction.TOLEFT ||
                                direction == Direction.TORIGHT))) {
            return new CompressInfo(false, Direction.NODIRECTION, PatternType.NOTYPE,
                    prec, dep, candPrec, candDep, metaData);
        }

        Ref lastCandPrec = findLastPrec(candPrec, candDep, metaData, direction);
        if (this.isTypeSensitive) {
            lastCandPrec.setEdgeType(candPrec.getEdgeType());
        }
        PatternType compressType =
                findCompPatternHelper(direction, prec, dep, candPrec, candDep, lastCandPrec);
        PatternType retCompType = PatternType.NOTYPE;
        if (curCompType == PatternType.NOTYPE) retCompType = compressType;
        else if (curCompType == compressType) retCompType = compressType;

        return new CompressInfo(false, direction, retCompType,
                prec, dep, candPrec, candDep, metaData);
    }

    private boolean checkLeftUpRelative(Ref prec, Direction direction) {
        if (!prec.checkLeftUpColumnDollar() && !prec.checkLeftUpRowDollar()) {
            return true;
        }
        if (prec.checkLeftUpColumnDollar() && !prec.checkLeftUpRowDollar() &&
                (direction == Direction.TODOWN || direction == Direction.TOUP)) {
            return true;
        }
        if (prec.checkLeftUpRowDollar() && !prec.checkLeftUpColumnDollar() &&
                (direction == Direction.TOLEFT || direction == Direction.TORIGHT)) {
            return true;
        }
        return false;
    }

    private boolean checkRightDownRelative(Ref prec, Direction direction) {
        if (!prec.checkRightDownColumnDollar() && !prec.checkRightDownRowDollar()) {
            return true;
        }
        if (prec.checkRightDownColumnDollar() && !prec.checkRightDownRowDollar() &&
                (direction == Direction.TODOWN || direction == Direction.TOUP)) {
            return true;
        }
        if (prec.checkRightDownRowDollar() && !prec.checkRightDownColumnDollar() &&
                (direction == Direction.TOLEFT || direction == Direction.TORIGHT)) {
            return true;
        }
        return false;
    }

    private PatternType findCompPatternHelper(Direction direction,
                                              Ref prec, Ref dep,
                                              Ref candPrec, Ref candDep,
                                              Ref lastCandPrec) {
        PatternType compressType = PatternType.NOTYPE;

        if (dollar_signed) {
            if (checkLeftUpRelative(prec, direction)) {
                if (checkRightDownRelative(prec, direction)) {
                    // RR
                    if (isCompressibleTypeOne(lastCandPrec, prec, direction)) {
                        compressType = PatternType.TYPEONE;
                        if (isCompressibleTypeZero(prec, dep, lastCandPrec))
                            compressType = PatternType.TYPEZERO;
                    }
                } else {
                    // RF
                    if (isCompressibleTypeTwo(lastCandPrec, prec, direction)) {
                        compressType = PatternType.TYPETWO;
                    }
                }
            } else {
                if (checkRightDownRelative(prec, direction)) {
                    // FR
                    if (isCompressibleTypeThree(lastCandPrec, prec, direction)) {
                        compressType = PatternType.TYPETHREE;
                    }
                } else {
                    // FF
                    if (isCompressibleTypeFour(lastCandPrec, prec)) {
                        compressType = PatternType.TYPEFOUR;
                    }
                }
            }
        }

        if (compressType == PatternType.NOTYPE) {
            if (isCompressibleTypeOne(lastCandPrec, prec, direction)) {
                compressType = PatternType.TYPEONE;
                if (isCompressibleTypeZero(prec, dep, lastCandPrec))
                    compressType = PatternType.TYPEZERO;
            } else if (isCompressibleTypeTwo(lastCandPrec, prec, direction))
                compressType = PatternType.TYPETWO;
            else if (isCompressibleTypeThree(lastCandPrec, prec, direction))
                compressType = PatternType.TYPETHREE;
            else if (isCompressibleTypeFour(lastCandPrec, prec))
                compressType = PatternType.TYPEFOUR;
        }

        // ADD: check EdgeType
        if (compressType != PatternType.NOTYPE) {
            if (this.isTypeSensitive) {
                if (lastCandPrec.getEdgeType() != prec.getEdgeType()) {
                    compressType = PatternType.NOTYPE;
                }
            }
        }

        return compressType;
    }

    private boolean isSubsume(Ref large, Ref small) {
        if (large.getOverlap(small) == null) return false;
        return large.getOverlap(small).equals(small);
    }

    private Iterable<Ref> findAdjacency(Ref ref, int gapSize) {
        LinkedList<Ref> res = new LinkedList<>();
        int shift_step = gapSize + DEAULT_SHIFT_STEP;
        Arrays.stream(Direction.values()).filter(direction -> direction != Direction.NODIRECTION)
                .forEach(direction ->
                        findOverlappingRefs(shiftRef(ref, direction, shift_step))
                                .forEachRemaining(adjRef -> {
                                    if (isValidAdjacency(adjRef, ref, shift_step)) res.addLast(adjRef); // valid adjacency
                                })
                );
        return res;
    }

    private Iterable<Ref> findOverlapAndAdjacency(Ref ref, int gapSize) {
        // LinkedList<Ref> res = new LinkedList<>();
        Set <Ref> res = new HashSet<>();
        int shift_step = gapSize + DEAULT_SHIFT_STEP;

        // findOverlappingRefs(ref).forEachRemaining(res::addLast);
        findOverlappingRefs(ref).forEachRemaining(res::add);
        Arrays.stream(Direction.values()).filter(direction -> direction != Direction.NODIRECTION)
                .forEach(direction ->
                        findOverlappingRefs(shiftRef(ref, direction, shift_step))
                                .forEachRemaining(adjRef -> {
                                    // if (isValidAdjacency(adjRef, ref, shift_step)) res.addLast(adjRef); // valid adjacency
                                    if (isValidAdjacency(adjRef, ref, shift_step)) res.add(adjRef); // valid adjacency
                                })
                );

        return res;
    }

    private class CompressInfoComparator implements Comparator<CompressInfo> {
        @Override
        public int compare(CompressInfo infoA, CompressInfo infoB) {
            if (infoA.isDuplicate) return -1;
            else if (infoB.isDuplicate) return 1;
            else {
                int directionResult = infoA.direction.compareTo(infoB.direction);
                if (directionResult != 0) {
                    return directionResult;
                } else {
                    return infoA.compType.compareTo(infoB.compType);
                }
            }
        }
    }

    private class DollarSignedCompressInfoComparator implements Comparator<CompressInfo> {
        @Override
        public int compare(CompressInfo infoA, CompressInfo infoB) {
            if (infoA.isDuplicate) return -1;
            else if (infoB.isDuplicate) return 1;
            else {
                int directionResult = infoA.direction.compareTo(infoB.direction);
                if (directionResult != 0) {
                    return directionResult;
                } else {
                    // prec and direction must be the same
                    Ref prec = infoA.prec;
                    Direction direction = infoA.direction;
                    if (checkLeftUpRelative(prec, direction)) {
                        if (checkRightDownRelative(prec, direction)) {
                            // RR
                            return infoA.compType.compareTo(infoB.compType);
                        } else {
                            // RF
                            if (infoA.compType == PatternType.TYPETWO && infoB.compType == PatternType.TYPETWO) {
                                return 0;
                            }
                            if (infoA.compType == PatternType.TYPETWO) {
                                return -1;
                            }
                            if (infoB.compType == PatternType.TYPETWO) {
                                return 1;
                            }

                        }
                    } else {
                        if (checkRightDownRelative(prec, direction)) {
                            // FR
                            if (infoA.compType == PatternType.TYPETHREE && infoB.compType == PatternType.TYPETHREE) {
                                return 0;
                            }
                            if (infoA.compType == PatternType.TYPETHREE) {
                                return -1;
                            }
                            if (infoB.compType == PatternType.TYPETHREE) {
                                return 1;
                            }
                        } else {
                            // FF
                            if (infoA.compType == PatternType.TYPEFOUR && infoB.compType == PatternType.TYPEFOUR) {
                                return 0;
                            }
                            if (infoA.compType == PatternType.TYPEFOUR) {
                                return -1;
                            }
                            if (infoB.compType == PatternType.TYPEFOUR) {
                                return 1;
                            }
                        }
                    }
                    return infoA.compType.compareTo(infoB.compType);
                }
            }
        }
    }

    private class CompressInfo {
        Boolean isDuplicate;
        Direction direction;
        PatternType compType;
        Ref prec;
        Ref dep;
        Ref candPrec;
        Ref candDep;
        EdgeMeta edgeMeta;

        CompressInfo(Boolean isDuplicate,
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
    }

    private class EdgeUpdate {
        Ref oldPrec;
        Ref oldDep;
        EdgeMeta oldEdgeMeta;

        Ref newPrec;
        Ref newDep;
        EdgeMeta newEdgeMeta;

        EdgeUpdate(Ref oldPrec,
                   Ref oldDep,
                   EdgeMeta oldEdgeMeta,
                   Ref newPrec,
                   Ref newDep,
                   EdgeMeta newEdgeMeta) {
            this.oldPrec = oldPrec;
            this.oldDep = oldDep;
            this.oldEdgeMeta = oldEdgeMeta;
            updateEdge(newPrec, newDep, newEdgeMeta);
        }

        void updateEdge(Ref newPrec,
                        Ref newDep,
                        EdgeMeta newEdgeMeta) {
            this.newPrec = newPrec;
            this.newDep = newDep;
            this.newEdgeMeta = newEdgeMeta;
        }

        boolean hasUpdate() {
            return !(oldPrec.equals(newPrec) &&
                    oldDep.equals(oldPrec) &&
                    oldEdgeMeta.equals(newEdgeMeta));
        }

    }
}

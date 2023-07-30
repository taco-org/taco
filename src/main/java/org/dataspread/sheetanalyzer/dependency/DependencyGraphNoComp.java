package org.dataspread.sheetanalyzer.dependency;

import com.github.davidmoten.rtree.Entry;
import com.github.davidmoten.rtree.RTree;
import com.github.davidmoten.rtree.geometry.Rectangle;

import org.dataspread.sheetanalyzer.dependency.util.*;
import org.dataspread.sheetanalyzer.util.Pair;
import org.dataspread.sheetanalyzer.util.Ref;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.dataspread.sheetanalyzer.dependency.util.PatternTools.getRectangleFromRef;

public class DependencyGraphNoComp implements DependencyGraph {
    protected HashMap<Ref, Set<Ref>> precToDepSet = new HashMap<>();
    protected HashMap<Ref, Set<Ref>> depToPrecSet = new HashMap<>();
    private RTree<Ref, Rectangle> _rectToRef = RTree.create();

    public HashMap<Ref, Set<Ref>> getGraph() {
        return precToDepSet;
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
        RTree<Ref, Rectangle> foundRefSet = RTree.create();
        Queue<Ref> updateQueue = new LinkedList<>();
        updateQueue.add(precedent);
        while (!updateQueue.isEmpty()) {
            Ref updateRef = updateQueue.remove();
            for (Ref precRef: getNeighbors(updateRef)) {
                List<RefWithMeta> tempResults = new LinkedList<>();
                for (Ref depRef: getDirectDependents(precRef)) {
                    if (!isDirectDep) {
                        if (!isContained(foundRefSet, depRef)) {
                            foundRefSet = foundRefSet.add(depRef, getRectangleFromRef(depRef));
                            updateQueue.add(depRef);
                        }
                    }
                    Offset offset = new Offset(0, 0);
                    EdgeMeta edgeMeta = new EdgeMeta(PatternType.NOTYPE, offset, offset);
                    RefWithMeta depRefWithMeta = new RefWithMeta(depRef, edgeMeta);
                    tempResults.add(depRefWithMeta);
                }
                if (!tempResults.isEmpty()) {
                    Set<RefWithMeta> oldResults = results.getOrDefault(precRef, new HashSet<>());
                    oldResults.addAll(tempResults);
                    results.put(precRef, oldResults);
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

    private boolean isContained(RTree<Ref, Rectangle> resultSet, Ref input) {
        boolean isContained = false;
        Iterator<Entry<Ref, Rectangle>> matchIter =
                resultSet.search(RefUtils.refToRect(input)).toBlocking().getIterator();
        while (matchIter.hasNext()) {
            if (isSubsume(matchIter.next().value(), input)) {
                isContained = true;
                break;
            }
        }
        return isContained;
    }

    private boolean isSubsume(Ref large, Ref small) {
        Ref overlap = large.getOverlap(small);
        if (overlap == null)
            return false;
        return overlap.equals(small);
    }

    private Set<Ref> getNeighbors(Ref ref) {
        Iterator<Entry<Ref, Rectangle>> rTreeIter = this._rectToRef.search(getRectangleFromRef(ref))
                .toBlocking().getIterator();
        Set<Ref> neighbors = new HashSet<>();
        while (rTreeIter.hasNext()) {
            neighbors.add(rTreeIter.next().value());
        }
        return neighbors;
    }

    private Set<Ref> getDirectDependents(Ref prec) {
        if (this.precToDepSet.containsKey(prec)) {
            return this.precToDepSet.get(prec);
        } else {
            return new HashSet<>();
        }
    }


    public HashMap<Ref, List<RefWithMeta>> getPrecedents(Ref dependent, boolean isDirectPrec) {
        HashMap<Ref, List<RefWithMeta>> results = new HashMap<>();
        if (RefUtils.isValidRef(dependent)) {
            results = getPrecedentsInternal(dependent, isDirectPrec);
        }
        return results;
    }

    private HashMap<Ref, List<RefWithMeta>> getPrecedentsInternal(Ref dependent, boolean isDirectDep) {
        HashMap<Ref, Set<RefWithMeta>> results = new HashMap<>();
        RTree<Ref, Rectangle> foundRefSet = RTree.create();
        Queue<Ref> updateQueue = new LinkedList<>();
        updateQueue.add(dependent);
        while (!updateQueue.isEmpty()) {
            Ref updateRef = updateQueue.remove();
            for (Ref depRef: getNeighbors(updateRef)) {
                List<RefWithMeta> tempResults = new LinkedList<>();
                for (Ref precRef: getDirectPrecedent(depRef)) {
                    if (!isDirectDep) {
                        if (!isContained(foundRefSet, precRef)) {
                            foundRefSet = foundRefSet.add(precRef, getRectangleFromRef(precRef));
                            updateQueue.add(precRef);
                        }
                    }
                    Offset offset = new Offset(0, 0);
                    EdgeMeta edgeMeta = new EdgeMeta(PatternType.NOTYPE, offset, offset);
                    RefWithMeta precRefWithMeta = new RefWithMeta(precRef, edgeMeta);
                    tempResults.add(precRefWithMeta);
                }
                if (!tempResults.isEmpty()) {
                    Set<RefWithMeta> oldResults = results.getOrDefault(depRef, new HashSet<>());
                    oldResults.addAll(tempResults);
                    results.put(depRef, oldResults);
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

    private Set<Ref> getDirectPrecedent(Ref dep) {
        if (this.depToPrecSet.containsKey(dep)) {
            return this.depToPrecSet.get(dep);
        } else {
            return new HashSet<>();
        }
    }

    private void insertMemEntry(Ref prec, Ref dep) {
        Set<Ref> depSet = precToDepSet.getOrDefault(prec, new HashSet<>());
        depSet.add(dep);
        precToDepSet.put(prec, depSet);

        Set<Ref> precSet = depToPrecSet.getOrDefault(dep, new HashSet<>());
        precSet.add(prec);
        depToPrecSet.put(dep, precSet);

        _rectToRef = _rectToRef.add(prec, RefUtils.refToRect(prec));
        _rectToRef = _rectToRef.add(dep, RefUtils.refToRect(dep));
    }

    private void deleteMemEntry(Ref prec, Ref dep) {
        Set<Ref> depSet = precToDepSet.get(prec);
        if (depSet != null) {
            depSet.remove(dep);
            if (depSet.isEmpty()) {
                precToDepSet.remove(prec);
            }
        }

        Set<Ref> precSet = depToPrecSet.get(dep);
        if (precSet != null) {
            precSet.remove(prec);
            if (precSet.isEmpty()) {
                depToPrecSet.remove(dep);
            }
        }

        _rectToRef = _rectToRef.delete(prec, RefUtils.refToRect(prec));
        _rectToRef = _rectToRef.delete(dep, RefUtils.refToRect(dep));
    }

    public void add(Ref precedent, Ref dependent) {
        insertMemEntry(precedent, dependent);
    }

    public void addBatch(List<Pair<Ref, Ref>> edgeBatch) {
        edgeBatch.forEach(oneEdge -> {
            Ref prec = oneEdge.first;
            Ref dep = oneEdge.second;
            add(prec, dep);
        });
    }

    public void clearDependents(Ref delDep) {
        assert (delDep.getRow() == delDep.getLastRow()) && (delDep.getColumn() == delDep.getLastColumn());
        List<Ref> precList = new ArrayList<>();
        Iterator<Ref> precIter = getDirectPrecedent(delDep).iterator();
        while (precIter.hasNext()) {
            precList.add(precIter.next());
        }
        for (int i = 0; i < precList.size(); i++) {
            Ref precRef = precList.get(i);
            deleteMemEntry(precRef, delDep);
        }
    }

    public long getNumEdges() {
        AtomicLong numEdges = new AtomicLong(0);
        depToPrecSet.forEach((dep, precSet) -> {
            numEdges.addAndGet(precSet.size());
        });
        return numEdges.get();
    }

    public long getNumVertices() {
        HashSet<Ref> refSet = new HashSet<>();
        depToPrecSet.forEach((dep, precSet) -> {
            refSet.add(dep);
            refSet.addAll(precSet);
        });
        return refSet.size();
    }

}

package org.dataspread.sheetanalyzer.dependency;

import com.github.davidmoten.rtree.Entry;
import com.github.davidmoten.rtree.RTree;
import com.github.davidmoten.rtree.geometry.Rectangle;

import org.dataspread.sheetanalyzer.dependency.util.*;
import org.dataspread.sheetanalyzer.util.Pair;
import org.dataspread.sheetanalyzer.util.Ref;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.dataspread.sheetanalyzer.dependency.util.PatternTools.getRectangleFromRef;

public class DependencyGraphNoComp implements DependencyGraph {
    protected HashMap<Ref, Set<Ref>> precToDepSet = new HashMap<>();
    protected HashMap<Ref, Set<Ref>> depToPrecSet = new HashMap<>();
    private RTree<Ref, Rectangle> _rectToRef = RTree.create();

    public Set<Ref> getDependents(Ref precedent) {
        LinkedHashSet<Ref> result = new LinkedHashSet<>();
        if (RefUtils.isValidRef(precedent)) {
            getDependentsInternal(precedent, result);
        }
        return result;
    }

    public HashMap<Ref, Set<Ref>> getGraph() {
        return precToDepSet;
    }

    private void getDependentsInternal(Ref prec, LinkedHashSet<Ref> result) {
        // RTree<Ref, Rectangle> resultSet = RTree.create();
        Queue<Ref> updateQueue = new LinkedList<>();
        updateQueue.add(prec);
        while (!updateQueue.isEmpty()) {
            Ref updateRef = updateQueue.remove();
            for (Ref precRef: getNeighbors(updateRef)) {
                for (Ref depRef: getDirectDependents(precRef)) {
                    if (!result.contains(depRef)) {
                        result.add(depRef);
                        updateQueue.add(depRef);
                    }
                    // if (!isContained(resultSet, depRef)) {
                    //     resultSet = resultSet.add(depRef, getRectangleFromRef(depRef));
                    //     result.add(depRef);
                    //     updateQueue.add(depRef);
                    // }
                }
            }
        }
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

    public Set<Ref> getPrecedents(Ref dependent) {
        LinkedHashSet<Ref> result = new LinkedHashSet<>();
        if (RefUtils.isValidRef(dependent)) {
            getPrecedentInternal(dependent, result);
        }
        return result;
    }

    private void getPrecedentInternal(Ref dep, LinkedHashSet<Ref> result) {
        RTree<Ref, Rectangle> resultSet = RTree.create();
        Queue<Ref> updateQueue = new LinkedList<>();
        updateQueue.add(dep);
        while (!updateQueue.isEmpty()) {
            Ref updateRef = updateQueue.remove();
            for (Ref depRef: getNeighbors(updateRef)) {
                for (Ref precRef: getDirectPrecedent(depRef)) {
                    if (!isContained(resultSet, precRef)) {
                        resultSet = resultSet.add(precRef, getRectangleFromRef(precRef));
                        result.add(precRef);
                        updateQueue.add(precRef);
                    }
                }
            }
        }
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

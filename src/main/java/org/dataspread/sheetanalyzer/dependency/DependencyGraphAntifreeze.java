package org.dataspread.sheetanalyzer.dependency;

import org.dataspread.sheetanalyzer.dependency.util.AntifreezeCompressor;
import org.dataspread.sheetanalyzer.util.Pair;
import org.dataspread.sheetanalyzer.util.Ref;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class DependencyGraphAntifreeze implements DependencyGraph {

    private final int compressionConst = 20;
    private Map<Ref, Set<Ref>> compPrecToDepSet = new HashMap<>();
    private final Map<Ref, Set<Ref>> fullPrecToDepSet = new HashMap<>();
    private final Map<Ref, Set<Ref>> fullDepToPrecSet = new HashMap<>();

    @Override
    public Set<Ref> getDependents(Ref precedent) {
        assert precedent.getRow() == precedent.getLastRow() &&
                precedent.getColumn() == precedent.getLastColumn();
        Set<Ref> result = compPrecToDepSet.get(precedent);
        if (result == null) {
            return new LinkedHashSet<>();
        } else {
            return result;
        }
    }

    @Override
    public Set<Ref> getPrecedents(Ref dependent) {
        return null;
    }

    @Override
    public void add(Ref precedent, Ref dependent) {
        insertMemEntry(precedent, dependent);
    }

    @Override
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

    private Set<Ref> getDirectPrecedent(Ref dep) {
        if (this.fullDepToPrecSet.containsKey(dep)) {
            return this.fullDepToPrecSet.get(dep);
        } else {
            return new HashSet<>();
        }
    }

    @Override
    public void addBatch(List<Pair<Ref, Ref>> edgeBatch) {
        Map<Ref, Set<Ref>> compressedGraph =
                AntifreezeCompressor.buildCompressedGraph(edgeBatch, compressionConst);

        compressedGraph.forEach((prec, dependants) -> {
            compPrecToDepSet.put(prec, dependants);
        });

        edgeBatch.forEach(edge -> {
            Ref prec = edge.first;
            Ref dep = edge.second;
            insertMemEntry(prec, dep);
        });
    }

    @Override
    public long getNumEdges() {
        AtomicLong numEdges = new AtomicLong(0);
        compPrecToDepSet.forEach((prec, depSet) -> {
            numEdges.addAndGet(depSet.size());
        });
        return numEdges.get();
    }

    @Override
    public long getNumVertices() {
        HashSet<Ref> refSet = new HashSet<>();
        compPrecToDepSet.forEach((prec, depSet) -> {
            refSet.add(prec);
            refSet.addAll(depSet);
        });
        return refSet.size();
    }

    public void rebuildCompGraph() {
        List<Pair<Ref, Ref>> edgeBatch = new LinkedList<>();
        fullPrecToDepSet.forEach((prec, depList) -> {
            depList.forEach(dep -> {
                edgeBatch.add(new Pair<>(prec, dep));
            });
        });
        Map<Ref, Set<Ref>> compressedGraph =
                AntifreezeCompressor.buildCompressedGraph(edgeBatch, compressionConst);
        compPrecToDepSet = new HashMap<>();
        compressedGraph.forEach((prec, dependants) -> {
            compPrecToDepSet.put(prec, dependants);
        });
    }

    private void insertMemEntry(Ref prec, Ref dep) {
        Set<Ref> depSet = fullPrecToDepSet.getOrDefault(prec, new HashSet<>());
        depSet.add(dep);
        fullPrecToDepSet.put(prec, depSet);

        Set<Ref> precSet = fullDepToPrecSet.getOrDefault(dep, new HashSet<>());
        precSet.add(prec);
        fullDepToPrecSet.put(dep, precSet);
    }

    private void deleteMemEntry(Ref prec, Ref dep) {
        Set<Ref> depSet = fullPrecToDepSet.get(prec);
        if (depSet != null) {
            depSet.remove(dep);
            if (depSet.isEmpty()) {
                fullPrecToDepSet.remove(prec);
            }
        }

        Set<Ref> precSet = fullDepToPrecSet.get(dep);
        if (precSet != null) {
            precSet.remove(prec);
            if (precSet.isEmpty()) {
                fullDepToPrecSet.remove(dep);
            }
        }
    }

}

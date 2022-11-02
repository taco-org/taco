package org.dataspread.sheetanalyzer.analyzer;

import org.dataspread.sheetanalyzer.SheetAnalyzer;
import org.dataspread.sheetanalyzer.dependency.DependencyGraph;
import org.dataspread.sheetanalyzer.dependency.DependencyGraphTACO;
import org.dataspread.sheetanalyzer.dependency.util.RefWithMeta;
import org.dataspread.sheetanalyzer.parser.POIParser;
import org.dataspread.sheetanalyzer.parser.SpreadsheetParser;
import org.dataspread.sheetanalyzer.util.Pair;
import org.dataspread.sheetanalyzer.util.Ref;
import org.dataspread.sheetanalyzer.util.SheetData;
import org.dataspread.sheetanalyzer.util.SheetNotSupportedException;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class SheetAnalyzerImpl extends SheetAnalyzer {

    private final SpreadsheetParser parser;
    private final String fileName;
    private HashMap<String, SheetData> sheetDataMap;
    private HashMap<String, DependencyGraph> depGraphMap;
    private boolean inRowCompression;

    private long numEdges = 0;
    private long numVertices = 0;
    private final long maxNumQueries = 100000;
    private final long maxUnChangeNumQueries = 10000;
    private long graphBuildTimeCost = 0;

    public SheetAnalyzerImpl(String filePath) throws SheetNotSupportedException {
        this(filePath, false);
    }

    public SheetAnalyzerImpl(String filePath, boolean inRowCompression) throws SheetNotSupportedException {
        this(filePath, inRowCompression, true);
    }

    public SheetAnalyzerImpl(String filePath,
                         boolean inRowCompression,
                         boolean isDollar) throws SheetNotSupportedException {
        this(filePath, inRowCompression, isDollar, false);
    }

    public SheetAnalyzerImpl(String filePath,
                         boolean inRowCompression,
                         boolean isDollar, boolean isGap) throws SheetNotSupportedException {
        this(filePath, inRowCompression, isDollar, isGap, false);
    }

    public SheetAnalyzerImpl(String filePath,
                         boolean inRowCompression,
                         boolean isDollar, boolean isGap, boolean isTypeSensitive) throws SheetNotSupportedException {
        parser = new POIParser(filePath);
        fileName = parser.getFileName();
        // All sheet data stored <string, sheetdata>
        sheetDataMap = parser.getSheetData();
        this.inRowCompression = inRowCompression;

        depGraphMap = new HashMap<>();
        long start = System.currentTimeMillis();
        genDepGraphFromSheetData(depGraphMap, isDollar, isGap, isTypeSensitive);
        long end = System.currentTimeMillis();
        graphBuildTimeCost = end - start;
    }

    private void genDepGraphFromSheetData(HashMap<String, DependencyGraph> inputDepGraphMap,
                                          boolean isDollar,
                                          boolean isGap,
                                          boolean isTypeSensitive) {
        sheetDataMap.forEach((sheetName, sheetData) -> {
            DependencyGraphTACO tacoGraph = new DependencyGraphTACO();
            tacoGraph.setIsDollar(isDollar);
            tacoGraph.setIsGap(isGap);
            tacoGraph.setInRowCompression(inRowCompression);
            tacoGraph.setDoCompression(true);
            tacoGraph.setIsTypeSensitive(isTypeSensitive);

            HashSet<Ref> refSet = new HashSet<>();
            sheetData.getDepPairs().forEach(depPair -> {
                if (inRowCompression) {
                    boolean inRowOnly = isInRowOnly(depPair);
                    tacoGraph.setDoCompression(inRowOnly);
                }
                Ref dep = depPair.first;
                List<Ref> precList = depPair.second;
                Set<Ref> visited = new HashSet<>();
                precList.forEach(prec -> {
                    if (!visited.contains(prec)) {
                        tacoGraph.add(prec, dep);
                        numEdges += 1;
                        visited.add(prec);
                    }
                });
                refSet.add(dep);
                refSet.addAll(precList);
            });

            inputDepGraphMap.put(sheetName, tacoGraph);
            numVertices += refSet.size();
        });
    }

    @Override
    public String getFileName() { return fileName; }

    @Override
    public Set<String> getSheetNames() {
        return depGraphMap.keySet();
    }

    @Override
    public int getNumSheets() {
        return sheetDataMap.size();
    }

    @Override
    public HashMap<String, DependencyGraph> getDependencyGraphs() {
        return depGraphMap;
    }

    @Override
    public Map<Ref, List<RefWithMeta>> getDependents(String sheetName, Ref ref) {
        return ((DependencyGraphTACO)(depGraphMap.get(sheetName))).getDependents(ref, false);
    }

    @Override
    public Map<Ref, List<RefWithMeta>> getPrecedents(String sheetName, Ref ref) {
        return ((DependencyGraphTACO)(depGraphMap.get(sheetName))).getPrecedents(ref, false);
    }

    @Override
    public Map<String, Map<Ref, List<RefWithMeta>>> getTACODepGraphs() {
        Map<String, Map<Ref, List<RefWithMeta>>> tacoDepGraphs = new HashMap<>();
        depGraphMap.forEach((sheetName, depGraph) -> {
            tacoDepGraphs.put(sheetName, ((DependencyGraphTACO)depGraph).getCompressedGraph());
        });
        return tacoDepGraphs;
    }

    @Override
    public HashMap<Integer, Integer> getRefDistribution() {
        HashMap<Integer, Integer> refDist = new HashMap<>();
        sheetDataMap.forEach((sheetName, sheetData) -> {
            sheetData.getDepSet().forEach(dep -> {
                Integer numRefs = sheetData.getNumRefs(dep);
                Integer existingCount = refDist.getOrDefault(numRefs, 0);
                refDist.put(numRefs, existingCount + 1);
            });
        });
        return refDist;
    }

    @Override
    public long getNumCompEdges() {
        AtomicLong numOfCompEdges = new AtomicLong();
        depGraphMap.forEach((sheetName, depGraph) -> {
            numOfCompEdges.addAndGet(depGraph.getNumEdges());
        });
        return numOfCompEdges.get();
    }

    @Override
    public long getNumCompVertices() {
        AtomicLong numOfCompVertices = new AtomicLong();
        depGraphMap.forEach((sheetName, depGraph) -> {
            numOfCompVertices.addAndGet(depGraph.getNumVertices());
        });
        return numOfCompVertices.get();
    }

    @Override
    public long getNumEdges() {
        return numEdges;
    }

    @Override
    public long getNumVertices() {
        return numVertices;
    }

    @Override
    public long getNumOfFormulae() {
        AtomicLong numOfFormulae = new AtomicLong();
        sheetDataMap.forEach((sheetName, sheetData) -> {
            numOfFormulae.addAndGet(sheetData.getDepSet().size());
        });
        return numOfFormulae.get();
    }

    @Override
    public Pair<Ref, Long> getRefWithLongestDepChain() {
        AtomicReference<Ref> retRef = new AtomicReference<>(null);
        AtomicLong maxDepLength = new AtomicLong(0L);
        sheetDataMap.forEach((sheetName, sheetData) -> {
            Pair<Ref, Long> perSheetPair = getRefWithLongestPathPerSheetData(sheetData);
            if (perSheetPair.second > maxDepLength.get()) {
                perSheetPair.first.setSheetName(sheetName);
                retRef.set(perSheetPair.first);
                maxDepLength.set(perSheetPair.second);
            }
        });

        return new Pair<>(retRef.get(), maxDepLength.get());
    }

    @Override
    public long getLongestPathLength(String sheetName, Ref startRef) {
        SheetData sheetData = sheetDataMap.get(sheetName);
        Pair<HashMap<Ref, Set<Ref>>, HashMap<Ref, Set<Ref>>> cellwiseDepGraph = sheetData.genCellWiseDepGraph();
        HashMap<Ref, Long> refToLength = genRefToLength(cellwiseDepGraph, startRef);

        AtomicReference<Long> maxLength = new AtomicReference<>(0L);
        refToLength.forEach((curRef, curLength) -> {
            if (curLength > maxLength.get()) {
                maxLength.set(curLength);
            }
        });

        return maxLength.get();
    }

    private Pair<Ref, Long> getRefWithLongestPathPerSheetData(SheetData sheetData) {
        Pair<HashMap<Ref, Set<Ref>>, HashMap<Ref, Set<Ref>>> cellwiseDepGraph = sheetData.genCellWiseDepGraph();
        HashMap<Ref, Set<Ref>> depToPrecs = cellwiseDepGraph.second;

        HashMap<Ref, Long> refToLength = genRefToLength(cellwiseDepGraph, SheetData.rootRef);

        AtomicReference<Ref> maxRef = new AtomicReference<>();
        AtomicReference<Long> maxLength = new AtomicReference<>(0L);
        refToLength.forEach((curRef, curLength) -> {
            if (curLength > maxLength.get()) {
                maxRef.set(curRef);
                maxLength.set(curLength);
            }
        });

        Long curLength = maxLength.get();
        Ref curRef = maxRef.get();
        while (curLength > 1L) {
            for (Ref prec: depToPrecs.get(curRef)) {
                if (refToLength.containsKey(prec) &&
                        refToLength.get(prec) == curLength - 1){
                    curRef = prec;
                    curLength -= 1;
                    break;
                }
            }
        }

        return new Pair<>(curRef, maxLength.get() - 1);
    }

    private HashMap<Ref, Long> genRefToLength(Pair<HashMap<Ref, Set<Ref>>, HashMap<Ref, Set<Ref>>> cellwiseDepGraph,
                                              Ref startRef) {
        HashMap<Ref, Set<Ref>> precToDeps = cellwiseDepGraph.first;

        HashMap<Ref, Long> refToLength = new HashMap<>();
        List<Ref> sortedRefs = SheetData.getSortedRefsByTopology(SheetData.replicateGraph(startRef, cellwiseDepGraph), startRef);
        sortedRefs.forEach(rootCell -> {
            Long curLength = refToLength.getOrDefault(rootCell, 0L);
            refToLength.put(rootCell, curLength);
            precToDeps.getOrDefault(rootCell, new HashSet<>()).forEach(dep -> {
                Long depLength = refToLength.getOrDefault(dep, curLength + 1L);
                if (depLength < curLength + 1L) depLength = curLength + 1;
                refToLength.put(dep, depLength);
            });
        });

        return refToLength;
    }

    @Override
    public Pair<Ref, Long> getRefWithMostDeps() throws SheetNotSupportedException {
        throw new SheetNotSupportedException();
    }

    @Override
    public long getGraphBuildTimeCost() { return graphBuildTimeCost; }

    public HashMap<String, SheetData> getSheetDataMap() { return sheetDataMap; }

    private boolean isInRowOnly(Pair<Ref, List<Ref>> depPair) {
        Ref dep = depPair.first;
        List<Ref> precSet = depPair.second;
        int rowIndex = dep.getRow();
        AtomicBoolean isInRowOnly = new AtomicBoolean(true);
        precSet.forEach(prec -> {
            if (prec.getRow() != rowIndex || prec.getLastRow() != rowIndex)
                isInRowOnly.set(false);
        });
        return isInRowOnly.get();
    }
}

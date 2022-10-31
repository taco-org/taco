package org.dataspread.sheetanalyzer;

import org.dataspread.sheetanalyzer.dependency.DependencyGraph;
import org.dataspread.sheetanalyzer.dependency.DependencyGraphAntifreeze;
import org.dataspread.sheetanalyzer.dependency.DependencyGraphNoComp;
import org.dataspread.sheetanalyzer.dependency.DependencyGraphTACO;
import org.dataspread.sheetanalyzer.dependency.util.RefWithMeta;
import org.dataspread.sheetanalyzer.parser.POIParser;
import org.dataspread.sheetanalyzer.parser.SpreadsheetParser;
import org.dataspread.sheetanalyzer.dependency.util.DepGraphType;
import org.dataspread.sheetanalyzer.util.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class SheetAnalyzer {
    private final SpreadsheetParser parser;
    private final String fileName;
    private HashMap<String, SheetData> sheetDataMap;
    private HashMap<String, DependencyGraph> depGraphMap;
    private boolean inRowCompression;

    // ADD
    private final DepGraphType depGraphType;

    private long numEdges = 0;
    private long numVertices = 0;
    private final long maxNumQueries = 100000;
    private final long maxUnChangeNumQueries = 10000;
    private long graphBuildTimeCost = 0;

    public SheetAnalyzer(String filePath) throws SheetNotSupportedException {
        this(filePath, false, DepGraphType.TACO);
    }

    public SheetAnalyzer(String filePath,
                         boolean inRowCompression, DepGraphType depGraphType) throws SheetNotSupportedException {
        this(filePath, inRowCompression, depGraphType, false, true);
    }

    public SheetAnalyzer(String filePath,
                         boolean inRowCompression, DepGraphType depGraphType,
                         boolean isDollar) throws SheetNotSupportedException {
        this(filePath, inRowCompression, depGraphType, isDollar, true);
    }

    public SheetAnalyzer(String filePath,
                         boolean inRowCompression, DepGraphType depGraphType,
                         boolean isDollar, boolean isGap) throws SheetNotSupportedException {
        this(filePath, inRowCompression, depGraphType, isDollar, isGap, false);
    }

    public SheetAnalyzer(String filePath,
                         boolean inRowCompression, DepGraphType depGraphType,
                         boolean isDollar, boolean isGap, boolean isTypeSensitive) throws SheetNotSupportedException {
        parser = new POIParser(filePath);
        fileName = parser.getFileName();
        // All sheet data stored <string, sheetdata>
        sheetDataMap = parser.getSheetData();
        this.inRowCompression = inRowCompression;
        this.depGraphType = depGraphType;

        depGraphMap = new HashMap<>();
        long start = System.currentTimeMillis();
        genDepGraphFromSheetData(depGraphMap, isDollar, isGap, isTypeSensitive);
        long end = System.currentTimeMillis();
        graphBuildTimeCost = end - start;
    }

    public long getGraphBuildTimeCost() { return graphBuildTimeCost; }

    public HashMap<String, SheetData> getSheetDataMap() { return sheetDataMap; }

    public boolean isTACO() {
        return this.depGraphType == DepGraphType.TACO;
    }

    private void genDepGraphFromSheetData(HashMap<String, DependencyGraph> inputDepGraphMap,
                                          boolean isDollar,
                                          boolean isGap,
                                          boolean isTypeSensitive) {
        sheetDataMap.forEach((sheetName, sheetData) -> {
            DependencyGraph depGraph = null;
            if (depGraphType == DepGraphType.TACO) {
                depGraph = new DependencyGraphTACO();
                DependencyGraphTACO tacoGraph = (DependencyGraphTACO)depGraph;
                tacoGraph.setIsDollar(isDollar);
                tacoGraph.setIsGap(isGap);
                tacoGraph.setInRowCompression(inRowCompression);
                tacoGraph.setDoCompression(true);
                // Add EdgeType check
                tacoGraph.setIsTypeSensitive(isTypeSensitive);
            } else if (depGraphType == DepGraphType.NOCOMP) {
                depGraph = new DependencyGraphNoComp();
            } else {
                depGraph = new DependencyGraphAntifreeze();
            }

            HashSet<Ref> refSet = new HashSet<>();
            DependencyGraph finalDepGraph = depGraph;

            sheetData.getDepPairs().forEach(depPair -> {
                if (inRowCompression && depGraphType == DepGraphType.TACO) {
                    boolean inRowOnly = isInRowOnly(depPair);
                    ((DependencyGraphTACO) finalDepGraph).setDoCompression(inRowOnly);
                }
                Ref dep = depPair.first;
                List<Ref> precList = depPair.second;
                Set<Ref> visited = new HashSet<>();
                precList.forEach(prec -> {
                    if (!visited.contains(prec)) {
                        finalDepGraph.add(prec, dep);
                        numEdges += 1;
                        visited.add(prec);
                    }
                });
                refSet.add(dep);
                refSet.addAll(precList);
            });

            if (depGraphType == DepGraphType.ANTIFREEZE)
                ((DependencyGraphAntifreeze) depGraph).rebuildCompGraph();

            // depGraph.postMerge();
            inputDepGraphMap.put(sheetName, depGraph);
            numVertices += refSet.size();
        });
    }

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

    public String getFileName() { return fileName; }

    public Set<String> getSheetNames() {
        return depGraphMap.keySet();
    }

    public HashMap<String, DependencyGraph> getDependencyGraphs() {
        return depGraphMap;
    }

    public HashMap<String, String> getTacoBreakdown() {
        HashMap<String, String> tacoMap = new HashMap<>();
        depGraphMap.forEach((sheetName, depGraph) -> {
            if (depGraph instanceof DependencyGraphTACO) {
                tacoMap.put(sheetName, ((DependencyGraphTACO) depGraph).getTACOBreakdown());
            } else {
                tacoMap.put(sheetName, "TACO not used");
            }
        });
        return tacoMap;
    }

    public Set<Ref> getDependents(String sheetName, Ref ref) {
        return depGraphMap.get(sheetName).getDependents(ref);
    }

    public Set<Ref> getPrecedents(String sheetName, Ref ref) {
        return depGraphMap.get(sheetName).getPrecedents(ref);
    }

    public HashMap<String, HashMap<Ref, Set<Ref>>> getNoCompDepgraphs() {
        HashMap<String, HashMap<Ref, Set<Ref>>> DepGraphs = new HashMap<>();
        depGraphMap.forEach((sheetName, depGraph) -> {
            DepGraphs.put(sheetName, ((DependencyGraphNoComp)depGraph).getGraph());
        });
        return DepGraphs;
    }

    public HashMap<String, HashMap<Ref, List<RefWithMeta>>> getTACODepGraphs() {
        HashMap<String, HashMap<Ref, List<RefWithMeta>>> tacoDepGraphs = new HashMap<>();
        depGraphMap.forEach((sheetName, depGraph) -> {
            tacoDepGraphs.put(sheetName, ((DependencyGraphTACO)depGraph).getCompressedGraph());
        });
        return tacoDepGraphs;
    }

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

    public long getNumCompEdges() {
        AtomicLong numOfCompEdges = new AtomicLong();
        depGraphMap.forEach((sheetName, depGraph) -> {
            numOfCompEdges.addAndGet(depGraph.getNumEdges());
        });
        return numOfCompEdges.get();
    }

    public long getNumCompVertices() {
        AtomicLong numOfCompVertices = new AtomicLong();
        depGraphMap.forEach((sheetName, depGraph) -> {
            numOfCompVertices.addAndGet(depGraph.getNumVertices());
        });
        return numOfCompVertices.get();
    }

    public long getNumEdges() {
        return numEdges;
    }

    public long getNumVertices() {
        return numVertices;
    }

    public long getNumOfFormulae() {
        AtomicLong numOfFormulae = new AtomicLong();
        sheetDataMap.forEach((sheetName, sheetData) -> {
            numOfFormulae.addAndGet(sheetData.getDepSet().size());
        });
        return numOfFormulae.get();
    }

    public Long getNumDependents(String sheetName, Ref ref) {
        return (long) this.getDependents(sheetName, ref).stream().mapToInt(Ref::getCellCount).sum();
    }

    /* Return the cell that has the longest org.dataspread.sheetanalyzer.dependency chain
    * */
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

    public Long getLongestPathLength(String sheetName, Ref startRef) {
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

    /* Return the cell that has the largest number of dependencies
     * */
    public Pair<Ref, Long> getRefWithMostDeps() {
        AtomicReference<Ref> retRef = new AtomicReference<>(null);
        AtomicLong maxNumDeps = new AtomicLong(0L);
        sheetDataMap.forEach((String sheetName, SheetData sheetData) -> {
            DependencyGraph depGraph = depGraphMap.get(sheetName);
            Set<Ref> valueOnlyPrecSet = sheetData.getValueOnlyPrecSet();
            int numUnchangeQueries = 0;
            int numQueries = 0;
            for (Ref cellRef : valueOnlyPrecSet) {
                AtomicLong numDeps = new AtomicLong(0L);
                depGraph.getDependents(cellRef).forEach(depRef -> {
                    numDeps.addAndGet(depRef.getCellCount());
                });
                if (numDeps.get() > maxNumDeps.get()) {
                    numUnchangeQueries = 0;
                    cellRef.setSheetName(sheetName);
                    retRef.set(cellRef);
                    maxNumDeps.set(numDeps.get());
                }
                numUnchangeQueries++;
                numQueries++;
                if (numQueries >= maxNumQueries ||
                        numUnchangeQueries >= maxUnChangeNumQueries) break;
            }
        });
        return new Pair<>(retRef.get(), maxNumDeps.get());
    }

    public boolean includeDerivedColumnOnly () {
        return false;
    }

    public HashSet<RefWithMeta> extractDerivedColumns() {
        return new HashSet<>();
    }

    public boolean isTabularSheet() {
        return false;
    }

    public int getNumSheets() {
        return sheetDataMap.size();
    }

}

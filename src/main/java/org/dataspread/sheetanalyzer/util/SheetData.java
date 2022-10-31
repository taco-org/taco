package org.dataspread.sheetanalyzer.util;

import java.util.*;

public class SheetData {

    private final String sheetName;

    private int maxRows;
    private int maxCols;

    // List of formula cells
    private final List<Ref> formulaCells = new LinkedList<>();

    // HashMap of dependents: cell: list of prec
    private final HashMap<Ref, List<Ref>> sheetDeps = new HashMap<>();

    private final HashMap<Ref, Integer> formulaNumRefs = new HashMap<>();

    private final HashMap<Ref, CellContent> sheetContent = new HashMap<>();

    private final HashSet<Ref> accessAreaCache = new HashSet<>();

    private Pair<HashMap<Ref, Set<Ref>>, HashMap<Ref, Set<Ref>>> cellWiseGraph = null;

    public static final Ref rootRef = new RefImpl(-1, -1);
    private final long maxRange = 100;

    public SheetData(String sheetName) {
        this.sheetName = sheetName;
    }

    public void addDeps(Ref dep, List<Ref> precList) {
        formulaCells.add(dep);
        sheetDeps.put(dep, precList);
    }

    public void addFormulaNumRef(Ref dep, int numRefs) {
        formulaNumRefs.put(dep, numRefs);
    }

    public void addContent(Ref cellRef, CellContent cellContent) {
        sheetContent.put(cellRef, cellContent);
    }

    public void addOneAccess(Ref areaRef) {
        accessAreaCache.add(areaRef);
    }

    public boolean areaAccessed(Ref areaRef) {
        return accessAreaCache.contains(areaRef);
    }

    public List<Pair<Ref, List<Ref>>> getDepPairs() {
        LinkedList<Pair<Ref, List<Ref>>> depPairList = new LinkedList<>();
        formulaCells.forEach(formulaCell -> {
            depPairList.add(new Pair<>(formulaCell, sheetDeps.get(formulaCell)));
        });
        return depPairList;
    }

    public Set<Ref> getDepSet() {
        return formulaNumRefs.keySet();
    }

    public Set<Ref> getValueOnlyPrecSet() {
        Set<Ref> valueOnlyPrecSet = new HashSet<>();
        Set<Ref> areaSet = new HashSet<>();

        sheetDeps.forEach((Ref dep, List<Ref> precList) -> {
            precList.forEach(prec -> {
                if (!areaSet.contains(prec)) {
                    areaSet.add(prec);
                    valueOnlyPrecSet.addAll(toCellSet(prec));
                }
            });
        });

        return valueOnlyPrecSet;
    }

    private Set<Ref> toCellSet(Ref ref) {
        Set<Ref> cellSet = new HashSet<>();
        for (int row = ref.getRow(); row <= ref.getLastRow(); row++) {
            for (int col = ref.getColumn(); col <= ref.getLastColumn(); col++) {
                Ref cellRef = new RefImpl(row, col);
                CellContent cc = sheetContent.get(cellRef);
                if (!cc.isFormula  || (formulaNumRefs.get(cellRef) == 0))
                    cellSet.add(cellRef);
            }
        }
        return cellSet;
    }

    public Pair<HashMap<Ref, Set<Ref>>, HashMap<Ref, Set<Ref>>> genCellWiseDepGraph() {
        if (cellWiseGraph == null) {

            Set<Ref> valueCells = new HashSet<>();

            HashMap<Ref, Set<Ref>> precToDeps = new HashMap<>();
            HashMap<Ref, Set<Ref>> depToPrecs = new HashMap<>();

            sheetDeps.forEach((dep, precSet) -> {
                precSet.forEach(precRange -> {
                    int numCells = 0;
                    for (int row = precRange.getRow(); row <= precRange.getLastRow(); row++) {
                        for (int col = precRange.getColumn(); col <= precRange.getLastColumn(); col++) {
                            Ref prec = new RefImpl(row, col);
                            CellContent cc = sheetContent.get(prec);
                            if (!cc.isFormula || (formulaNumRefs.get(prec) == 0))
                                valueCells.add(prec);

                            // add to precToDeps
                            Set<Ref> deps = precToDeps.getOrDefault(prec, new HashSet<>());
                            deps.add(dep);
                            precToDeps.putIfAbsent(prec, deps);

                            // add to depToPrecs
                            Set<Ref> precs = depToPrecs.getOrDefault(dep, new HashSet<>());
                            precs.add(prec);
                            depToPrecs.putIfAbsent(dep, precs);

                            numCells += 1;
                        }
                        if (numCells >= maxRange) break;
                    }
                });
            });

            precToDeps.put(rootRef, valueCells);
            valueCells.forEach(valueCell -> {
                Set<Ref> precSet = new HashSet<>();
                precSet.add(rootRef);
                depToPrecs.put(valueCell, precSet);
            });

            cellWiseGraph = new Pair<>(precToDeps, depToPrecs);
        }
        return cellWiseGraph;
    }

    public static Pair<HashMap<Ref, Set<Ref>>, HashMap<Ref, Set<Ref>>> replicateGraph(Ref startCell,
                                                                                      Pair<HashMap<Ref, Set<Ref>>, HashMap<Ref, Set<Ref>>> inputCellWiseGraph) {
        HashMap<Ref, Set<Ref>> newPrecToDeps = new HashMap<>();
        HashMap<Ref, Set<Ref>> newDepToPrecs = new HashMap<>();

        HashMap<Ref, Set<Ref>> oldPrecToDeps = inputCellWiseGraph.first;

        List<Ref> rootCells = new LinkedList<>();
        rootCells.add(startCell);
        HashSet<Ref> visited = new HashSet<>();

        while (!rootCells.isEmpty()) {
            Ref rootCell = rootCells.remove(0);
            Set<Ref> depSet = oldPrecToDeps.get(rootCell);
            if (depSet != null) {
                depSet.forEach(dep -> {
                    insertNewGraph(newPrecToDeps, newDepToPrecs, rootCell, dep);
                    if (!visited.contains(dep)) {
                        visited.add(dep);
                        rootCells.add(dep);
                    }
                });
            }
        }

        return new Pair<>(newPrecToDeps, newDepToPrecs);
    }

    private static void insertNewGraph(HashMap<Ref, Set<Ref>> newPrecToDeps,
                                       HashMap<Ref, Set<Ref>> newDepToPrecs,
                                       Ref prec, Ref dep) {
        Set<Ref> depSet = newPrecToDeps.getOrDefault(prec, new HashSet<>());
        depSet.add(dep);
        newPrecToDeps.put(prec, depSet);

        Set<Ref> precSet = newDepToPrecs.getOrDefault(dep, new HashSet<>());
        precSet.add(prec);
        newDepToPrecs.put(dep, precSet);
    }

    public static List<Ref> getSortedRefsByTopology(Pair<HashMap<Ref, Set<Ref>>, HashMap<Ref, Set<Ref>>> inputCellWiseGraph,
                                             Ref startCell) {
        HashMap<Ref, Set<Ref>> precToDeps = inputCellWiseGraph.first;
        HashMap<Ref, Set<Ref>> depToPrecs = inputCellWiseGraph.second;

        List<Ref> sortedCells = new LinkedList<>();
        List<Ref> rootCells = new LinkedList<>();
        rootCells.add(startCell);

        while (!rootCells.isEmpty()) {
            Ref rootCell = rootCells.remove(0);
            Set<Ref> depSet = precToDeps.remove(rootCell);
            if (depSet != null) {
                depSet.forEach(dep -> {
                    Set<Ref> precSet = depToPrecs.get(dep);
                    precSet.remove(rootCell);
                    if (precSet.isEmpty()) {
                        depToPrecs.remove(dep);
                        rootCells.add(dep);
                    }
                });
            }
            sortedCells.add(rootCell);
        }

        return sortedCells;
    }

    public void setMaxRowsCols(int maxRows, int maxCols) {
        this.maxRows = maxRows;
        this.maxCols = maxCols;
    }

    public int getMaxRows() {return maxRows;}
    public int getMaxCols() {return maxCols;}

    public List<Ref> getPrecSet(Ref dep) {
        return sheetDeps.get(dep);
    }

    public int getNumRefs(Ref dep) {
        return formulaNumRefs.get(dep);
    }

    public CellContent getCellContent(Ref ref) {
        return sheetContent.get(ref);
    }

    public String getSheetName() {
        return sheetName;
    }
}

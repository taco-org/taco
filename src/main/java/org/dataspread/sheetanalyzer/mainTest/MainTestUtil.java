package org.dataspread.sheetanalyzer.mainTest;

import org.dataspread.sheetanalyzer.SheetAnalyzer;
import org.dataspread.sheetanalyzer.dependency.DependencyGraph;
import org.dataspread.sheetanalyzer.dependency.DependencyGraphAntifreeze;
import org.dataspread.sheetanalyzer.dependency.DependencyGraphNoComp;
import org.dataspread.sheetanalyzer.dependency.DependencyGraphTACO;
import org.dataspread.sheetanalyzer.dependency.util.DepGraphType;
import org.dataspread.sheetanalyzer.dependency.util.PatternType;
import org.dataspread.sheetanalyzer.dependency.util.RefUtils;
import org.dataspread.sheetanalyzer.dependency.util.RefWithMeta;
import org.dataspread.sheetanalyzer.util.*;

import java.io.PrintWriter;
import java.util.*;

public class MainTestUtil {

    public static void writePerSheetStat(SheetAnalyzer sheetAnalyzer,
                                         PrintWriter statPW,
                                         boolean inRowCompression) {
        String fileName = sheetAnalyzer.getFileName().replace(",", "-");
        long numFormulae = sheetAnalyzer.getNumOfFormulae();
        long numEdges = sheetAnalyzer.getNumEdges();
        long numVertices = sheetAnalyzer.getNumVertices();
        long numCompEdges = sheetAnalyzer.getNumCompEdges();
        long numCompVertices = sheetAnalyzer.getNumCompVertices();
        long graphBuildTime = sheetAnalyzer.getGraphBuildTimeCost();

        long[] numCompEdgesPerPattern = new long[PatternType.values().length];
        long[] numEdgesPerPattern = new long[PatternType.values().length];

        if (!inRowCompression) {
            if (sheetAnalyzer.isTACO()) {
                sheetAnalyzer.getTACODepGraphs().forEach((sheetName, tacoGraph) -> {
                    tacoGraph.forEach((prec, depWithMetaList) -> {
                        depWithMetaList.forEach(depWithMeta -> {
                            Ref dep = depWithMeta.getRef();
                            PatternType patternType = depWithMeta.getPatternType();

                            int patternIndex = patternType.ordinal();
                            numCompEdgesPerPattern[patternIndex] += 1;

                            long numPatternEdges = dep.getCellCount();
                            if (patternType.ordinal() >= PatternType.TYPEFIVE.ordinal() &&
                                    patternType != PatternType.NOTYPE) {
                                long gap = patternType.ordinal() - PatternType.TYPEFIVE.ordinal() + 1;
                                numPatternEdges = (numPatternEdges - 1) / (gap + 1) + 1;
                            }
                            numEdgesPerPattern[patternIndex] += numPatternEdges;
                        });
                    });
                });
            }
        }

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(fileName).append(",")
                .append(numFormulae).append(",")
                .append(numVertices).append(",")
                .append(numEdges).append(",")
                .append(numCompVertices).append(",")
                .append(numCompEdges).append(",")
                .append(graphBuildTime).append(",");
        if (!inRowCompression) {
            if (sheetAnalyzer.isTACO()) {
                for (int pIdx = 0; pIdx < numCompEdgesPerPattern.length; pIdx++) {
                    stringBuilder.append(numCompEdgesPerPattern[pIdx]).append(",")
                            .append(numEdgesPerPattern[pIdx]).append(",");
                }
            }
        }
        stringBuilder.deleteCharAt(stringBuilder.length() - 1).append("\n");
        statPW.write(stringBuilder.toString());
    }

    public static void TestGraphModify(PrintWriter statPW, String fileDir, String fileName,
                                       String refLoc, DepGraphType depGraphType, boolean isDollar, boolean isGap) {
        boolean inRowCompression = false;
        String filePath = fileDir + "/" + fileName;
        int modifySize = 1000;

        try {
            SheetAnalyzer sheetAnalyzer = new SheetAnalyzer(filePath, inRowCompression, depGraphType, isDollar, isGap);
            String sheetName = refLoc.split(":")[0];
            Ref targetRef = RefUtils.fromStringToCell(refLoc);
            int origRow = targetRef.getRow();
            int origCol = targetRef.getColumn();
            DependencyGraph depGraph = sheetAnalyzer.getDependencyGraphs().get(sheetName);

            ArrayList<Ref> candidateDeleteRefs = new ArrayList<>();
            int count = 0;
            while (count < modifySize) {
                Ref candRef = new RefImpl(origRow + count, origCol);
                candidateDeleteRefs.add(candRef);
                count += 1;
            }

            long start = System.currentTimeMillis();
            for (Ref ref: candidateDeleteRefs) {
                depGraph.clearDependents(ref);
            }
            if (depGraphType == DepGraphType.ANTIFREEZE)
                ((DependencyGraphAntifreeze) depGraph).rebuildCompGraph();
            long end = System.currentTimeMillis();
            long graphModifyTimeCost = end - start;

            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(fileName).append(",")
                    .append(refLoc).append(",")
                    .append(graphModifyTimeCost).append("\n");
            statPW.write(stringBuilder.toString());

        } catch (SheetNotSupportedException | OutOfMemoryError | NullPointerException e) {
            System.out.println(e.getMessage());
        }
    }

    public static void TestRefDependent(PrintWriter statPW, String fileDir, String fileName,
                                        String refLoc, DepGraphType depGraphType, boolean isDollar, boolean isGap) {
        boolean inRowCompression = false;
        String filePath = fileDir + "/" + fileName;

        try {
            SheetAnalyzer sheetAnalyzer = new SheetAnalyzer(filePath, inRowCompression, depGraphType,
                    isDollar, isGap, true);
            long graphBuildTime = sheetAnalyzer.getGraphBuildTimeCost();
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(fileName).append(",")
                    .append(refLoc).append(",")
                    .append(graphBuildTime).append(",");
            statPW.write(stringBuilder.toString());

            String sheetName = refLoc.split(":")[0];
            Ref targetRef = RefUtils.fromStringToCell(refLoc);

            HashMap<Ref, List<RefWithMeta>> result;
            long lookupSize = 0, lookupTime = 0;
            DependencyGraph depGraph = sheetAnalyzer.getDependencyGraphs().get(sheetName);

            long start = System.currentTimeMillis();
            result = ((DependencyGraphTACO)depGraph).getDependents(targetRef, false);
            for (Ref ref: result.keySet()) {
                System.out.print("Ref: " + ref + " Deps: ");
                for (RefWithMeta refWithMeta: result.get(ref)) {
                    System.out.print(refWithMeta.getRef() + " ");
                }
                System.out.print("\n");
            }
            lookupSize = result.size();
            lookupTime = System.currentTimeMillis() - start;

            stringBuilder = new StringBuilder();
            stringBuilder.append(lookupSize).append(",")
                    .append(lookupTime).append("\n");
            statPW.write(stringBuilder.toString());
        } catch (SheetNotSupportedException | OutOfMemoryError | NullPointerException e) {
            System.out.println(e.getMessage());
        }
    }

    public static void TestComparisonStat(PrintWriter statPW, String filePath) {
        boolean inRowCompression = false;
        try {
            SheetAnalyzer sheetCompAnalyzer = new SheetAnalyzer(filePath, inRowCompression, DepGraphType.TACO);
            SheetAnalyzer sheetNoCompAnalyzer = new SheetAnalyzer(filePath, inRowCompression, DepGraphType.NOCOMP);

            Pair<Ref, Long> mostDeps = new Pair(new RefImpl(-1, -1), 0);
            Pair<Ref, Long> longestDeps = new Pair(new RefImpl(-1, -1), 0);
            long mostDepCompLookupTime = 0, longestDepCompLookupTime = 0,
                    mostDepCompLookupSize = 0, longestDepCompLookupSize = 0,
                    mostDepCompPostProcesedLookupSize = 0, longestDepCompPostProcesedLookupSize = 0,
                    mostDepCompPostProcessedLookupTime = 0, longestDepCompPostProcessedLookupTime = 0;
            long mostDepNoCompLookupTime = 0, longestDepNoCompLookupTime = 0,
                    mostDepNoCompLookupSize = 0, longestDepNoCompLookupSize = 0,
                    mostDepNoCompPostProcesedLookupSize = 0, longestDepNoCompPostProcesedLookupSize = 0,
                    mostDepNoCompPostProcessedLookupTime = 0, longestDepNoCompPostProcessedLookupTime = 0;

            String fileName = sheetCompAnalyzer.getFileName().replace(",", "-");
            long numEdges = sheetCompAnalyzer.getNumEdges();
            mostDeps = sheetCompAnalyzer.getRefWithMostDeps();
            longestDeps = sheetCompAnalyzer.getRefWithLongestDepChain();
            Set<Ref> result, processedResult;

            // MostDeps - Comp
            long start = System.currentTimeMillis();
            String depSheetName = mostDeps.first.getSheetName();
            DependencyGraphTACO depCompGraph = (DependencyGraphTACO) sheetCompAnalyzer.getDependencyGraphs().get(depSheetName);
            result = depCompGraph.getDependents(mostDeps.first);
            mostDepCompLookupSize = result.size();
            mostDepCompLookupTime = System.currentTimeMillis() - start;
            processedResult = RefUtils.postProcessRefSet(result);
            mostDepCompPostProcesedLookupSize = processedResult.size();
            mostDepCompPostProcessedLookupTime = System.currentTimeMillis() - start;

            // MostDeps - NoComp
            start = System.currentTimeMillis();
            DependencyGraphNoComp depNoCompGraph = (DependencyGraphNoComp) sheetNoCompAnalyzer.getDependencyGraphs().get(depSheetName);
            result = depNoCompGraph.getDependents(mostDeps.first);
            mostDepNoCompLookupSize = result.size();
            mostDepNoCompLookupTime = System.currentTimeMillis() - start;
            processedResult = RefUtils.postProcessRefSet(result);
            mostDepNoCompPostProcesedLookupSize = processedResult.size();
            mostDepNoCompPostProcessedLookupTime = System.currentTimeMillis() - start;

            // LongestDeps - Comp
            start = System.currentTimeMillis();
            depSheetName = longestDeps.first.getSheetName();
            depCompGraph = (DependencyGraphTACO) sheetCompAnalyzer.getDependencyGraphs().get(depSheetName);
            result = depCompGraph.getDependents(longestDeps.first);
            longestDepCompLookupSize = result.size();
            longestDepCompLookupTime = System.currentTimeMillis() - start;
            processedResult = RefUtils.postProcessRefSet(result);
            longestDepCompPostProcesedLookupSize = processedResult.size();
            longestDepCompPostProcessedLookupTime = System.currentTimeMillis() - start;

            // LongestDeps - NoComp
            start = System.currentTimeMillis();
            depNoCompGraph = (DependencyGraphNoComp) sheetNoCompAnalyzer.getDependencyGraphs().get(depSheetName);
            result = depNoCompGraph.getDependents(longestDeps.first);
            longestDepNoCompLookupSize = result.size();
            longestDepNoCompLookupTime = System.currentTimeMillis() - start;
            processedResult = RefUtils.postProcessRefSet(result);
            longestDepNoCompPostProcesedLookupSize = processedResult.size();
            longestDepNoCompPostProcessedLookupTime = System.currentTimeMillis() - start;

            if (numEdges >= 10) {
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append(fileName).append(",")
                        .append(mostDeps.first.getSheetName()).append(",")
                        .append(mostDeps.first).append(",")
                        .append(mostDeps.second).append(",")
                        .append(mostDepCompLookupTime).append(",")
                        .append(mostDepCompLookupSize).append(",")
                        .append(mostDepCompPostProcesedLookupSize).append(",")
                        .append(mostDepCompPostProcessedLookupTime).append(",")
                        .append(mostDepNoCompLookupTime).append(",")
                        .append(mostDepNoCompLookupSize).append(",")
                        .append(mostDepNoCompPostProcesedLookupSize).append(",")
                        .append(mostDepNoCompPostProcessedLookupTime).append(",")
                        .append(longestDeps.first.getSheetName()).append(",")
                        .append(longestDeps.first).append(",")
                        .append(longestDeps.second).append(",")
                        .append(longestDepCompLookupTime).append(",")
                        .append(longestDepCompLookupSize).append(",")
                        .append(longestDepCompPostProcesedLookupSize).append(",")
                        .append(longestDepCompPostProcessedLookupTime).append(",")
                        .append(longestDepNoCompLookupTime).append(",")
                        .append(longestDepNoCompLookupSize).append(",")
                        .append(longestDepNoCompPostProcesedLookupSize).append(",")
                        .append(longestDepNoCompPostProcessedLookupTime);
                stringBuilder.append("\n");
                statPW.write(stringBuilder.toString());
            }
        } catch (SheetNotSupportedException | OutOfMemoryError e) {
            System.out.println(e.getMessage());
        }
    }

    public static Ref cellStringToRef(String cellString) {
        int colIndex = cellString.charAt(0) - 'A';
        int rowIndex = Integer.parseInt(cellString.substring(1)) - 1;

        return new RefImpl(rowIndex, colIndex);
    }

    public static DepGraphType fromStringToDepGraphType(String depGraphString) {
        if (depGraphString.trim().compareToIgnoreCase("taco") == 0)
            return DepGraphType.TACO;
        else if (depGraphString.trim().compareToIgnoreCase("antifreeze") == 0)
            return DepGraphType.ANTIFREEZE;
        else
            return DepGraphType.NOCOMP;
    }
}

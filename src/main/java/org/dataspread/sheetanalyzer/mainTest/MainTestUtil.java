package org.dataspread.sheetanalyzer.mainTest;

import org.dataspread.sheetanalyzer.SheetAnalyzer;
import org.dataspread.sheetanalyzer.analyzer.SheetAnalyzerImpl;
import org.dataspread.sheetanalyzer.dependency.DependencyGraph;
import org.dataspread.sheetanalyzer.dependency.DependencyGraphTACO;
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

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(fileName).append(",")
                .append(numFormulae).append(",")
                .append(numVertices).append(",")
                .append(numEdges).append(",")
                .append(numCompVertices).append(",")
                .append(numCompEdges).append(",")
                .append(graphBuildTime).append(",");
        if (!inRowCompression) {
            for (int pIdx = 0; pIdx < numCompEdgesPerPattern.length; pIdx++) {
                stringBuilder.append(numCompEdgesPerPattern[pIdx]).append(",")
                        .append(numEdgesPerPattern[pIdx]).append(",");
            }
        }
        stringBuilder.deleteCharAt(stringBuilder.length() - 1).append("\n");
        statPW.write(stringBuilder.toString());
    }

    public static void TestGraphModify(PrintWriter statPW, String fileDir, String fileName,
                                       String refLoc, boolean isDollar, boolean isGap, boolean isTypeSensitive) {
        boolean inRowCompression = false;
        boolean isCompression = true;
        String filePath = fileDir + "/" + fileName;
        int modifySize = 1000;

        try {
            SheetAnalyzer sheetAnalyzer = new SheetAnalyzerImpl(filePath, isCompression, inRowCompression, isDollar, isGap, isTypeSensitive);
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
                                        String refLoc, boolean isDollar, boolean isGap, boolean isTypeSensitive) {
        boolean inRowCompression = false;
        boolean isCompression = false;
        String filePath = fileDir + "/" + fileName;

        try {
            SheetAnalyzer sheetAnalyzer = new SheetAnalyzerImpl(filePath, isCompression, inRowCompression, isDollar, isGap, true);
            long graphBuildTime = sheetAnalyzer.getGraphBuildTimeCost();
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(fileName).append(",")
                    .append(refLoc).append(",")
                    .append(graphBuildTime).append(",");
            statPW.write(stringBuilder.toString());

            String sheetName = refLoc.split(":")[0];
            Ref targetRef = RefUtils.fromStringToCell(refLoc);

            Map<Ref, List<RefWithMeta>> result;
            long lookupSize, lookupTime;
            DependencyGraph depGraph = sheetAnalyzer.getDependencyGraphs().get(sheetName);

            long start = System.currentTimeMillis();
            result = depGraph.getDependents(targetRef, false);
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
}

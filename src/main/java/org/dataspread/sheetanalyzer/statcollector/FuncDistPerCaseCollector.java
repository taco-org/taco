package org.dataspread.sheetanalyzer.statcollector;

import org.dataspread.sheetanalyzer.dependency.util.EdgeMeta;
import org.dataspread.sheetanalyzer.dependency.util.Offset;
import org.dataspread.sheetanalyzer.dependency.util.PatternType;
import org.dataspread.sheetanalyzer.dependency.util.RefWithMeta;
import org.dataspread.sheetanalyzer.util.FormulaToken;
import org.dataspread.sheetanalyzer.util.Ref;
import org.dataspread.sheetanalyzer.util.RefImpl;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.dataspread.sheetanalyzer.statcollector.StatsCollectorUtil.*;

public class FuncDistPerCaseCollector implements StatsCollector{

    private final HashMap<Integer, Integer> numFunctionsPerCase ;
    private final HashMap<String, Integer> funcFreqCaseOne;
    private final HashMap<String, Integer> funcFreqCaseTwo;
    private final HashMap<String, Integer> funcFreqCaseThree;
    private final HashMap<String, Integer> funcFreqCaseFour;
    private final HashMap<Integer, Integer> caseTwoRowsApart;
    private int caseTwoIntermediate;
    private int caseThreeSingleColumn;

    private final String statFolder;

    private final String numFunctionsPerCaseFile = "numFunctionsPerCase.csv";
    private final String funcFreqCaseOneFile = "funcFreqCaseOne.csv";
    private final String funcFreqCaseTwoFile = "funcFreqCaseTwo.csv";
    private final String funcFreqCaseThreeFile = "funcFreqCaseThree.csv";
    private final String funcFreqCaseFourFile = "funcFreqCaseFour.csv";
    private final String caseTwoRowsApartFile = "caseTwoRowsApart.csv";
    private final String perCaseStatsFile = "perCaseStats.csv";

    private final int caseZero = 0;
    private final int caseOne = 1;
    private final int caseTwo = 2;
    private final int caseThree = 3;
    private final int caseFour = 4;

    public FuncDistPerCaseCollector(String statFolder) {
        this.statFolder = statFolder;
        this.numFunctionsPerCase = convertToIntInt(loadCSVtoHashMap(createFilePath(statFolder, numFunctionsPerCaseFile)));
        this.funcFreqCaseOne = convertToStrInt(loadCSVtoHashMap(createFilePath(statFolder, funcFreqCaseOneFile)));
        this.funcFreqCaseTwo = convertToStrInt(loadCSVtoHashMap(createFilePath(statFolder, funcFreqCaseTwoFile)));
        this.funcFreqCaseThree = convertToStrInt(loadCSVtoHashMap(createFilePath(statFolder, funcFreqCaseThreeFile)));
        this.funcFreqCaseFour = convertToStrInt(loadCSVtoHashMap(createFilePath(statFolder, funcFreqCaseFourFile)));
        this.caseTwoRowsApart = convertToIntInt(loadCSVtoHashMap(createFilePath(statFolder, caseTwoRowsApartFile)));

        HashMap<Integer, Integer> perCaseStats = convertToIntInt(loadCSVtoHashMap(createFilePath(statFolder, perCaseStatsFile)));
        caseTwoIntermediate = perCaseStats.getOrDefault(caseTwo, 0);
        caseThreeSingleColumn = perCaseStats.getOrDefault(caseThree, 0);
    }

    @Override
    public void collectStats(ColumnPattern columnPattern) {
        Stack<RefWithMeta> refWithMetaStack = new Stack<>();
        for (int i = 0; i < columnPattern.getFormulaTokens().length; i++) {
            FormulaToken formulaToken = columnPattern.getFormulaTokens()[i];
            if (formulaToken.isRef()) {
                EdgeMeta edgeMeta = columnPattern.getEdgeMetas()[i];
                refWithMetaStack.push(new RefWithMeta(formulaToken.getRef(), edgeMeta));
            }
            else {
                String funcStr = formulaToken.getFunctionStr();
                List<RefWithMeta> refWithMetaList = new ArrayList<>();
                for (int j = 0; j < formulaToken.getNumOperands(); j++)
                    refWithMetaList.add(refWithMetaStack.pop());

                List<RefWithMeta> filteredList =
                        refWithMetaList.stream().filter(e -> !e.getEdgeMeta().isConstant).collect(Collectors.toList());
                if (isCaseZero(filteredList)) { // Case zero
                    int numFunc = numFunctionsPerCase.getOrDefault(caseZero, 0);
                    numFunctionsPerCase.put(caseZero, numFunc + 1);
                } else if (isCaseOne(filteredList)) {
                    int numFunc = numFunctionsPerCase.getOrDefault(caseOne, 0);
                    numFunctionsPerCase.put(caseOne, numFunc + 1);

                    numFunc = funcFreqCaseOne.getOrDefault(funcStr, 0);
                    funcFreqCaseOne.put(funcStr, numFunc + 1);

                } else if (isCaseTwo(filteredList)) {
                    int numFunc = numFunctionsPerCase.getOrDefault(caseTwo, 0);
                    numFunctionsPerCase.put(caseTwo, numFunc + 1);

                    numFunc = funcFreqCaseTwo.getOrDefault(funcStr, 0);
                    funcFreqCaseTwo.put(funcStr, numFunc + 1);

                    if (hasIntermediate(filteredList))
                        caseTwoIntermediate += 1;
                    else {
                        int rowsApart = getRowsApart(filteredList);
                        numFunc = caseTwoRowsApart.getOrDefault(rowsApart, 0);
                        caseTwoRowsApart.put(rowsApart, numFunc + 1);
                    }

                } else if (isCaseThree(filteredList)) {
                    int numFunc = numFunctionsPerCase.getOrDefault(caseThree, 0);
                    numFunctionsPerCase.put(caseThree, numFunc + 1);

                    numFunc = funcFreqCaseThree.getOrDefault(funcStr, 0);
                    funcFreqCaseThree.put(funcStr, numFunc + 1);

                    if (isSingleColumn(filteredList))
                        caseThreeSingleColumn += 1;

                } else {
                    int numFunc = numFunctionsPerCase.getOrDefault(caseFour, 0);
                    numFunctionsPerCase.put(caseFour, numFunc + 1);

                    numFunc = funcFreqCaseFour.getOrDefault(funcStr, 0);
                    funcFreqCaseFour.put(funcStr, numFunc + 1);
                }

                Ref ref = new RefImpl(1, 1,
                        columnPattern.getNumCells(), 1);
                ref.setIntermediate(true);
                EdgeMeta edgeMeta = new EdgeMeta(PatternType.TYPEONE,
                        new Offset(1, 1),
                        new Offset(1, 1));
                refWithMetaStack.push(new RefWithMeta(ref, edgeMeta));
            }
        }
    }

    private boolean isCaseZero(List<RefWithMeta> filteredList) {
        return filteredList.size() == 0;
    }

    private boolean isCaseOne(List<RefWithMeta> filteredList) {
        return filteredList.size() == 1 && isCell(filteredList.get(0));
    }

    private boolean isCaseTwo(List<RefWithMeta> filteredList) {
        return filteredList.stream().allMatch(this::isCell);
    }

    private boolean isCaseThree(List<RefWithMeta> filteredList) {
        return filteredList.size() == 1 && !isCell(filteredList.get(0));
    }

    private boolean isCell(RefWithMeta refWithMeta) {
        EdgeMeta edgeMeta = refWithMeta.getEdgeMeta();
        if (edgeMeta.patternType != PatternType.TYPEFOUR)
            return edgeMeta.startOffset.equals(edgeMeta.endOffset);
        else
            return refWithMeta.getRef().getCellCount() == 1;
    }

    private boolean hasIntermediate(List<RefWithMeta> filteredList) {
        return filteredList.stream().anyMatch(refWithMeta -> (
            refWithMeta.getRef().isIntermediate()));
    }

    private boolean isSingleColumn(List<RefWithMeta> filteredList) {
        EdgeMeta edgeMeta = filteredList.get(0).getEdgeMeta();
        return edgeMeta.startOffset.getColOffset() == edgeMeta.endOffset.getColOffset();
    }

    private int getRowsApart(List<RefWithMeta> filteredList) {
        int minRow = Integer.MAX_VALUE;
        int maxRow = Integer.MIN_VALUE;
        for (RefWithMeta refWithMeta: filteredList) {
            EdgeMeta edgeMeta = refWithMeta.getEdgeMeta();
            if (edgeMeta.patternType == PatternType.TYPEONE) {
                if (edgeMeta.startOffset.getRowOffset() < minRow)
                    minRow = edgeMeta.startOffset.getRowOffset();
                if (edgeMeta.endOffset.getRowOffset() > maxRow)
                    maxRow = edgeMeta.endOffset.getRowOffset();
            }
        }
        if (minRow == Integer.MAX_VALUE) return 0;
        else return maxRow - minRow;
    }

    @Override
    public void writeStats() {
        HashMap<Integer, Integer> perCaseStats = new HashMap<>();
        perCaseStats.put(2, caseTwoIntermediate);
        perCaseStats.put(3, caseThreeSingleColumn);
        try {
            writeHashMapToCSV(numFunctionsPerCase, createFilePath(statFolder, numFunctionsPerCaseFile));
            writeHashMapToCSV(funcFreqCaseOne, createFilePath(statFolder, funcFreqCaseOneFile));
            writeHashMapToCSV(funcFreqCaseTwo, createFilePath(statFolder, funcFreqCaseTwoFile));
            writeHashMapToCSV(funcFreqCaseThree, createFilePath(statFolder, funcFreqCaseThreeFile));
            writeHashMapToCSV(funcFreqCaseFour, createFilePath(statFolder, funcFreqCaseFourFile));
            writeHashMapToCSV(caseTwoRowsApart, createFilePath(statFolder, caseTwoRowsApartFile));
            writeHashMapToCSV(perCaseStats, createFilePath(statFolder, perCaseStatsFile));
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }
}

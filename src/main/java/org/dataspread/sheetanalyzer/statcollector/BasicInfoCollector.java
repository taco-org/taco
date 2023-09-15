package org.dataspread.sheetanalyzer.statcollector;

import org.dataspread.sheetanalyzer.dependency.util.EdgeMeta;
import org.dataspread.sheetanalyzer.dependency.util.Offset;
import org.dataspread.sheetanalyzer.dependency.util.PatternType;
import org.dataspread.sheetanalyzer.dependency.util.RefWithMeta;
import org.dataspread.sheetanalyzer.util.FormulaToken;
import org.dataspread.sheetanalyzer.util.Ref;
import org.dataspread.sheetanalyzer.util.RefImpl;

import java.io.*;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

import static org.dataspread.sheetanalyzer.statcollector.StatsCollectorUtil.createFilePath;

public class BasicInfoCollector implements StatsCollector {

    private final String statFolder;
    private final String spreadsheetFile = "Spreadsheet.csv";
    private final String templateFile = "Template.csv";
    private final String functionFile = "Function.csv";
    private final String refFile = "Reference.csv";

    private final String del = ",";
    private final String newline = "\n";
    private final int startID = 1;
    private final int ssID;
    private final String ssName;
    private final String dsName;

    private final String[] rangeFunctions = {"FILTER", "INDIRECT", "LINEST", "LOGEST",
            "MINVERSE", "MMULT", "MUNIT", "OFFSET", "TRANSPOSE", "TREND"};

    private final List<ColumnPattern> cpList = new LinkedList<>();

    public BasicInfoCollector(String statFolder,
                              String ssName,
                              String dsName) {
        this.statFolder = statFolder;
        this.ssID = getSSID();
        this.ssName = ssName;
        this.dsName = dsName;
    }

    private int getSSID() {
        File file = new File(createFilePath(statFolder, spreadsheetFile));

        // Check if the file exists
        if (file.exists()) {
            try {
                return readLastSSID(file);
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(-1);
            }
        }
        return startID;
    }

    private int readLastSSID(File file) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            String lastLine = null;
            while ((line = br.readLine()) != null) {
                lastLine = line;
            }
            if (lastLine != null)
                return Integer.parseInt(lastLine.split(del)[0]);
            else
                return startID;
        }
    }

    @Override
    public void collectStats(ColumnPattern columnPattern) {
        cpList.add(columnPattern);
    }

    @Override
    public void writeStats() {
        try (BufferedWriter ssBW = new BufferedWriter(new FileWriter(createFilePath(statFolder, spreadsheetFile), true));
             BufferedWriter templateBW = new BufferedWriter(new FileWriter(createFilePath(statFolder, templateFile), true));
             BufferedWriter funcBW = new BufferedWriter(new FileWriter(createFilePath(statFolder, functionFile), true));
             BufferedWriter refBW = new BufferedWriter(new FileWriter(createFilePath(statFolder, refFile), true))
             ) {
            // Write to the SpreadSheet file
            ssBW.write(ssID + del + ssName + del + dsName + newline);

            int templateID = startID;
            for (ColumnPattern cp : cpList) {

                // Write to the Template file
                String templateStr = cp.getTemplateString();
                int numFunc = cp.getNumFunc();
                templateBW.write(ssID + del + templateID + del + templateStr + del + numFunc + newline);

                // Write to the Function and Ref files
                int funcID = startID;
                Stack<RefWithMeta> refWithMetaStack = new Stack<>();
                for (int i = 0; i < cp.getFirstFormulaTokens().length; i++) {
                    FormulaToken ft = cp.getFirstFormulaTokens()[i];
                    if (ft.isRef()) {
                        EdgeMeta edgeMeta = cp.getEdgeMetas()[i];
                        refWithMetaStack.push(new RefWithMeta(ft.getRef(), edgeMeta));
                    } else {
                        String funcStr = ft.getFunctionStr();
                        LinkedList<RefWithMeta> refWithMetaList = new LinkedList<>();
                        for (int j = 0; j < ft.getNumOperands(); j++)
                            refWithMetaList.addFirst(refWithMetaStack.pop());
                        funcBW.write(ssID + del + templateID + del + funcID + del
                                + funcStr + del + refWithMetaList.size() + newline);

                        int refID = startID;
                        for (RefWithMeta refWithMeta : refWithMetaList) {
                            EdgeMeta em = refWithMeta.getEdgeMeta();
                            Ref ref = refWithMeta.getRef();
                            refBW.write(ssID + del + templateID + del + funcID + del + refID + del + refWithMeta.getPatternType() + del
                                    + em.startOffset.getRowOffset() + del + em.startOffset.getColOffset() + del
                                    + em.endOffset.getRowOffset() + del + em.endOffset.getColOffset() + del
                                    + ref.getScalarValue() + del + ref.isIntermediate() + del + ref.getRefFuncID() + newline);
                            refID += 1;
                        }

                        Ref interRef;
                        if (Arrays.stream(rangeFunctions).anyMatch(rangFunc -> rangFunc.compareToIgnoreCase(funcStr) == 0)) {
                            interRef = new RefImpl(1, 1, 2, 2);
                        } else {
                            interRef = new RefImpl(1, 1, 1, 1);
                        }
                        interRef.setIntermediate(true);

                        EdgeMeta edgeMeta = new EdgeMeta(PatternType.TYPEONE,
                                new Offset(1, 1),
                                new Offset(1, 1));
                        if (refWithMetaList.stream().allMatch(refWithMeta -> refWithMeta.getEdgeMeta().patternType == PatternType.TYPEFOUR)) {
                            edgeMeta = new EdgeMeta(PatternType.TYPEFOUR,
                                    new Offset(1, 1),
                                    new Offset(1, 1));
                        }

                        refWithMetaStack.push(new RefWithMeta(interRef, edgeMeta));

                        funcID++;
                    }
                }
                templateID++;
            }
        } catch (IOException e) {
            System.out.println("Writing Spreadsheet File Failed");
            System.exit(-1);
        }
    }

}

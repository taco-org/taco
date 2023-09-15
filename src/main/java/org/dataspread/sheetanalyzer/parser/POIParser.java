package org.dataspread.sheetanalyzer.parser;

import org.apache.poi.hssf.usermodel.HSSFEvaluationWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.formula.FormulaParser;
import org.apache.poi.ss.formula.FormulaParsingWorkbook;
import org.apache.poi.ss.formula.FormulaRenderingWorkbook;
import org.apache.poi.ss.formula.FormulaType;
import org.apache.poi.ss.formula.ptg.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFEvaluationWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.dataspread.sheetanalyzer.util.*;
import org.dataspread.sheetanalyzer.dependency.util.EdgeType;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.*;

public class POIParser implements SpreadsheetParser {

    private Workbook workbook;
    private FormulaParsingWorkbook evalbook;
    private final HashMap<String, SheetData> sheetDataMap;
    private final String fileName;

    public POIParser(String filePath) throws SheetNotSupportedException {
        File fileItem = new File(filePath);
        fileName = fileItem.getName();
        sheetDataMap = new HashMap<>();

        try {
            this.workbook = WorkbookFactory.create(fileItem);
            if (workbook instanceof HSSFWorkbook) {
                this.evalbook = HSSFEvaluationWorkbook.create((HSSFWorkbook) workbook);
            } else if (workbook instanceof XSSFWorkbook) {
                this.evalbook = XSSFEvaluationWorkbook.create((XSSFWorkbook) workbook);
            } else {
                throw new SheetNotSupportedException();
            }
            parseSpreadsheet();
        } catch (Exception e) {
            e.printStackTrace();
            throw new SheetNotSupportedException("Parsing " + filePath + " failed");
        } finally {
            if (workbook != null) {
                try {
                    workbook.close();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (RuntimeException ignored) {

                }
            }
        }
    }

    public POIParser(Map<String, String[][]> sheetContent) throws SheetNotSupportedException {
        try {
            fileName = "TempWorkbook";
            sheetDataMap = new HashMap<>();
            workbook = new XSSFWorkbook();
            evalbook = XSSFEvaluationWorkbook.create((XSSFWorkbook) workbook);
            parseSheetContentToWorkbook(sheetContent);
            parseSpreadsheet();
        } catch (Exception e) {
            throw new SheetNotSupportedException("Parsing formulae failed");
        }
    }

    private void parseSheetContentToWorkbook(Map<String, String[][]> sheetContent) {
        for (Map.Entry<String, String[][]> sheet : sheetContent.entrySet()) {
            String sheetName = sheet.getKey();
            String[][] sheetCells = sheet.getValue();
            parseCellsToWorkbook(sheetName, sheetCells);
        }
    }

    private void parseCellsToWorkbook(String sheetName, String[][] cells) {
        Sheet sheet = workbook.createSheet(sheetName);
        for (int i = 0; i < cells.length; i++) {
            Row row = sheet.createRow(i);
            for (int j = 0; j < cells[0].length; j++) {
                String cellString = cells[i][j];
                boolean isFormula = cellString.startsWith("=");
                CellType cellType = isFormula ? CellType.FORMULA : CellType.STRING;
                Cell cell = row.createCell(j, cellType);
                if (isFormula) {
                    cell.setCellFormula(cellString.substring(1));
                } else if (cellString.length() < 1) {
                    cell.setBlank();
                } else {
                    cell.setCellValue(cellString);
                }
            }
        }
    }

    public String getFileName() {
        return fileName;
    }

    public HashMap<String, SheetData> getSheetData() {
        return sheetDataMap;
    }

    public boolean skipParsing(int threshold) {
        int totalRows = 0;
        for(Sheet sheet: workbook) {
            totalRows += sheet.getPhysicalNumberOfRows();
        }
        return totalRows <= threshold;
    }

    private void parseSpreadsheet() {
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            SheetData sheetData = parseOneSheet(workbook.getSheetAt(i));
            sheetDataMap.put(workbook.getSheetAt(i).getSheetName()
                    .replace(',','-'), sheetData);
        }
    }

    private SheetData parseOneSheet(Sheet sheet) {
        SheetData sheetData = new SheetData(sheet.getSheetName());
        int maxRows = 0;
        int maxCols = 0;
        for (Row row : sheet) {
            for (Cell cell : row) {
                if (cell.getColumnIndex() > maxCols) maxCols = cell.getColumnIndex();
                if (cell.getCellType() == CellType.FORMULA) {
                    parseOneFormulaCell(sheetData, cell);
                }
            }
            if (row.getRowNum() > maxRows) maxRows = row.getRowNum();
        }

        // for (int i = 0; i <= maxCols; i++) {
        //     for (int j = 0; j <= maxRows; j++) {
        //         Row row = sheet.getRow(j);
        //         if (row != null) {
        //             Cell cell = row.getCell(i);
        //             if (cell != null) {
        //                 if (cell.getCellType() == CellType.FORMULA) {
        //                     parseOneFormulaCell(sheetData, cell);
        //                 }
        //                 // else {
        //                 //     Ref dep = new RefImpl(cell.getRowIndex(), cell.getColumnIndex());
        //                 //     CellContent cellContent = new CellContent(getCellContentString(cell),
        //                 //             "", null, false);
        //                 //     sheetData.addContent(dep, cellContent);
        //                 // }
        //             }
        //         }
        //     }
        // }

        sheetData.setMaxRowsCols(maxRows, maxCols);

        return sheetData;
    }

    private String getCellContentString(Cell cell) {
        switch (cell.getCellType()) {
            case ERROR:
                return String.valueOf(cell.getErrorCellValue());
            case STRING:
                return cell.getStringCellValue();
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case NUMERIC:
                return String.valueOf(cell.getNumericCellValue());
            default:
                return "";
        }
    }

    private void parseOneFormulaCell(SheetData sheetData, Cell cell) {
        // Dependent Ref
        Ref dep = new RefImpl(cell.getRowIndex(), cell.getColumnIndex());

        try {
            Ptg[] tokens = this.getTokens(cell);
            if (tokens == null) return;
            List<Ref> precList = new LinkedList<>();
            int numRefs = 0;
            ArrayList<FormulaToken> formulaTokens = new ArrayList<>();

            for (Ptg ptg: tokens) {
                if (ptg instanceof OperandPtg) {
                    Ref prec = parseOneToken(cell, (OperandPtg) ptg, sheetData);
                    prec.setConstant(false);
                    precList.add(prec);
                    numRefs += 1;

                    formulaTokens.add(new FormulaToken(prec, "", 0));
                } else if (ptg instanceof OperationPtg) {
                    OperationPtg operationPtg = (OperationPtg) ptg;
                    int numOperands = operationPtg.getNumberOfOperands();
                    String[] operands = new String[numOperands];
                    Arrays.fill(operands, "");

                    String funcStr = operationPtg.toFormulaString(operands).replaceAll("[,()]+$", "");
                    if (funcStr.startsWith("#")) // A formula indicating an error
                        return;
                    formulaTokens.add(new FormulaToken(null, funcStr, numOperands));
                } else if (ptg instanceof ScalarConstantPtg) {
                    Ref prec = new RefImpl(0, 0);
                    prec.setConstant(true);
                    prec.setScalarValue(ptg.toFormulaString());
                    formulaTokens.add(new FormulaToken(prec, "", 0));
                } else {
                   return;
                }
            }

            if (!precList.isEmpty())
                sheetData.addDeps(dep, precList);
            sheetData.addFormulaNumRef(dep, numRefs);
            CellContent cellContent = new CellContent("",
                    cell.getCellFormula(), formulaTokens.toArray(new FormulaToken[0]), true);
            sheetData.addContent(dep, cellContent);
        } catch (SheetNotSupportedException e) {
            // CellContent cellContent = new CellContent("",
            //         "", null, false);
            // sheetData.addContent(dep, cellContent);
        }
    }

    private Ref parseOneToken(Cell cell, OperandPtg token,
                              SheetData sheetData) throws SheetNotSupportedException {
        Sheet sheet = this.getDependentSheet(cell, token);
        if (sheet != null) {
            if (token instanceof Area2DPtgBase) {
                Area2DPtgBase ptg = (Area2DPtgBase) token;
                int rowStart = ptg.getFirstRow();
                int colStart = ptg.getFirstColumn();
                int rowEnd = ptg.getLastRow();
                int colEnd = ptg.getLastColumn();

                // ADD: Set area dollar sign
                Ref areaRef = new RefImpl(rowStart, colStart, rowEnd, colEnd);
                if (!ptg.isFirstColRelative()) {
                    areaRef.setLeftUpColumnDollar();
                }
                if (!ptg.isFirstRowRelative()) {
                    areaRef.setLeftUpRowDollar();
                }
                if (!ptg.isLastColRelative()) {
                    areaRef.setRightDownColumnDollar();
                }
                if (!ptg.isLastRowRelative()) {
                    areaRef.setRightDownRowDollar();
                }

                if (!sheetData.areaAccessed(areaRef)) {
                    sheetData.addOneAccess(areaRef);
                    for (int r = ptg.getFirstRow(); r <= ptg.getLastRow(); r++) {
                        for (int c = ptg.getFirstColumn(); c <= ptg.getLastColumn(); c++) {
                            Cell dep = this.getCellAt(sheet, r, c);
                            if (dep == null) {
                                Ref cellRef = new RefImpl(r, c);
                                if (sheetData.getCellContent(cellRef) == null) {
                                    sheetData.addContent(cellRef,
                                            CellContent.getNullCellContent());
                                }
                            }
                        }
                    }
                }
                return areaRef;
            } else if (token instanceof RefPtg) {
                RefPtg ptg = (RefPtg) token;
                int row = ptg.getRow();
                int col = ptg.getColumn();
                Cell dep = this.getCellAt(sheet, row, col);
                if (dep == null) {
                    sheetData.addContent(new RefImpl(row, col),
                            CellContent.getNullCellContent());
                }

                Ref new_ref = new RefImpl(row, col, row, col);
                // ADD: Set dollar sign
                if (!ptg.isRowRelative()) {
                    new_ref.setLeftUpRowDollar();
                    new_ref.setRightDownRowDollar();
                }
                if (!ptg.isColRelative()) {
                    new_ref.setLeftUpColumnDollar();
                    new_ref.setRightDownColumnDollar();
                }
                return new_ref;
                //return new RefImpl(row, col, row, col);
            } else if (token instanceof Area3DPtg ||
                    token instanceof Area3DPxg ||
                    token instanceof Ref3DPtg ||
                    token instanceof Ref3DPxg) {
                throw new SheetNotSupportedException();
            }
        }

        throw new SheetNotSupportedException();
    }

    private Sheet getDependentSheet (Cell src, OperandPtg opPtg) throws SheetNotSupportedException {
        Sheet sheet = null;
        if (opPtg instanceof RefPtg) {
            sheet = src.getSheet();
        } else if (opPtg instanceof Area2DPtgBase) {
            sheet = src.getSheet();
        } else {
            throw new SheetNotSupportedException();
        }

        // else if (opPtg instanceof Ref3DPtg) {
        //     sheet = this.workbook.getSheet(this.getSheetNameFrom3DRef((Ref3DPtg) opPtg));
        // } else if (opPtg instanceof Area3DPtg) {
        //     sheet = this.workbook.getSheet(this.getSheetNameFrom3DRef((Area3DPtg) opPtg));
        // }
        return sheet;
    }

    private String getSheetNameFrom3DRef (OperandPtg ptg) {
        String sheetName = null;
        if (ptg instanceof Ref3DPtg) {
            Ref3DPtg ptgRef3D = (Ref3DPtg) ptg;
            sheetName = ptgRef3D.toFormulaString((FormulaRenderingWorkbook) this.evalbook);
        } else if (ptg instanceof Area3DPtg) {
            Area3DPtg ptgArea3D = (Area3DPtg) ptg;
            sheetName = ptgArea3D.toFormulaString((FormulaRenderingWorkbook) this.evalbook);
        }
        return sheetName != null ? sheetName.substring(0, sheetName.indexOf('!')) : null;
    }

    private Cell getCellAt (Sheet sheet, int rowIdx, int colIdx) {
        Cell cell;
        try {
            cell = sheet.getRow(rowIdx).getCell(colIdx);
        } catch (NullPointerException e) {
            return null;
        }
        return cell;
    }

    private Ptg[] getTokens (Cell cell) {
        try {
            return FormulaParser.parse(
                    cell.getCellFormula(),
                    this.evalbook,
                    FormulaType.CELL,
                    this.workbook.getSheetIndex(cell.getSheet()),
                    cell.getRowIndex()
            );
        } catch (Exception e) { return null; }
    }
}

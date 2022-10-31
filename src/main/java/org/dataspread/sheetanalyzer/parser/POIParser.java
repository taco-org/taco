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
import org.apache.xmlgraphics.util.dijkstra.Edge;
import org.dataspread.sheetanalyzer.util.*;
import org.dataspread.sheetanalyzer.dependency.util.EdgeType;
import org.w3c.dom.Attr;

import java.io.File;
import java.io.IOException;
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
            throw new SheetNotSupportedException("Parsing " + filePath + " failed");
        } finally {
            if (workbook != null) {
                try {
                    workbook.close();
                } catch (IOException e) {
                    e.printStackTrace();
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
            for (Cell cell : row)
                if (cell.getColumnIndex() > maxCols) maxCols = cell.getColumnIndex();
            if (row.getRowNum() > maxRows) maxRows = row.getRowNum();
        }

        for (int i = 0; i <= maxCols; i++) {
            for (int j = 0; j <= maxRows; j++) {
                Row row = sheet.getRow(j);
                if (row != null) {
                    Cell cell = row.getCell(i);
                    if (cell != null) {
                        if (cell.getCellType() == CellType.FORMULA) {
                            parseOneFormulaCell(sheetData, cell);
                        } else {
                            Ref dep = new RefImpl(cell.getRowIndex(), cell.getColumnIndex());
                            CellContent cellContent = new CellContent(getCellContentString(cell),
                                    "", false);
                            sheetData.addContent(dep, cellContent);
                        }
                    }
                }
            }
        }

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
            assert tokens != null;
            EdgeType edgeType = null;
            int numberOfOperands = -1;
            List<Ref> precList = new LinkedList<>();
            Deque<Ref> precDeque = new ArrayDeque<>();
            int numRefs = 0;

            for (Ptg ptg: tokens) {
                if (ptg instanceof OperandPtg) {
                    Ref prec = parseOneToken(cell, (OperandPtg) ptg, sheetData);
                    if (prec != null) {
                        numRefs += 1;
                        precDeque.push(prec);
                    }
                }
                if (ptg instanceof OperationPtg || ptg instanceof AttrPtg) {
                    if (ptg instanceof AttrPtg) {
                        numberOfOperands = ((AttrPtg) ptg).getNumberOfOperands();
                    } else {
                        numberOfOperands = ((OperationPtg) ptg).getNumberOfOperands();
                    }

                    if (ptg instanceof AddPtg) {
                        edgeType = EdgeType.ADD;
                    } else if (ptg instanceof SubtractPtg) {
                        edgeType = EdgeType.SUBTRACT;
                    } else if (ptg instanceof MultiplyPtg) {
                        edgeType = EdgeType.MULTIPLY;
                    } else if (ptg instanceof DividePtg) {
                        edgeType = EdgeType.DIVIDE;
                    } else if (ptg instanceof PowerPtg) {
                        edgeType = EdgeType.POWER;
                    } else if (ptg instanceof GreaterEqualPtg ||
                            ptg instanceof GreaterThanPtg ||
                            ptg instanceof LessThanPtg ||
                            ptg instanceof LessEqualPtg ||
                            ptg instanceof NotEqualPtg ||
                            ptg instanceof EqualPtg) {
                        edgeType = EdgeType.COMPARE;
                    } else if (ptg instanceof FuncVarPtg) {
                        edgeType = EdgeType.FUNCTION;
                    } else if (ptg instanceof IntersectionPtg || ptg instanceof UnionPtg) {
                        edgeType = EdgeType.SETOPERATION;
                    } else {
                        if (ptg instanceof AttrPtg) {
                            edgeType = EdgeType.FUNCTION;
                        } else {
                            edgeType = EdgeType.OTHER;
                        }
                    }

                    int count = 0;
                    while (count < numberOfOperands && !precDeque.isEmpty()) {
                        Ref popRef = precDeque.pop();
                        popRef.setEdgeType(edgeType);
                        precList.add(popRef);
                        count += 1;
                    }
                }
            }

            while (!precDeque.isEmpty()) {
                Ref popRef = precDeque.pop();
                precList.add(popRef);
            }

            if (!precList.isEmpty())
                sheetData.addDeps(dep, precList);
            sheetData.addFormulaNumRef(dep, numRefs);
            CellContent cellContent = new CellContent("",
                    cell.getCellFormula(), true);
            sheetData.addContent(dep, cellContent);
        } catch (SheetNotSupportedException e) {
            CellContent cellContent = new CellContent("",
                    "", false);
            sheetData.addContent(dep, cellContent);
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

                /*
                String content = ptg.toFormulaString();
                int first_d_loc = -1, second_d_loc = -1;
                first_d_loc = content.indexOf("$");
                if (first_d_loc != -1) {
                    if (first_d_loc == 0) {
                        second_d_loc = content.indexOf("$", first_d_loc + 1);
                    } else {
                        second_d_loc = first_d_loc;
                        first_d_loc = -1;
                    }
                }
                */

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

        return null;
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

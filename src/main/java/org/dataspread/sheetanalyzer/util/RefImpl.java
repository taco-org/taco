package org.dataspread.sheetanalyzer.util;

import org.dataspread.sheetanalyzer.dependency.util.EdgeType;

import java.io.Serializable;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 
 * @author dennis
 * @since 3.5.0
 */
public class RefImpl implements Ref, Serializable {

	private static final long serialVersionUID = 1L;
	protected String bookName;
	protected String sheetName;
	protected String lastSheetName;
	private RefType _type;
	private int _row;
	private int _column;
	private int _lastRow;
	private int _lastColumn;
	private int _sheetIdx = -1; // tricky! used in IntervalTree only!
	private Set<Ref> _precedents;

	// ADD: indicator of dollar sign
	private boolean left_up_column_dollar = false;
	private boolean left_up_row_dollar = false;
	private boolean right_down_column_dollar = false;
	private boolean right_down_row_dollar = false;

	// ADD: Edgetype
	private EdgeType edgeType = EdgeType.OTHER;

	@SuppressWarnings("unused")
	public RefImpl() {
		// Required for serialization.
	}

	public RefImpl(int row, int column) {
		this(RefType.CELL, Catalog.bookName, Catalog.sheetName, null, row, column, row, column);
	}

	public RefImpl(int row, int column, int lastRow, int lastColumn) {
		this((row==lastRow&&column==lastColumn)?RefType.CELL:RefType.AREA, Catalog.bookName,
				Catalog.sheetName, null, row, column, lastRow,lastColumn);
	}

	public RefImpl(String bookName, String sheetName, int row, int column,
			int lastRow, int lastColumn) {
		this((row==lastRow&&column==lastColumn)?RefType.CELL:RefType.AREA, bookName, sheetName, null, row, column, lastRow,lastColumn);
	}

	public RefImpl(String bookName, String sheetName, int row, int column) {
		this(RefType.CELL, bookName, sheetName, null, row, column, row, column);
	}

	public RefImpl(String bookName, String sheetName, String lastSheetName, int row, int column,
			int lastRow, int lastColumn) {
		this((row==lastRow&&column==lastColumn)?RefType.CELL:RefType.AREA, bookName, sheetName, lastSheetName, row, column, lastRow,lastColumn);
	}

	public RefImpl(String bookName, String sheetName, String lastSheetName, int row, int column) {
		this(RefType.CELL, bookName, sheetName, lastSheetName, row, column, row, column);
	}

	public RefImpl(String bookName, String sheetName, int sheetIdx) {
		this(RefType.SHEET, bookName, sheetName, null, -1, -1, -1, -1);
		this._sheetIdx = sheetIdx;
	}

	public RefImpl(String bookName) {
		this(RefType.BOOK, bookName, null, null, -1, -1, -1, -1);
	}


	protected RefImpl(RefType type, String bookName, String sheetName, String lastSheetName,
			int row, int column, int lastRow, int lastColumn) {
		this._type = type;
		this.bookName = bookName;
		this.sheetName = sheetName;
		this.lastSheetName = lastSheetName;
		this._row = row;
		this._column = column;
		this._lastRow = lastRow;
		this._lastColumn = lastColumn;
	}

	public void setSheetName(String sheetName) {
		this.sheetName = sheetName;
	}

	// ADD: set dollar sign
	@Override
	public void setLeftUpColumnDollar() {
		left_up_column_dollar = true;
	}

	@Override
	public void setLeftUpRowDollar() {
		left_up_row_dollar = true;
	}

	@Override
	public void setRightDownColumnDollar() {
		right_down_column_dollar = true;
	}

	@Override
	public void setRightDownRowDollar() {
		right_down_row_dollar = true;
	}

	@Override
	public boolean checkLeftUpColumnDollar() {
		return left_up_column_dollar;
	}

	@Override
	public boolean checkLeftUpRowDollar() {
		return left_up_row_dollar;
	}

	@Override
	public boolean checkRightDownColumnDollar() {
		return right_down_column_dollar;
	}

	@Override
	public boolean checkRightDownRowDollar() {
		return right_down_row_dollar;
	}

	@Override
	public RefType getType() {
		return _type;
	}

	@Override
	public void setType(RefType type) {
		_type = type;
	}

	@Override
	public void setRow(int row) {
		_row = row;
	}

	@Override
	public void setColumn(int column) {
		_column = column;
	}

	@Override
	public void setLastRow(int lastRow) {
		_lastRow = lastRow;
	}

	@Override
	public void setLastColumn(int lastColumn) {
		_lastColumn = lastColumn;
	}

	// ADD: set edge type
	@Override
	public void setEdgeType(EdgeType type) {
		edgeType = type;
	}

	@Override
	public EdgeType getEdgeType() {
		return edgeType;
	}

	@Override
	public String getBookName() {
		return bookName;
	}

	@Override
	public String getSheetName() {
		return sheetName;
	}
	
	@Override
	public String getLastSheetName() {
		return lastSheetName;
	}

	@Override
	public int getRow() {
		return _row;
	}

	@Override
	public int getColumn() {
		return _column;
	}

	@Override
	public int getLastRow() {
		return _lastRow;
	}

	@Override
	public int getLastColumn() {
		return _lastColumn;
	}

	@Override
	public Set<Ref> getPrecedents() {
		return _precedents;
	}

	@Override
	public void addPrecedent(Ref precedent) {
		if (_precedents == null)
			_precedents = new LinkedHashSet<Ref>();
		_precedents.add(precedent);
	}

	@Override
	public void clearDependent() {
		_precedents.clear();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((bookName == null) ? 0 : bookName.hashCode());
		result = prime * result + _column;
		result = prime * result + _lastColumn;
		result = prime * result + _lastRow;
		result = prime * result + _row;
		result = prime * result
				+ ((sheetName == null) ? 0 : sheetName.hashCode());
		result = prime * result
				+ ((lastSheetName == null) ? 0 : lastSheetName.hashCode());
		result = prime * result + ((_type == null) ? 0 : _type.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!(obj instanceof Ref))
			return false;
		Ref other = (Ref) obj;
		
		if (bookName == null) {
			if (other.getBookName() != null)
				return false;
		} else if (!bookName.equals(other.getBookName()))
			return false;
		if (_column != other.getColumn())
			return false;
		if (_lastColumn != other.getLastColumn())
			return false;
		if (_lastRow != other.getLastRow())
			return false;
		if (_row != other.getRow())
			return false;
		if (sheetName == null) {
			if (other.getSheetName() != null)
				return false;
		} else if (!sheetName.equals(other.getSheetName()))
			return false;
		if (lastSheetName == null) {
			if (other.getLastSheetName() != null)
				return false;
		} else if (!lastSheetName.equals(other.getLastSheetName()))
			return false;
		return _type == other.getType();
	}

	private String fromNumToAlphabet(int i) {
		return i >= 0 && i < 26 ? String.valueOf((char)(i + 65)) : Integer.toString(i);
    }

	private String genColumnLabel(int column) {
		int remaining = column;
		int digit = 1;
		StringBuilder colLabel = new StringBuilder();
		while (Math.pow(26, digit) < remaining + 1) {
			remaining = remaining - digit * 26;
			digit++;
		}
		for (int i = 0; i < digit; i++) {
			int cur = remaining % 26;
			remaining = remaining / 26;
			colLabel.append(fromNumToAlphabet(cur));
		}
		return colLabel.reverse().toString();
	}

	private String genCellString(int row, int column) {
		return genColumnLabel(column) + (row + 1);
	}


	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		switch (_type) {
		case AREA:
			sb.insert(0, "(" + genCellString(_row, _column)  +
					":" + genCellString(_lastRow, _lastColumn) + ")");
			break;
			// sb.insert(0,":( "+ _lastRow + " , " + _lastColumn + ")");
		case CELL:
			sb.insert(0, genCellString(_row, _column));
			break;
			// sb.insert(0, ":( "+ _row + " , " + _column + ")");
		case SHEET:
			if(lastSheetName != null) {
				sb.insert(0, sheetName + ":" + lastSheetName + "!");
			} else {
				sb.insert(0, sheetName + "!");
			}
			break;
		case OBJECT://will be override
			if(lastSheetName!=null){
				sb.insert(0, sheetName + ":" + lastSheetName + "!");
			}else if(sheetName!=null){
				sb.insert(0, sheetName + "!");
			}
		case NAME://will be override
		case BOOK:
		case INDIRECT:
		case TABLE: //ZSS-960
		}

		// sb.insert(0, sheetName + ":");
		return sb.toString();
	}

	//Tricky! This is for IntervalTree only
	@Override
	public int getSheetIndex() {
		return _sheetIdx;
	}
	
	//Tricky! This is for IntervalTree only
	@Override
	public int getLastSheetIndex() {
		return -1;
	}

	@Override
	public Ref getBoundingBox(Ref target1) {
		RefImpl target = (RefImpl) target1;
		final int row1 = Math.min(this._row, target._row);
		final int row2 = Math.max(this._lastRow, target._lastRow);
		final int col1 = Math.min(this._column, target._column);
		final int col2 = Math.max(this._lastColumn, target._lastColumn);
		return new RefImpl(this.bookName, this.sheetName, row1, col1, row2, col2);
	}

	@Override
	public Ref getOverlap(Ref target1) {
		RefImpl target = (RefImpl) target1;
		final int row1 = Math.max(this._row, target._row);
		final int row2 = Math.min(this._lastRow, target._lastRow);
		if (row1 > row2) return null; // no overlapping

		final int col1 = Math.max(this._column, target._column);
		final int col2 = Math.min(this._lastColumn, target._lastColumn);
		if (col1 > col2) return null; // no overlapping

		return new RefImpl(this.bookName, this.sheetName, row1, col1, row2, col2);
	}

	@Override
	public Set<Ref> getNonOverlap(Ref target1) {
		Set<Ref> retSet = new HashSet<>();

		if (getOverlap(target1) == null) retSet.add(this);
		else {

			RefImpl target = (RefImpl) target1;
			int rRow = this._row;
			int rCol = this._column;
			int rLastRow = this._lastRow;
			int rLastCol = this._lastColumn;
			if (rRow <= rLastRow && rCol <= rLastCol && rCol < target._column) {
				Ref leftRef = new RefImpl(this.bookName, this.sheetName,
						rRow, rCol, rLastRow, Math.min(rLastCol, target._column - 1));
				retSet.add(leftRef);
				rCol = target._column;
			}

			if (rRow <= rLastRow && rCol <= rLastCol && rRow < target._row) {
				Ref upRef = new RefImpl(this.bookName, this.sheetName,
						rRow, rCol, Math.min(rLastRow, target._row - 1), rLastCol);
				retSet.add(upRef);
				rRow = target._row;
			}


			if (rRow <= rLastRow && rCol <= rLastCol && rLastRow > target._lastRow) {
				Ref downRef = new RefImpl(this.bookName, this.sheetName,
						Math.max(rRow, target._lastRow + 1), rCol, rLastRow, rLastCol);
				retSet.add(downRef);
				rLastRow = target._lastRow;
			}


			if (rRow <= rLastRow && rCol <= rLastCol && rLastCol > target._lastColumn) {
				Ref rightRef = new RefImpl(this.bookName, this.sheetName,
						rRow, Math.max(rCol, target._lastColumn + 1), rLastRow, rLastCol);
				retSet.add(rightRef);
			}
		}
		return retSet;
	}

	@Override
	public int getCellCount() {
		return (_lastRow - _row + 1) * (_lastColumn - _column + 1);
	}
}

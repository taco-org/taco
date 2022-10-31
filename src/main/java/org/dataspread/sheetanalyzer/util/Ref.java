package org.dataspread.sheetanalyzer.util;

import org.dataspread.sheetanalyzer.dependency.util.EdgeType;

import java.util.Set;

public interface Ref {
	RefType getType();

	void setType(Ref.RefType type);

	String getBookName();

	void setSheetName(String sheetName);

	String getSheetName();

	String getLastSheetName();

	int getRow();

	int getColumn();

	int getLastRow();

	int getLastColumn();

	void setRow(int row);
	void setColumn(int column);
	void setLastRow(int lastRow);
	void setLastColumn(int lastColumn);

	// ADD: set EdgeType
	void setEdgeType(EdgeType type);
	EdgeType getEdgeType();

	// ADD: interface for dollar setting and checking
	void setLeftUpColumnDollar();
	void setLeftUpRowDollar();
	void setRightDownColumnDollar();
	void setRightDownRowDollar();
	boolean checkLeftUpColumnDollar();
	boolean checkLeftUpRowDollar();
	boolean checkRightDownColumnDollar();
	boolean checkRightDownRowDollar();

	//ZSS-815
	//since 3.7.0
	int getSheetIndex();
	
	//ZSS-815
	//since 3.7.0
	int getLastSheetIndex();

	Set<Ref> getPrecedents();

	Ref getBoundingBox(Ref target);

	int getCellCount();
	Ref getOverlap(Ref target);
	Set<Ref> getNonOverlap(Ref target);

	void addPrecedent(Ref precedent);

	void clearDependent();

	/**
	 * @since 3.5.0
	 */
	enum RefType {
		CELL, AREA, SHEET, BOOK, NAME, OBJECT, INDIRECT, TABLE,
	}
}

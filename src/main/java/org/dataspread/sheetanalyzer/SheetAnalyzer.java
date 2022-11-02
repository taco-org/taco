package org.dataspread.sheetanalyzer;

import org.dataspread.sheetanalyzer.analyzer.SheetAnalyzerImpl;
import org.dataspread.sheetanalyzer.dependency.DependencyGraph;
import org.dataspread.sheetanalyzer.dependency.util.RefWithMeta;
import org.dataspread.sheetanalyzer.util.Pair;
import org.dataspread.sheetanalyzer.util.Ref;
import org.dataspread.sheetanalyzer.util.SheetNotSupportedException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class SheetAnalyzer {
    /**
     * Creating a SheetAnalyzer from a Spreadsheet File
     *
     * @param filePath
     * @return
     * @throws SheetNotSupportedException
     */
    public static SheetAnalyzer createSheetAnalyzer(String filePath) throws SheetNotSupportedException {
        return new SheetAnalyzerImpl(filePath);
    }

    /**
     * @return fileName
     */
    public abstract String getFileName();

    /**
     * @return a set of sheetnames
     */
    public abstract Set<String> getSheetNames();

    /**
     * Get the number of sheets in the spreadsheet file
     *
     * @return
     */
    public abstract int getNumSheets();

    /**
     * @return a map between sheetnames and dependencyGraph
     */
    public abstract Map<String, DependencyGraph> getDependencyGraphs();

    /**
     * Get the dependents of a reference {@link Ref}
     *
     * @param sheetName
     * @param ref
     * @return
     */
    public abstract Map<Ref, List<RefWithMeta>> getDependents(String sheetName, Ref ref);

    /**
     * Get the precedents of a reference {@link Ref}
     *
     * @param sheetName
     * @param ref
     * @return
     */
    public abstract Map<Ref, List<RefWithMeta>> getPrecedents(String sheetName, Ref ref);

    /**
     * Get the full information of a TACO graph
     *
     * @return
     */
    public abstract Map<String, Map<Ref, List<RefWithMeta>>> getTACODepGraphs();


    /**
     * Get the distribution of references a formula has
     *
     * @return
     */
    public abstract Map<Integer, Integer> getRefDistribution();

    /**
     * Get the number of compressed edges of TACO
     *
     * @return
     */
    public abstract long getNumCompEdges();

    /**
     * Get the number of compressed vertices of TACO
     *
     * @return
     */
    public abstract long getNumCompVertices();

    /**
     * Get the number of edges without compression
     *
     * @return
     */
    public abstract long getNumEdges();

    /**
     * Get the number of vertices without compression
     *
     * @return
     */
    public abstract long getNumVertices();

    /**
     * Get the number of formulae
     *
     * @return
     */
    public abstract long getNumOfFormulae();

    /**
     * Get the {@link Ref} that has the longest dependency chain
     *
     * @return
     */
    public abstract Pair<Ref, Long> getRefWithLongestDepChain();

    /**
     * Get the length of the longest path of a reference {@link Ref}
     *
     * @param startRef
     * @return
     */
    public abstract long getLongestPathLength(String sheetName, Ref startRef);

    /**
     * Get the {@link Ref} that has the most dependents
     *
     * @return
     */
    public abstract Pair<Ref, Long> getRefWithMostDeps() throws SheetNotSupportedException;

    /**
     * Get the graph build time
     *
     * @return
     */
    public abstract long getGraphBuildTimeCost();

}

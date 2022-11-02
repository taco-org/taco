package org.dataspread.sheetanalyzer.dependency;

import org.dataspread.sheetanalyzer.dependency.util.RefWithMeta;
import org.dataspread.sheetanalyzer.util.Pair;
import org.dataspread.sheetanalyzer.util.Ref;

import java.util.List;
import java.util.Map;

public interface DependencyGraph {
 Map<Ref, List<RefWithMeta>> getDependents(Ref precedent, boolean isDirect);
 Map<Ref, List<RefWithMeta>> getPrecedents(Ref dependent, boolean isDirect);
 void add(Ref precedent, Ref dependent);
 void clearDependents(Ref dependent);
 void addBatch(List<Pair<Ref, Ref>> edgeBatch);
 long getNumEdges();
 long getNumVertices();
}

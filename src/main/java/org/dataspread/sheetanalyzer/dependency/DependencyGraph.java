package org.dataspread.sheetanalyzer.dependency;

import org.dataspread.sheetanalyzer.util.Pair;
import org.dataspread.sheetanalyzer.util.Ref;

import java.util.List;
import java.util.Set;

public interface DependencyGraph {
 Set<Ref> getDependents(Ref precedent);
 Set<Ref> getPrecedents(Ref dependent);
 void add(Ref precedent, Ref dependent);
 void clearDependents(Ref dependent);
 void addBatch(List<Pair<Ref, Ref>> edgeBatch);
 long getNumEdges();
 long getNumVertices();
}

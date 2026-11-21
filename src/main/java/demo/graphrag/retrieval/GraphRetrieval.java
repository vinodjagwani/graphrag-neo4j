package demo.graphrag.retrieval;

import java.util.List;
import java.util.Set;

/**
 * What graph retrieval found for one question.
 *
 * @param seeds          entities named in the question
 * @param candidatePaths distinct paths found before ranking
 * @param selected       the best paths, highest score first
 * @param chunkIds       ids of the chunks that stated the facts on the selected paths
 */
public record GraphRetrieval(List<String> seeds, int candidatePaths, List<RankedPath> selected,
        Set<String> chunkIds) {

    public GraphRetrieval {
        seeds = List.copyOf(seeds);
        selected = List.copyOf(selected);
    }
}

package demo.graphrag.retrieval;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import demo.graphrag.config.DemoProperties;
import demo.graphrag.domain.GraphPath;
import demo.graphrag.graph.GraphStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Multi-hop retrieval in three steps, with no language model anywhere in it:
 * <ol>
 *   <li><b>Link</b>: find the entities named in the question.</li>
 *   <li><b>Walk</b>: from each of them, collect every simple path of up to {@code graphHops} relationships.</li>
 *   <li><b>Rank</b>: render each path as a sentence, embed it, keep the {@code graphTopPaths} closest to the
 *       question. The chunks that stated the relationships on those paths are the context.</li>
 * </ol>
 */
@Component
public class GraphRetriever {

    private static final Logger log = LoggerFactory.getLogger(GraphRetriever.class);

    private static final int PATHS_PER_SEED = 1000;

    private final GraphStore graphStore;
    private final PathRanker pathRanker;
    private final DemoProperties properties;

    public GraphRetriever(final GraphStore graphStore, final PathRanker pathRanker, final DemoProperties properties) {
        this.graphStore = graphStore;
        this.pathRanker = pathRanker;
        this.properties = properties;
    }

    /**
     * Finds the evidence for a question by following relationships in Neo4j: link the entities it names, walk
     * outward through the graph, keep the paths closest in meaning to the question, and return the chunks that
     * stated those facts. This is the part plain vector search cannot do, because the facts on a path need not
     * sound like the question.
     */
    public GraphRetrieval retrieve(final String question) {
        final List<String> seeds = graphStore.entitiesMentionedIn(question);
        final List<GraphPath> candidates = collectPaths(seeds);
        final List<RankedPath> selected = selectTopPaths(question, candidates);
        log.debug("Graph retrieval: seeds={}, candidate paths={}, kept={}", seeds, candidates.size(), selected.size());
        return toRetrieval(seeds, candidates, selected);
    }

    // A path found from two seeds in opposite directions has the same edges, so it is kept once.
    private List<GraphPath> collectPaths(final List<String> seeds) {
        return seeds.stream()
                .flatMap(this::pathsFrom)
                .collect(Collectors.toMap(GraphPath::edgeSentences, Function.identity(), (first, _) -> first,
                        LinkedHashMap::new))
                .values().stream()
                .toList();
    }

    private Stream<GraphPath> pathsFrom(final String seed) {
        return graphStore.paths(seed, properties.graphHops(), PATHS_PER_SEED).stream();
    }

    private List<RankedPath> selectTopPaths(final String question, final List<GraphPath> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        return pathRanker.rank(question, candidates).stream().limit(properties.graphTopPaths()).toList();
    }

    private GraphRetrieval toRetrieval(final List<String> seeds, final List<GraphPath> candidates,
            final List<RankedPath> selected) {
        return new GraphRetrieval(seeds, candidates.size(), selected, chunkIdsOf(selected));
    }

    private Set<String> chunkIdsOf(final List<RankedPath> selected) {
        return selected.stream()
                .flatMap(ranked -> ranked.path().sourceIds().stream())
                .collect(Collectors.toCollection(TreeSet::new));
    }
}

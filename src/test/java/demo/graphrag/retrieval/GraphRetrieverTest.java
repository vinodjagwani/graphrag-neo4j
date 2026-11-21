package demo.graphrag.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import demo.graphrag.config.DemoProperties;
import demo.graphrag.domain.Edge;
import demo.graphrag.domain.GraphPath;
import demo.graphrag.domain.RelationType;
import demo.graphrag.graph.GraphStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GraphRetrieverTest {

    private static final String QUESTION = "Who leads the team that owns Payment Gateway?";

    @Mock
    private GraphStore graphStore;

    @Mock
    private PathRanker pathRanker;

    private GraphRetriever graphRetriever;

    @BeforeEach
    void setUp() {
        graphRetriever = new GraphRetriever(graphStore, pathRanker, new DemoProperties(3, 3, 1));
    }

    @Test
    @DisplayName("keeps only the best-ranked paths and returns the chunks that stated their facts")
    void testRetrieveKeepsTopPathsAndCollectsSources() {
        final GraphPath best = aPath("Payments Team", RelationType.LED_BY, "Alice Chen", "c03");
        final GraphPath worse = aPath("Payment Gateway", RelationType.OWNED_BY, "Payments Team", "c02");
        givenEntitiesInQuestion("Payment Gateway");
        givenPathsFrom("Payment Gateway", best, worse);
        when(pathRanker.rank(QUESTION, List.of(best, worse)))
                .thenReturn(List.of(new RankedPath(best, 0.9), new RankedPath(worse, 0.5)));

        final GraphRetrieval retrieval = graphRetriever.retrieve(QUESTION);

        assertThat(retrieval.candidatePaths()).isEqualTo(2);
        assertThat(retrieval.selected()).extracting(RankedPath::path).containsExactly(best);
        assertThat(retrieval.chunkIds()).containsExactly("c03");
    }

    @Test
    @DisplayName("counts a path once when two entities in the question reach it from opposite ends")
    void testRetrieveDeduplicatesPathsFoundFromBothEnds() {
        final GraphPath fromGateway = aPath("Payment Gateway", RelationType.OWNED_BY, "Payments Team", "c02");
        final GraphPath fromTeam = aPath("Payment Gateway", RelationType.OWNED_BY, "Payments Team", "c02");
        givenEntitiesInQuestion("Payment Gateway", "Payments Team");
        givenPathsFrom("Payment Gateway", fromGateway);
        givenPathsFrom("Payments Team", fromTeam);
        when(pathRanker.rank(QUESTION, List.of(fromGateway))).thenReturn(List.of(new RankedPath(fromGateway, 0.8)));

        final GraphRetrieval retrieval = graphRetriever.retrieve(QUESTION);

        assertThat(retrieval.candidatePaths()).isEqualTo(1);
    }

    @Test
    @DisplayName("returns nothing and skips ranking when the question names no known entity")
    void testRetrieveWithoutEntities() {
        givenEntitiesInQuestion();

        final GraphRetrieval retrieval = graphRetriever.retrieve(QUESTION);

        assertThat(retrieval.seeds()).isEmpty();
        assertThat(retrieval.selected()).isEmpty();
        assertThat(retrieval.chunkIds()).isEmpty();
        verifyNoInteractions(pathRanker);
    }

    private void givenEntitiesInQuestion(final String... names) {
        when(graphStore.entitiesMentionedIn(QUESTION)).thenReturn(List.of(names));
    }

    private void givenPathsFrom(final String seed, final GraphPath... paths) {
        when(graphStore.paths(eq(seed), anyInt(), anyInt())).thenReturn(List.of(paths));
    }

    private GraphPath aPath(final String from, final RelationType type, final String to, final String chunkId) {
        return new GraphPath(List.of(new Edge(from, type, to, List.of(chunkId))));
    }
}

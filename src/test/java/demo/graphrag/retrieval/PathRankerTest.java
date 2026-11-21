package demo.graphrag.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.when;

import java.util.List;

import demo.graphrag.domain.Edge;
import demo.graphrag.domain.GraphPath;
import demo.graphrag.domain.RelationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;

@ExtendWith(MockitoExtension.class)
class PathRankerTest {

    private static final String QUESTION = "who leads the team?";

    @Mock
    private EmbeddingModel embeddingModel;

    @InjectMocks
    private PathRanker pathRanker;

    @Test
    @DisplayName("puts the path whose embedding is closest to the question first")
    void testRankOrdersByCosineSimilarity() {
        final GraphPath unrelated = aPath("Fraud Engine", RelationType.PROVIDED_BY, "Veridian Labs");
        final GraphPath related = aPath("Payments Team", RelationType.LED_BY, "Alice Chen");
        givenEmbeddings(new float[] {1, 0}, List.of(unrelated, related), new float[] {0, 1}, new float[] {1, 0});

        final List<RankedPath> ranked = pathRanker.rank(QUESTION, List.of(unrelated, related));

        assertThat(ranked).extracting(RankedPath::path).containsExactly(related, unrelated);
        assertThat(ranked.getFirst().score()).isCloseTo(1.0, within(1e-9));
        assertThat(ranked.getLast().score()).isCloseTo(0.0, within(1e-9));
    }

    @Test
    @DisplayName("scores a path by the cosine of the angle between its vector and the question's")
    void testRankScoresAngleBetweenVectors() {
        final GraphPath path = aPath("Payments Team", RelationType.LED_BY, "Alice Chen");
        givenEmbeddings(new float[] {1, 0}, List.of(path), new float[] {1, 1});

        final List<RankedPath> ranked = pathRanker.rank(QUESTION, List.of(path));

        assertThat(ranked).singleElement()
                .satisfies(only -> assertThat(only.score()).isCloseTo(Math.sqrt(0.5), within(1e-6)));
    }

    private void givenEmbeddings(final float[] questionVector, final List<GraphPath> paths,
            final float[]... pathVectors) {
        when(embeddingModel.embed(QUESTION)).thenReturn(questionVector);
        when(embeddingModel.embed(paths.stream().map(GraphPath::text).toList())).thenReturn(List.of(pathVectors));
    }

    private GraphPath aPath(final String from, final RelationType type, final String to) {
        return new GraphPath(List.of(new Edge(from, type, to, List.of("c01"))));
    }
}

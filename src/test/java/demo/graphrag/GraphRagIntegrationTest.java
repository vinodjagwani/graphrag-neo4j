package demo.graphrag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import demo.graphrag.corpus.CorpusLoader;
import demo.graphrag.domain.GraphPath;
import demo.graphrag.graph.GraphStore;
import demo.graphrag.indexing.Indexer;
import demo.graphrag.retrieval.GraphRetriever;
import demo.graphrag.runner.Question;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.neo4j.Neo4jContainer;

/** Needs Docker: starts a real Neo4j, loads the corpus, and checks what GraphRAG retrieves. */
@SpringBootTest(properties = "demo.run=false")
@Testcontainers
class GraphRagIntegrationTest {

    @Container
    @ServiceConnection
    static final Neo4jContainer NEO4J = new Neo4jContainer("neo4j:2026.10.2-community");

    @Autowired
    private CorpusLoader corpusLoader;

    @Autowired
    private Indexer indexer;

    @Autowired
    private GraphRetriever graphRetriever;

    @Autowired
    private GraphStore graphStore;

    @Test
    @DisplayName("graph holds one typed relationship per stated fact, with the chunk that stated it")
    void testGraphStoresFactsWithSources() {
        indexer.index(corpusLoader.load());

        final List<GraphPath> paths = graphStore.paths("Payments Team", 1, 10);

        assertThat(paths).extracting(GraphPath::text)
                .containsExactlyInAnyOrder(
                        "Payment Gateway is owned by Payments Team",
                        "Payments Team is led by Alice Chen");
        assertThat(paths).flatExtracting(GraphPath::sourceIds)
                .containsExactlyInAnyOrder("c02", "c03");
    }

    @Test
    @DisplayName("multi-hop retrieval reaches every supporting chunk, walking forward and backward")
    void testGraphRetrievalFindsEvidenceAcrossHops() {
        indexer.index(corpusLoader.load());

        assertThat(Question.demoQuestions()).allSatisfy(question ->
                assertThat(graphRetriever.retrieve(question.text()).chunkIds())
                        .as(question.text())
                        .containsAll(question.evidence()));
    }
}

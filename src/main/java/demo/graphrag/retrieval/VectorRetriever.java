package demo.graphrag.retrieval;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import demo.graphrag.config.DemoProperties;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

/** Plain vector RAG, the baseline: the {@code vectorTopK} chunks closest to the question. */
@Component
public class VectorRetriever {

    private final VectorStore vectorStore;
    private final DemoProperties properties;

    public VectorRetriever(final VectorStore vectorStore, final DemoProperties properties) {
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    /**
     * Similarity search on the Neo4j vector index (Spring AI's {@code Neo4jVectorStore}): embeds the question and
     * returns the nearest chunks. It sees text only, not relationships, so it is the baseline GraphRAG is
     * compared against. Ids are returned in id order.
     */
    public Set<String> retrieve(final String question) {
        return vectorStore.similaritySearch(toRequest(question)).stream()
                .map(Document::getId)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private SearchRequest toRequest(final String question) {
        return SearchRequest.builder().query(question).topK(properties.vectorTopK()).build();
    }
}

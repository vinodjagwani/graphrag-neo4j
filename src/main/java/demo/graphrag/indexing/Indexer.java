package demo.graphrag.indexing;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import demo.graphrag.domain.Chunk;
import demo.graphrag.domain.Triple;
import demo.graphrag.extraction.RuleBasedExtractor;
import demo.graphrag.graph.GraphStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

/**
 * The whole indexing side: chunks go into the Neo4j vector index (all plain vector RAG needs), then facts are
 * extracted from each chunk and written as relationships (what GraphRAG adds on top).
 */
@Service
public class Indexer {

    private static final Logger log = LoggerFactory.getLogger(Indexer.class);

    private final VectorStore vectorStore;
    private final RuleBasedExtractor extractor;
    private final GraphStore graphStore;

    public Indexer(final VectorStore vectorStore, final RuleBasedExtractor extractor, final GraphStore graphStore) {
        this.vectorStore = vectorStore;
        this.extractor = extractor;
        this.graphStore = graphStore;
    }

    /**
     * Loads the corpus into Neo4j twice over. First as embedded {@code Chunk} nodes for vector search. Then, per
     * chunk, extracts "A relation B" facts and writes them as {@code Entity} nodes joined by typed relationships
     * that point back to their source chunk. The graph is rebuilt from scratch on every call.
     */
    public void index(final List<Chunk> chunks) {
        addToVectorStore(chunks);
        final Map<String, List<Triple>> triplesByChunk = extractTriples(chunks);
        rebuildGraph(triplesByChunk);
        log.info("Indexed {} chunks; {} of them stated a relation", chunks.size(), triplesByChunk.size());
    }

    private void addToVectorStore(final List<Chunk> chunks) {
        vectorStore.add(chunks.stream().map(this::toDocument).toList());
    }

    private Document toDocument(final Chunk chunk) {
        return new Document(chunk.id(), chunk.text(), Map.of());
    }

    private Map<String, List<Triple>> extractTriples(final List<Chunk> chunks) {
        return chunks.stream()
                .map(this::extractFrom)
                .filter(entry -> !entry.getValue().isEmpty())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, _) -> first,
                        LinkedHashMap::new));
    }

    private Map.Entry<String, List<Triple>> extractFrom(final Chunk chunk) {
        return Map.entry(chunk.id(), extractor.extract(chunk.text()));
    }

    private void rebuildGraph(final Map<String, List<Triple>> triplesByChunk) {
        graphStore.initSchema();
        graphStore.clearEntities();
        graphStore.write(triplesByChunk);
    }
}

package demo.graphrag.graph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import demo.graphrag.domain.Edge;
import demo.graphrag.domain.GraphPath;
import demo.graphrag.domain.RelationType;
import demo.graphrag.domain.Triple;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Value;
import org.springframework.stereotype.Component;

/**
 * Every Cypher statement. Entities are {@code (:Entity {name})}; each fact is a real typed relationship such as
 * {@code (:Entity)-[:OWNED_BY]->(:Entity)} carrying a {@code sources} list with the ids of the chunks that stated it.
 * Chunks are {@code (:Chunk {id, text, embedding})} nodes written by Spring AI's Neo4jVectorStore.
 */
@Component
public class GraphStore {

    private static final String ALL_TYPES = Arrays.stream(RelationType.values())
            .map(Enum::name)
            .collect(Collectors.joining("|"));

    // LINK: which known entities does the question mention? Case-insensitive "name appears in the question" match;
    // longest names first so "Payment Gateway" wins over a shorter name inside it.
    private static final String MENTIONED_ENTITIES_CYPHER = """
            MATCH (e:Entity)
            WHERE toLower($question) CONTAINS toLower(e.name)
            RETURN e.name AS name
            ORDER BY size(e.name) DESC
            """;

    // WALK: the multi-hop relationship search. Reads as: start at the seed entity, follow 1..N relationships of any
    // allowed type in either direction (no arrow on the pattern), and never visit the same node twice (the WHERE
    // keeps paths simple, so the walk cannot loop back). Each hop is returned in its true direction together with
    // the chunk ids that stated it. %s is the allow-listed type union, %d the hop limit.
    private static final String PATHS_CYPHER = """
            MATCH p = (s:Entity {name: $name})-[:%s*1..%d]-(t:Entity)
            WHERE all(n IN nodes(p) WHERE single(m IN nodes(p) WHERE m = n))
            RETURN [r IN relationships(p) | {from: startNode(r).name, type: type(r), to: endNode(r).name,
                                             sources: r.sources}] AS edges
            LIMIT $limit
            """;

    // WRITE: store one batch of facts of a single relationship type. MERGE creates each entity and each
    // relationship only if it does not exist yet, so re-running the load never duplicates anything. The sources
    // list remembers which chunks stated the fact, which is what lets an answer cite its evidence.
    // The relationship type is concatenated into the text because Cypher cannot parameterize it; RelationType
    // is the allow-list that makes that safe.
    private static final String WRITE_RELATIONS_CYPHER = """
            UNWIND $rows AS row
            MERGE (a:Entity {name: row.from})
            MERGE (b:Entity {name: row.to})
            MERGE (a)-[r:%s]->(b)
            SET r.sources = CASE WHEN row.chunk IN coalesce(r.sources, []) THEN r.sources
                                 ELSE coalesce(r.sources, []) + row.chunk END
            """;

    private final Driver driver;

    public GraphStore(final Driver driver) {
        this.driver = driver;
    }

    /** Creates a uniqueness constraint on {@code Entity.name}, so one real-world thing is always one node. */
    public void initSchema() {
        run("CREATE CONSTRAINT entity_name IF NOT EXISTS FOR (e:Entity) REQUIRE e.name IS UNIQUE", Map.of());
    }

    /** Deletes every entity and its relationships so the graph is rebuilt from scratch. Chunk nodes are kept. */
    public void clearEntities() {
        run("MATCH (e:Entity) DETACH DELETE e", Map.of());
    }

    /**
     * Stores extracted facts in Neo4j. "Checkout Service depends on Payment Gateway" becomes two {@code Entity}
     * nodes joined by a {@code DEPENDS_ON} relationship that records the chunk it came from. One batched
     * statement per relationship type.
     */
    public void write(final Map<String, List<Triple>> triplesByChunk) {
        groupByType(triplesByChunk).forEach(this::writeRelations);
    }

    /**
     * Step 1, link: finds the graph nodes a question is about, for example "Payment Gateway" in "Who owns Payment
     * Gateway?". These become the starting points of the walk. Longest name first.
     */
    public List<String> entitiesMentionedIn(final String question) {
        final List<Record> rows = query(MENTIONED_ENTITIES_CYPHER, Map.of("question", question));
        return rows.stream().map(this::nameOf).toList();
    }

    /**
     * Step 2, walk: finds relationships around an entity. Returns every simple path of 1..maxHops relationships
     * that starts at the seed and may follow relationships forwards or backwards, so "who owns X" and "what
     * depends on X" use the same query. Example from Checkout Service with 3 hops:
     * {@code Checkout Service -> Payment Gateway -> Payments Team -> Alice Chen}. Each edge is reported in its
     * true direction.
     *
     * @param seed    entity name to start from
     * @param maxHops longest path to return, counted in relationships
     * @param limit   cap on returned paths, so a highly connected node cannot flood the caller
     */
    public List<GraphPath> paths(final String seed, final int maxHops, final int limit) {
        final List<Record> rows = query(PATHS_CYPHER.formatted(ALL_TYPES, maxHops), pathParameters(seed, limit));
        return rows.stream().map(this::toGraphPath).toList();
    }

    private Map<RelationType, List<Map<String, Object>>> groupByType(final Map<String, List<Triple>> triplesByChunk) {
        final Map<RelationType, List<Map<String, Object>>> rowsByType = new EnumMap<>(RelationType.class);
        triplesByChunk.forEach((chunkId, triples) -> triples.forEach(triple -> rowsByType
                .computeIfAbsent(triple.type(), _ -> new ArrayList<>())
                .add(toRow(chunkId, triple))));
        return rowsByType;
    }

    private Map<String, Object> toRow(final String chunkId, final Triple triple) {
        return Map.of("chunk", chunkId, "from", triple.source(), "to", triple.target());
    }

    private void writeRelations(final RelationType type, final List<Map<String, Object>> rows) {
        run(WRITE_RELATIONS_CYPHER.formatted(type.name()), Map.of("rows", rows));
    }

    private Map<String, Object> pathParameters(final String seed, final int limit) {
        return Map.of("name", seed, "limit", limit);
    }

    private String nameOf(final Record row) {
        return row.get("name").asString();
    }

    private GraphPath toGraphPath(final Record row) {
        return new GraphPath(row.get("edges").asList(this::toEdge));
    }

    private Edge toEdge(final Value edge) {
        return new Edge(edge.get("from").asString(), RelationType.valueOf(edge.get("type").asString()),
                edge.get("to").asString(), edge.get("sources").asList(Value::asString));
    }

    private List<Record> query(final String cypher, final Map<String, Object> parameters) {
        return driver.executableQuery(cypher).withParameters(parameters).execute().records();
    }

    private void run(final String cypher, final Map<String, Object> parameters) {
        driver.executableQuery(cypher).withParameters(parameters).execute();
    }
}

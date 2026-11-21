# graphrag-neo4j

A small Spring Boot demo that puts **plain vector RAG** next to **GraphRAG** on Neo4j, with **no language model and
no API key**.

Some questions need several facts that live in different passages, for example *"Who leads the team that owns the
service Checkout Service depends on?"* Vector search finds passages that sound like the question and can miss the
ones that only complete the chain. GraphRAG stores the facts as nodes and relationships in Neo4j and follows the
links. This project runs both on the same data so you can compare what each one retrieves.

## What is real and what is faked

| Part | Status |
|---|---|
| Neo4j graph, Cypher queries, vector index | real |
| Embeddings (MiniLM, in-process ONNX, no network after the first model download) | real |
| Link, walk and rank retrieval | real |
| Fact extraction | **faked**: `RuleBasedExtractor` uses one regex per relationship. A real system asks a language model here (`ChatClient...entity(...)` in Spring AI). |
| Answer generation | **not run**: the demo prints the prompt it would send to a model |

The corpus is 18 short invented chunks and there are 4 hand-written questions. The output shows how the mechanism
works, not how well it performs on real data. No language model is used, so nothing here says how well a model would
extract facts or write answers.

## How it works

```
corpus.txt ─┬─> Neo4jVectorStore ──> (:Chunk {id, text, embedding})        plain vector RAG searches this
            └─> RuleBasedExtractor ─> (:Entity)-[:DEPENDS_ON|OWNED_BY|LED_BY|PROVIDED_BY {sources:[chunk ids]}]->(:Entity)

question ─> 1 link   entities whose name appears in the question
         ─> 2 walk   Cypher variable-length paths, up to 3 hops, either direction
         ─> 3 rank   embed each path as a sentence, keep the 3 closest to the question
         ─> context = the chunks listed in `sources` on the kept paths
```

For each question the demo prints the chunks each approach retrieved, marks them `needed` or `noise`, and says
whether the context holds every supporting chunk. At the end it prints the grounded prompt GraphRAG would send to a
model for the first question.

## Prerequisites

| Tool | Version | Check |
|---|---|---|
| Java | 25 | `java -version` |
| Maven | 3.9+ | `mvn -v` |
| Docker (engine running) | recent Docker Desktop | `docker info` |

The first run downloads the Neo4j image and the MiniLM model, so allow a few minutes and a network connection. Ports
7474 and 7687 must be free.

## Run it

1. Start Docker Desktop and wait until it reports the engine is running.
2. From the project folder:

   ```powershell
   mvn spring-boot:run
   ```

   Spring Boot starts Neo4j from `compose.yaml`, waits for its health check, indexes the corpus, then prints the
   comparison for each question and exits.

3. Run the tests:

   ```powershell
   mvn test
   ```

   The unit tests need nothing else. `GraphRagIntegrationTest` starts its own Neo4j through Testcontainers, so it
   needs Docker too.

To look at the graph, keep Neo4j running (for example `docker compose up -d`), open http://localhost:7474 and sign
in with user `neo4j`, password `demo-password`, then run `MATCH p=()-[]->() RETURN p`. By default Spring Boot's
Docker Compose support stops the container when the app exits; `spring.docker.compose.lifecycle-management=start-only`
leaves it running. Stop it with `docker compose down`.

## Configuration

`src/main/resources/application.yml`:

| Property | Default | Meaning |
|---|---|---|
| `demo.vector-top-k` | 3 | chunks plain vector RAG puts in the context |
| `demo.graph-hops` | 3 | longest path walked from an entity |
| `demo.graph-top-paths` | 3 | paths kept after ranking against the question |
| `demo.run` | true | set `false` to start the app without running the demo (the integration test does) |

The Neo4j password lives in `compose.yaml`. It is a throwaway demo value; do not reuse it anywhere real.

## Project layout

Everything lives under `demo.graphrag`.

| Package | Holds |
|---|---|
| `config` | `DemoProperties` (`demo.*` settings) |
| `domain` | Plain data: `Chunk`, `Triple`, `Edge`, `GraphPath`, `RelationType` |
| `corpus` | `CorpusLoader` reads `corpus.txt` |
| `extraction` | `RuleBasedExtractor`, the stand-in for a model |
| `graph` | `GraphStore`, every Cypher statement |
| `indexing` | `Indexer` fills the vector index and the graph |
| `retrieval` | `VectorRetriever` (baseline), `GraphRetriever` (link, walk, rank), `PathRanker` |
| `runner` | `DemoRunner`, `ConsoleReport`, `PromptBuilder`, `Question` |

Other files: `src/main/resources/corpus.txt` (the data), `compose.yaml` (Neo4j).

## Limits

- Entities are matched by exact name, so "the payments group" will not link to `Payments Team`. Real systems add
  entity resolution (alias table or embedding similarity) before writing the graph.
- Relationship types are a fixed list (`RelationType`); the extractor only understands one sentence shape per type.
- Path ranking is an embedding of the path sentence, not a trained ranker.
- The graph is rebuilt from scratch on every run.
- Swapping in a model means replacing `RuleBasedExtractor.extract` and handing `GraphRetrieval` to a `ChatClient`.

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| `failed to connect to the docker API` or `dockerDesktopLinuxEngine` not found | Docker Desktop is not running; start it and wait for the engine |
| `port is already allocated` | something else uses 7474 or 7687; stop it or change the ports in `compose.yaml` |
| Slow first start | image and model downloads; later runs are faster |
| Connection refused to Neo4j right after start | Neo4j is still starting; the health check normally covers this |

## Versions

Spring Boot 4.1.2, Spring AI 2.0.2 (documented as supporting Boot 4.0.x and 4.1.x), Neo4j `2026.10.2-community`,
Testcontainers 2.0.5 (managed by Boot), Java 25.

package demo.graphrag.retrieval;

import demo.graphrag.domain.GraphPath;

/** A candidate path with its cosine similarity to the question. */
public record RankedPath(GraphPath path, double score) {
}

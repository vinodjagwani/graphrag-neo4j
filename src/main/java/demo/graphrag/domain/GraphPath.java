package demo.graphrag.domain;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** A walk through the graph from a seed entity; edges are in walking order. */
public record GraphPath(List<Edge> edges) {

    public GraphPath {
        edges = List.copyOf(edges);
    }

    public String text() {
        return edges.stream().map(Edge::sentence).collect(Collectors.joining("; "));
    }

    /** The path's facts without order, so the same walk found from either end compares equal. */
    public Set<String> edgeSentences() {
        return edges.stream().map(Edge::sentence).collect(Collectors.toSet());
    }

    /** Ids of every chunk that stated one of the path's facts. */
    public Set<String> sourceIds() {
        return edges.stream().flatMap(edge -> edge.sources().stream()).collect(Collectors.toSet());
    }
}

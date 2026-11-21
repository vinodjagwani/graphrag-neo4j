package demo.graphrag.domain;

import java.util.List;

/** One stated fact in its true direction, with the ids of the chunks that stated it. */
public record Edge(String from, RelationType type, String to, List<String> sources) {

    public Edge {
        sources = List.copyOf(sources);
    }

    public String sentence() {
        return from + " " + type.phrase() + " " + to;
    }
}

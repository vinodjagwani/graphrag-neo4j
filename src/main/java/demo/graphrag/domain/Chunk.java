package demo.graphrag.domain;

/** One short passage of the corpus; the unit that is embedded and cited as evidence. */
public record Chunk(String id, String text) {
}

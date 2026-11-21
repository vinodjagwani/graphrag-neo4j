package demo.graphrag.domain;

/** A fact extracted from text: {@code source --type--> target}. */
public record Triple(String source, RelationType type, String target) {
}

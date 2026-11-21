package demo.graphrag.domain;

/**
 * The closed vocabulary of relationships. The constant name becomes the Neo4j relationship type, so this enum is
 * also the allow-list that makes it safe to put the type into Cypher text (Cypher cannot parameterize it).
 */
public enum RelationType {

    DEPENDS_ON("depends on"),
    OWNED_BY("is owned by"),
    LED_BY("is led by"),
    PROVIDED_BY("is provided by");

    private final String phrase;

    RelationType(final String phrase) {
        this.phrase = phrase;
    }

    public String phrase() {
        return phrase;
    }
}

package demo.graphrag.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import demo.graphrag.corpus.CorpusLoader;
import demo.graphrag.domain.Chunk;
import demo.graphrag.domain.RelationType;
import demo.graphrag.domain.Triple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RuleBasedExtractorTest {

    private final RuleBasedExtractor extractor = new RuleBasedExtractor();

    @Test
    @DisplayName("extracts one triple for each relation phrase")
    void testExtractEachRelationType() {
        final List<String> sentences = List.of(
                "Checkout Service depends on Payment Gateway.",
                "Payment Gateway is owned by Payments Team.",
                "Payments Team is led by Alice Chen.",
                "Fraud Engine is provided by Veridian Labs.");

        final List<Triple> triples = sentences.stream().map(this::extractOnly).toList();

        assertThat(triples).containsExactly(
                aTriple("Checkout Service", RelationType.DEPENDS_ON, "Payment Gateway"),
                aTriple("Payment Gateway", RelationType.OWNED_BY, "Payments Team"),
                aTriple("Payments Team", RelationType.LED_BY, "Alice Chen"),
                aTriple("Fraud Engine", RelationType.PROVIDED_BY, "Veridian Labs"));
    }

    @Test
    @DisplayName("ignores sentences that state no relation")
    void testExtractIgnoresDistractors() {
        final String distractor = "The Checkout Service dashboard shows latency for every region.";

        final List<Triple> triples = extractor.extract(distractor);

        assertThat(triples).isEmpty();
    }

    @Test
    @DisplayName("every corpus chunk is either a fact chunk with one triple or a distractor with none")
    void testCorpusSplitsIntoFactsAndDistractors() {
        final List<Chunk> chunks = new CorpusLoader().load();

        assertThat(chunks).filteredOn(chunk -> chunk.id().startsWith("c"))
                .allSatisfy(chunk -> assertThat(extractor.extract(chunk.text())).hasSize(1));
        assertThat(chunks).filteredOn(chunk -> chunk.id().startsWith("d"))
                .allSatisfy(chunk -> assertThat(extractor.extract(chunk.text())).isEmpty());
    }

    private Triple extractOnly(final String sentence) {
        final List<Triple> triples = extractor.extract(sentence);
        assertThat(triples).hasSize(1);
        return triples.getFirst();
    }

    private Triple aTriple(final String source, final RelationType type, final String target) {
        return new Triple(source, type, target);
    }
}

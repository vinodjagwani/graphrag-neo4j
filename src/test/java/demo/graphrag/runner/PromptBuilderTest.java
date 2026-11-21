package demo.graphrag.runner;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import demo.graphrag.domain.Edge;
import demo.graphrag.domain.GraphPath;
import demo.graphrag.domain.RelationType;
import demo.graphrag.retrieval.GraphRetrieval;
import demo.graphrag.retrieval.RankedPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PromptBuilderTest {

    private final PromptBuilder promptBuilder = new PromptBuilder();

    @Test
    @DisplayName("lists the kept paths, the cited passages and the question, in that order")
    void testBuildGroundsQuestionInRetrievedEvidence() {
        final Question question = aQuestion("Who leads the team that owns Payment Gateway?");
        final GraphRetrieval retrieval = aRetrieval("Payments Team", "Alice Chen", "c03");
        final Map<String, String> textById = Map.of("c03", "Payments Team is led by Alice Chen.");

        final String prompt = promptBuilder.build(question, retrieval, textById);

        assertThat(prompt)
                .startsWith("Answer using only the facts below.")
                .contains("- Payments Team is led by Alice Chen")
                .contains("[c03] Payments Team is led by Alice Chen.")
                .endsWith("Question: Who leads the team that owns Payment Gateway?");
        assertThat(prompt.indexOf("Relationship paths:")).isLessThan(prompt.indexOf("Source passages:"));
    }

    private Question aQuestion(final String text) {
        return new Question(text, Set.of("c03"));
    }

    private GraphRetrieval aRetrieval(final String from, final String to, final String chunkId) {
        final GraphPath path = new GraphPath(List.of(new Edge(from, RelationType.LED_BY, to, List.of(chunkId))));
        return new GraphRetrieval(List.of(from), 1, List.of(new RankedPath(path, 0.9)), Set.of(chunkId));
    }
}

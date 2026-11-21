package demo.graphrag.runner;

import java.util.Map;
import java.util.stream.Collectors;

import demo.graphrag.retrieval.GraphRetrieval;
import org.springframework.stereotype.Component;

/** Builds the grounded prompt that GraphRAG would send to a chat model. The demo only prints it. */
@Component
public class PromptBuilder {

    public String build(final Question question, final GraphRetrieval retrieval, final Map<String, String> textById) {
        return newPrompt(relationshipPaths(retrieval), sourcePassages(retrieval, textById), question.text());
    }

    private String relationshipPaths(final GraphRetrieval retrieval) {
        return retrieval.selected().stream()
                .map(ranked -> "- " + ranked.path().text())
                .collect(Collectors.joining("\n"));
    }

    private String sourcePassages(final GraphRetrieval retrieval, final Map<String, String> textById) {
        return retrieval.chunkIds().stream()
                .map(id -> "[" + id + "] " + textById.get(id))
                .collect(Collectors.joining("\n"));
    }

    private String newPrompt(final String paths, final String passages, final String question) {
        return """
                Answer using only the facts below. If they are not enough, say you do not know.

                Relationship paths:
                %s

                Source passages:
                %s

                Question: %s""".formatted(paths, passages, question);
    }
}

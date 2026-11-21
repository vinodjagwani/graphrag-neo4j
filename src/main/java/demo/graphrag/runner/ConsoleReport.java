package demo.graphrag.runner;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import demo.graphrag.config.DemoProperties;
import demo.graphrag.retrieval.GraphRetrieval;
import org.springframework.stereotype.Component;

/**
 * The demo's console output. It is the program's result, not diagnostics, so it goes to standard output and not to
 * the logger.
 */
@Component
public class ConsoleReport {

    private final DemoProperties properties;

    public ConsoleReport(final DemoProperties properties) {
        this.properties = properties;
    }

    public void printQuestion(final Question question) {
        System.out.printf("%n=== %s%n    evidence needed: %s%n", question.text(), new TreeSet<>(question.evidence()));
    }

    public void printVectorResult(final Question question, final Set<String> chunkIds,
            final Map<String, String> textById) {
        System.out.printf("%n  Vector RAG (top %d chunks by similarity)%n", properties.vectorTopK());
        printChunks(chunkIds, question, textById);
        printVerdict(chunkIds, question);
    }

    public void printGraphResult(final Question question, final GraphRetrieval retrieval,
            final Map<String, String> textById) {
        printGraphHeader(retrieval);
        printPaths(retrieval);
        printChunks(retrieval.chunkIds(), question, textById);
        printVerdict(retrieval.chunkIds(), question);
    }

    public void printPrompt(final String prompt) {
        System.out.printf("%n=== Prompt GraphRAG would send to a model for the first question%n%s%n", prompt);
    }

    private void printGraphHeader(final GraphRetrieval retrieval) {
        System.out.printf("%n  GraphRAG (link, walk up to %d hops, rank paths)%n", properties.graphHops());
        System.out.printf("    entities linked: %s | candidate paths: %d | kept: %d%n",
                retrieval.seeds(), retrieval.candidatePaths(), retrieval.selected().size());
    }

    private void printPaths(final GraphRetrieval retrieval) {
        retrieval.selected()
                .forEach(ranked -> System.out.printf("    path %.2f  %s%n", ranked.score(), ranked.path().text()));
    }

    private void printChunks(final Set<String> chunkIds, final Question question, final Map<String, String> textById) {
        chunkIds.forEach(id -> System.out.printf("    [%s] %s  %s%n", label(id, question), id, textById.get(id)));
    }

    private String label(final String chunkId, final Question question) {
        return question.evidence().contains(chunkId) ? "needed" : "noise ";
    }

    private void printVerdict(final Set<String> chunkIds, final Question question) {
        final Set<String> missing = missingEvidence(chunkIds, question);
        System.out.printf("    complete evidence: %s (%d chunks in context)%n", verdictText(missing), chunkIds.size());
    }

    private Set<String> missingEvidence(final Set<String> chunkIds, final Question question) {
        final Set<String> missing = new TreeSet<>(question.evidence());
        missing.removeAll(chunkIds);
        return missing;
    }

    private String verdictText(final Set<String> missing) {
        return missing.isEmpty() ? "YES" : "NO, missing " + missing;
    }
}

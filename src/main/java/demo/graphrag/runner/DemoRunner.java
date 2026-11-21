package demo.graphrag.runner;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import demo.graphrag.corpus.CorpusLoader;
import demo.graphrag.domain.Chunk;
import demo.graphrag.indexing.Indexer;
import demo.graphrag.retrieval.GraphRetriever;
import demo.graphrag.retrieval.VectorRetriever;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Indexes the corpus, then answers each demo question twice: with plain vector RAG and with GraphRAG. It prints
 * the context each one would hand to a model and whether that context holds every supporting chunk. No model is
 * called, so "complete" means the evidence is present, not that an answer was generated.
 */
@Component
@ConditionalOnProperty(name = "demo.run", havingValue = "true", matchIfMissing = true)
public class DemoRunner implements ApplicationRunner {

    private final CorpusLoader corpusLoader;
    private final Indexer indexer;
    private final VectorRetriever vectorRetriever;
    private final GraphRetriever graphRetriever;
    private final PromptBuilder promptBuilder;
    private final ConsoleReport report;

    public DemoRunner(final CorpusLoader corpusLoader, final Indexer indexer, final VectorRetriever vectorRetriever,
            final GraphRetriever graphRetriever, final PromptBuilder promptBuilder, final ConsoleReport report) {
        this.corpusLoader = corpusLoader;
        this.indexer = indexer;
        this.vectorRetriever = vectorRetriever;
        this.graphRetriever = graphRetriever;
        this.promptBuilder = promptBuilder;
        this.report = report;
    }

    @Override
    public void run(final ApplicationArguments args) {
        final List<Chunk> chunks = corpusLoader.load();
        indexer.index(chunks);
        final Map<String, String> textById = textById(chunks);
        final List<Question> questions = Question.demoQuestions();
        questions.forEach(question -> compare(question, textById));
        printSamplePrompt(questions.getFirst(), textById);
    }

    private Map<String, String> textById(final List<Chunk> chunks) {
        return chunks.stream().collect(Collectors.toMap(Chunk::id, Chunk::text));
    }

    private void compare(final Question question, final Map<String, String> textById) {
        report.printQuestion(question);
        report.printVectorResult(question, vectorRetriever.retrieve(question.text()), textById);
        report.printGraphResult(question, graphRetriever.retrieve(question.text()), textById);
    }

    private void printSamplePrompt(final Question question, final Map<String, String> textById) {
        report.printPrompt(promptBuilder.build(question, graphRetriever.retrieve(question.text()), textById));
    }
}

package demo.graphrag.retrieval;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

import demo.graphrag.domain.GraphPath;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

/** Orders candidate paths by how close their sentence embedding is to the question embedding. */
@Component
public class PathRanker {

    private final EmbeddingModel embeddingModel;

    public PathRanker(final EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /**
     * Step 3, rank: a walk can return many paths, most of them irrelevant. Each path is written as a sentence
     * ("Payments Team is led by Alice Chen"), embedded with the same model as the chunks, and scored by cosine
     * similarity to the question. Returns all candidates, best first.
     */
    public List<RankedPath> rank(final String question, final List<GraphPath> candidates) {
        final float[] questionVector = embeddingModel.embed(question);
        final List<float[]> pathVectors = embeddingModel.embed(pathTexts(candidates));
        return IntStream.range(0, candidates.size())
                .mapToObj(i -> toRankedPath(candidates.get(i), cosine(questionVector, pathVectors.get(i))))
                .sorted(Comparator.comparingDouble(RankedPath::score).reversed())
                .toList();
    }

    private List<String> pathTexts(final List<GraphPath> candidates) {
        return candidates.stream().map(GraphPath::text).toList();
    }

    private RankedPath toRankedPath(final GraphPath path, final double score) {
        return new RankedPath(path, score);
    }

    private double cosine(final float[] a, final float[] b) {
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}

package demo.graphrag.corpus;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import demo.graphrag.domain.Chunk;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Reads {@code corpus.txt}: one {@code id|text} chunk per line, {@code #} starts a comment. */
@Component
public class CorpusLoader {

    private static final String RESOURCE = "corpus.txt";

    public List<Chunk> load() {
        final String content = readResource();
        return Arrays.stream(content.split("\\R"))
                .filter(this::isChunkLine)
                .map(this::toChunk)
                .toList();
    }

    private String readResource() {
        try {
            return new ClassPathResource(RESOURCE).getContentAsString(StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new UncheckedIOException("Cannot read " + RESOURCE, e);
        }
    }

    private boolean isChunkLine(final String line) {
        return !line.isBlank() && !line.startsWith("#");
    }

    private Chunk toChunk(final String line) {
        final int separator = line.indexOf('|');
        return new Chunk(line.substring(0, separator).strip(), line.substring(separator + 1).strip());
    }
}

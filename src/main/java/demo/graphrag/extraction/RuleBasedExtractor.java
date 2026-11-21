package demo.graphrag.extraction;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import demo.graphrag.domain.RelationType;
import demo.graphrag.domain.Triple;
import org.springframework.stereotype.Component;

/**
 * Stand-in for the model that turns text into graph facts. A real GraphRAG system calls a language model here
 * (in Spring AI: {@code ChatClient.prompt()...call().entity(...)}); this class recognises one sentence shape per
 * {@link RelationType}, "A is owned by B.", so the demo runs with no provider and no API key. Everything
 * downstream of it is real.
 */
@Component
public class RuleBasedExtractor {

    private static final Map<RelationType, Pattern> PATTERNS = buildPatterns();

    public List<Triple> extract(final String text) {
        final String sentence = text.strip();
        return Arrays.stream(RelationType.values())
                .map(type -> match(type, sentence))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<Triple> match(final RelationType type, final String sentence) {
        final Matcher matcher = PATTERNS.get(type).matcher(sentence);
        return matcher.matches() ? Optional.of(toTriple(matcher, type)) : Optional.empty();
    }

    private Triple toTriple(final Matcher matcher, final RelationType type) {
        return new Triple(matcher.group(1), type, matcher.group(2));
    }

    private static Map<RelationType, Pattern> buildPatterns() {
        return Arrays.stream(RelationType.values())
                .collect(Collectors.toUnmodifiableMap(Function.identity(), RuleBasedExtractor::toPattern));
    }

    private static Pattern toPattern(final RelationType type) {
        return Pattern.compile("(.+?) " + Pattern.quote(type.phrase()) + " (.+?)\\.");
    }
}

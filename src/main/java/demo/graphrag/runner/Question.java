package demo.graphrag.runner;

import java.util.List;
import java.util.Set;

/** A demo question and the chunks that together hold the facts needed to answer it. */
public record Question(String text, Set<String> evidence) {

    public Question {
        evidence = Set.copyOf(evidence);
    }

    public static List<Question> demoQuestions() {
        return List.of(
                new Question("Who leads the team that owns the service Checkout Service depends on?",
                        Set.of("c01", "c02", "c03")),
                new Question("Which vendor provides the fraud detection that Checkout Service relies on?",
                        Set.of("c01", "c04", "c05")),
                new Question("Who leads the team that owns Ledger Service?",
                        Set.of("c07", "c08")),
                new Question("Which services are affected if Fraud Engine fails?",
                        Set.of("c04", "c01")));
    }
}

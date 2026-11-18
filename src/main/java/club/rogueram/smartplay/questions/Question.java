package club.rogueram.smartplay.questions;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class Question {
    private final String text;
    private final List<String> answers;

    public Question(String text, List<String> answers) {
        this.text = text;
        this.answers = Collections.unmodifiableList(new ArrayList<>(answers));
    }

    public String getText() { return text; }
    public String getAnswer() { return answers.get(0); }

    public boolean accepts(String input, boolean ignoreAccents) {
        String normalized = normalize(input, ignoreAccents);
        return !normalized.isEmpty() && answers.stream()
                .anyMatch(answer -> normalize(answer, ignoreAccents).equals(normalized));
    }

    private static String normalize(String input, boolean ignoreAccents) {
        String value = Normalizer.normalize(input == null ? "" : input, Normalizer.Form.NFC)
                .replace('\u00a0', ' ').trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        if (ignoreAccents) {
            value = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        }
        return value;
    }
}

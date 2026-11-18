package club.rogueram.smartplay.questions;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;

class QuestionTest {
    @Test void requiresTheFullAnswer() {
        Question question = new Question("Gaz ?", Collections.singletonList("dioxyde de carbone"));
        assertTrue(question.accepts("  DIOXYDE  de carbone  ", false));
        assertFalse(question.accepts("de", false));
        assertFalse(question.accepts("carbone", false));
        assertFalse(question.accepts("du dioxyde de carbone ou de l'oxygene", false));
    }
    @Test void supportsAliasesAndOptionalAccents() {
        Question question = new Question("?", Arrays.asList("éléphant", "pachyderme"));
        assertTrue(question.accepts("ELEPHANT", true));
        assertFalse(question.accepts("ELEPHANT", false));
        assertTrue(question.accepts("pachyderme", false));
        assertTrue(question.accepts("e\u0301le\u0301phant", false));
    }
    @Test void rejectsEmptyAnswers() {
        Question question = new Question("?", Collections.singletonList("Paris"));
        assertFalse(question.accepts(null, true));
        assertFalse(question.accepts(" \t\n ", true));
    }
    @Test void normalizesNonBreakingSpaces() {
        Question question = new Question("?", Collections.singletonList("New York"));
        assertTrue(question.accepts("New\u00a0York", false));
    }
}

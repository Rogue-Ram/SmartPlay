package club.rogueram.smartplay.questions;

import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class QuestionBankTest {
    private List<Question> load(String json) { return QuestionBank.load(new StringReader(json)); }
    @Test void supportsLegacyFormatAndAlternativeAnswers() {
        List<Question> questions = load("[{\"question\":\" Q? \",\"answer\":\" A \",\"answers\":[\"B\"]}]");
        assertEquals("Q?", questions.get(0).getText());
        assertEquals("A", questions.get(0).getAnswer());
        assertTrue(questions.get(0).accepts("b", false));
    }
    @Test void supportsAnswersWithoutLegacyAnswer() {
        assertEquals("A", load("[{\"question\":\"Q\",\"answers\":[\"A\"]}]").get(0).getAnswer());
    }
    @Test void rejectsInvalidBanks() {
        for (String json : new String[]{"null", "{}", "[]", "[null]", "[1]", "[{\"question\":\"Q\"}]",
                "[{\"question\":\"\",\"answer\":\"A\"}]", "[{\"question\":\"Q\",\"answer\":4}]",
                "[{\"question\":\"Q\",\"answers\":\"A\"}]", "[{\"question\":\"Q\",\"answers\":[null]}]",
                "[{\"question\":\"Q\",\"answers\":[]}]"}) {
            assertThrows(IllegalArgumentException.class, () -> load(json), json);
        }
    }
    @Test void rejectsDuplicatesAndMalformedJson() {
        assertThrows(IllegalArgumentException.class, () -> load("[{\"question\":\"Q\",\"answer\":\"A\"},{\"question\":\"Q\",\"answer\":\"B\"}]"));
        assertThrows(RuntimeException.class, () -> load("[{"));
    }
    @Test void bankCannotBeChangedAfterLoading() {
        assertThrows(UnsupportedOperationException.class, () -> load("[{\"question\":\"Q\",\"answer\":\"A\"}]").clear());
    }
    @Test void shippedExamplesAreValid() throws Exception {
        try (InputStreamReader reader = new InputStreamReader(
                getClass().getResourceAsStream("/questions.json"), StandardCharsets.UTF_8)) {
            assertEquals(3, QuestionBank.load(reader).size());
        }
    }
}

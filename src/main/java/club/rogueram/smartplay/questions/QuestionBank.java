package club.rogueram.smartplay.questions;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Parses into a new bank so a failed reload cannot corrupt the current questions. */
public final class QuestionBank {
    private QuestionBank() { }

    public static List<Question> load(Reader reader) {
        JsonElement root = JsonParser.parseReader(reader);
        if (!root.isJsonArray() || root.getAsJsonArray().size() == 0) {
            throw new IllegalArgumentException("questions.json doit contenir une liste non vide.");
        }
        List<Question> questions = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int index = 0;
        for (JsonElement entry : root.getAsJsonArray()) {
            index++;
            if (!entry.isJsonObject()) throw invalid(index, "objet attendu");
            JsonObject object = entry.getAsJsonObject();
            String text = string(object.get("question"), index);
            if (!seen.add(text)) throw invalid(index, "question en double");
            List<String> answers = new ArrayList<>();
            if (object.has("answer")) answers.add(string(object.get("answer"), index));
            if (object.has("answers")) {
                if (!object.get("answers").isJsonArray()) throw invalid(index, "answers doit etre une liste");
                JsonArray aliases = object.getAsJsonArray("answers");
                for (JsonElement alias : aliases) answers.add(string(alias, index));
            }
            if (answers.isEmpty()) throw invalid(index, "reponse manquante");
            questions.add(new Question(text, answers));
        }
        return Collections.unmodifiableList(questions);
    }

    private static String string(JsonElement value, int index) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || value.getAsString().trim().isEmpty()) throw invalid(index, "texte vide ou invalide");
        return value.getAsString().trim();
    }

    private static IllegalArgumentException invalid(int index, String reason) {
        return new IllegalArgumentException("Question " + index + " : " + reason);
    }
}

package com.itilms.liveclass.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.liveclass.dto.request.AskQuestionRequest;
import com.itilms.liveclass.dto.request.AskQuestionRequest.CodeTestCase;
import com.itilms.liveclass.dto.request.DataTable;
import com.itilms.liveclass.dto.request.MaterialRequest;
import com.itilms.liveclass.entity.QuestionType;

/**
 * The all-in-one upload: a single JSON file holding any number of questions, each with its options, answer key, code,
 * test cases and tables. JSON is what an AI assistant writes most reliably, so the format is deliberately forgiving
 * (a bare list or {"questions": [...]}, true/"True", a letter or the option text as the right answer, numbers where
 * text is expected) and every problem is reported against the question it belongs to.
 */
public final class MaterialJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MaterialJson() {
    }

    /** The example file trainers start from; also what the AI prompt shows. */
    public static String example() {
        return resource("question-bank-example.json");
    }

    /** Text a trainer pastes into an AI assistant, with the example inside it. */
    public static String aiPrompt() {
        return resource("question-bank-prompt.txt").replace("{{EXAMPLE}}", example().strip());
    }

    private static String resource(String name) {
        try (java.io.InputStream in = MaterialJson.class.getResourceAsStream("/" + name)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + name);
            }
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** True when the file's first real character opens a JSON object or list. */
    public static boolean looksLikeJson(byte[] file) {
        int i = 0;
        // A byte-order mark (EF BB BF) and whitespace may precede the first brace.
        if (file.length >= 3 && (file[0] & 0xFF) == 0xEF && (file[1] & 0xFF) == 0xBB && (file[2] & 0xFF) == 0xBF) {
            i = 3;
        }
        while (i < file.length && Character.isWhitespace(file[i])) {
            i++;
        }
        return i < file.length && (file[i] == '{' || file[i] == '[');
    }

    public static MaterialSheet.Result parse(byte[] file) {
        JsonNode root;
        try {
            root = MAPPER.readTree(file);
        } catch (IOException ex) {
            String where = ex instanceof JsonProcessingException jp && jp.getLocation() != null
                    ? " (line " + jp.getLocation().getLineNr() + ")" : "";
            String reason = ex instanceof JsonProcessingException jp ? jp.getOriginalMessage() : ex.getMessage();
            throw new BusinessRuleException("The file is not valid JSON" + where + ": " + reason
                    + ". Ask the assistant to return only the JSON, with no extra text around it.");
        }
        JsonNode list = root.isArray() ? root : root.path("questions");
        if (!list.isArray() || list.isEmpty()) {
            throw new BusinessRuleException("Expected a list of questions: either [...] or {\"questions\": [...]}.");
        }
        List<MaterialRequest> items = new ArrayList<>();
        List<MaterialSheet.RowError> errors = new ArrayList<>();
        int number = 0;
        for (JsonNode node : list) {
            number++;
            if (items.size() + errors.size() >= MaterialSheet.MAX_ROWS) {
                errors.add(new MaterialSheet.RowError(0, "Only the first " + MaterialSheet.MAX_ROWS
                        + " questions are read; upload the rest separately."));
                break;
            }
            try {
                items.add(toItem(node));
            } catch (IllegalArgumentException ex) {
                // Row 0 means "not a spreadsheet row": the message already names the question.
                errors.add(new MaterialSheet.RowError(0, "Question " + number + ": " + ex.getMessage()));
            }
        }
        return new MaterialSheet.Result(items, errors);
    }

    private static MaterialRequest toItem(JsonNode n) {
        if (!n.isObject()) {
            throw new IllegalArgumentException("each question must be an object { ... }.");
        }
        String prompt = text(n, "question", "prompt", "text");
        if (prompt.isEmpty()) {
            throw new IllegalArgumentException("the \"question\" text is empty.");
        }
        QuestionType type = MaterialSheet.typeOf(text(n, "type"));
        JsonNode correct = first(n, "correct", "answer", "answers");

        List<String> options = null;
        List<Integer> correctOptions = null;
        List<String> accepted = null;

        if (type == QuestionType.MCQ || type == QuestionType.MULTIPLE_SELECT) {
            options = strings(first(n, "options", "choices"));
            if (options.size() < 2 || options.size() > MaterialSheet.MAX_OPTIONS) {
                throw new IllegalArgumentException("give between 2 and " + MaterialSheet.MAX_OPTIONS + " options.");
            }
            correctOptions = indexes(correct, options);
            if (type == QuestionType.MCQ && correctOptions.size() != 1) {
                throw new IllegalArgumentException(
                        "a single-choice question needs exactly one correct option; use MULTIPLE_SELECT for more.");
            }
        } else if (type == QuestionType.TRUE_FALSE) {
            String k = correct == null ? "" : correct.asText().trim().toLowerCase(Locale.ROOT);
            if (!k.equals("true") && !k.equals("false")) {
                throw new IllegalArgumentException("for TRUE_FALSE, \"correct\" must be true or false.");
            }
            correctOptions = List.of(k.equals("true") ? 0 : 1);
        } else if (type == QuestionType.SHORT_ANSWER && correct != null && !correct.isNull()) {
            accepted = strings(correct);
            if (accepted.isEmpty()) {
                accepted = null;
            }
        }

        Integer marks = null;
        JsonNode marksNode = n.get("marks");
        if (marksNode != null && !marksNode.isNull()) {
            if (marksNode.isNumber()) {
                marks = marksNode.asInt();
            } else if (marksNode.asText().trim().matches("\\d+")) {
                marks = Integer.parseInt(marksNode.asText().trim());
            } else {
                throw new IllegalArgumentException("\"marks\" must be a number.");
            }
            if (marks < 1 || marks > 100) {
                throw new IllegalArgumentException("\"marks\" must be between 1 and 100.");
            }
        }

        String difficulty = text(n, "difficulty").toUpperCase(Locale.ROOT);
        if (!difficulty.isEmpty() && !List.of("EASY", "MEDIUM", "HARD").contains(difficulty)) {
            throw new IllegalArgumentException("\"difficulty\" must be EASY, MEDIUM or HARD.");
        }

        String language = null;
        String starter = null;
        String setupSql = null;
        Boolean anyLanguage = null;
        List<CodeTestCase> cases = null;
        JsonNode caseNodes = first(n, "testCases", "test_cases", "tests");
        boolean hasCases = caseNodes != null && caseNodes.isArray() && !caseNodes.isEmpty();
        if (type == QuestionType.CODING) {
            language = text(n, "language").toLowerCase(Locale.ROOT);
            language = language.isEmpty() ? "python" : language;
            if (!MaterialSheet.LANGUAGES.contains(language)) {
                throw new IllegalArgumentException("language \"" + language + "\" is not available. Use java, python, c, cpp, csharp or sql.");
            }
            starter = blankToNull(text(n, "starterCode", "starter_code", "starter"));
            setupSql = blankToNull(text(n, "setupSql", "setup_sql", "setup"));
            if (setupSql != null && !language.equals("sql")) {
                throw new IllegalArgumentException("\"setupSql\" is only for the sql language.");
            }
            JsonNode any = first(n, "allowAnyLanguage", "allowLanguageChoice");
            anyLanguage = !language.equals("sql") ? (any == null || any.isNull() || any.asBoolean(true)) : null;
            cases = hasCases ? testCases(caseNodes) : null;
        } else if (hasCases) {
            throw new IllegalArgumentException("test cases are only for CODING questions.");
        }

        List<DataTable> tables = tables(first(n, "tables", "dataset", "database"));
        try {
            DatasetRules.validate(tables);
        } catch (BusinessRuleException ex) {
            throw new IllegalArgumentException(ex.getMessage());
        }

        String code = blankToNull(text(n, "code", "snippet"));
        AskQuestionRequest question = new AskQuestionRequest(type, prompt, options, correctOptions, accepted, language,
                starter, blankToNull(text(n, "explanation")), marks, code, code == null ? null : "text",
                tables.isEmpty() ? null : tables, cases, setupSql, anyLanguage);

        String title = text(n, "title");
        if (title.isEmpty()) {
            title = prompt.length() > 60 ? prompt.substring(0, 60) : prompt;
        }
        String folder;
        try {
            folder = LiveMaterialService.normalizeFolder(text(n, "folder"));
        } catch (BusinessRuleException ex) {
            throw new IllegalArgumentException(ex.getMessage());
        }
        JsonNode shared = first(n, "share", "shared");
        return new MaterialRequest(title.length() > 160 ? title.substring(0, 160) : title, question, null,
                blankToNull(text(n, "topic")), tags(n.get("tags")), difficulty.isEmpty() ? null : difficulty,
                shared != null && (shared.isBoolean() ? shared.asBoolean() : List.of("yes", "true", "y")
                        .contains(shared.asText().trim().toLowerCase(Locale.ROOT))),
                folder);
    }

    private static List<CodeTestCase> testCases(JsonNode list) {
        if (list.size() > 10) {
            throw new IllegalArgumentException("a question takes at most 10 test cases.");
        }
        List<CodeTestCase> cases = new ArrayList<>();
        int i = 0;
        for (JsonNode c : list) {
            i++;
            String expected = text(c, "expectedOutput", "expected_output", "output", "expected");
            if (expected.isEmpty()) {
                throw new IllegalArgumentException("test case " + i + " has no expectedOutput.");
            }
            int weight = 1;
            JsonNode w = c.get("weight");
            if (w != null && !w.isNull()) {
                weight = w.asInt(0);
                if (weight < 1 || weight > 100) {
                    throw new IllegalArgumentException("test case " + i + ": weight must be between 1 and 100.");
                }
            }
            JsonNode hidden = c.get("hidden");
            cases.add(new CodeTestCase(text(c, "input", "stdin"), expected, hidden != null && hidden.asBoolean(false), weight));
        }
        return cases;
    }

    private static List<DataTable> tables(JsonNode list) {
        List<DataTable> tables = new ArrayList<>();
        if (list == null || list.isNull()) {
            return tables;
        }
        if (!list.isArray()) {
            throw new IllegalArgumentException("\"tables\" must be a list.");
        }
        for (JsonNode t : list) {
            List<String> columns = strings(t.get("columns"));
            List<List<String>> rows = new ArrayList<>();
            JsonNode rowNodes = t.get("rows");
            if (rowNodes != null && rowNodes.isArray()) {
                for (JsonNode row : rowNodes) {
                    List<String> cells = new ArrayList<>();
                    // A row may be a list in column order, or an object keyed by column name.
                    if (row.isObject()) {
                        for (String column : columns) {
                            cells.add(row.has(column) ? cell(row.get(column)) : "");
                        }
                    } else {
                        row.forEach(cell -> cells.add(cell(cell)));
                    }
                    rows.add(cells);
                }
            }
            tables.add(new DataTable(text(t, "name"), columns, rows));
        }
        return tables;
    }

    /** Resolves the right option(s): a letter (B), a position (2), or the option's own text; one value or a list. */
    private static List<Integer> indexes(JsonNode correct, List<String> options) {
        if (correct == null || correct.isNull()) {
            throw new IllegalArgumentException("say which option is correct in \"correct\" (e.g. \"B\").");
        }
        List<String> tokens = correct.isArray() ? strings(correct) : List.of(correct.asText().split("[,;]"));
        List<Integer> picked = new ArrayList<>();
        for (String raw : tokens) {
            String token = raw.trim();
            if (token.isEmpty()) {
                continue;
            }
            int index = -1;
            for (int i = 0; i < options.size(); i++) {
                if (options.get(i).equalsIgnoreCase(token)) {
                    index = i;
                    break;
                }
            }
            if (index < 0 && token.length() == 1 && Character.isLetter(token.charAt(0))) {
                index = Character.toUpperCase(token.charAt(0)) - 'A';
            } else if (index < 0 && token.matches("\\d+")) {
                index = Integer.parseInt(token) - 1;
            }
            if (index < 0 || index >= options.size()) {
                throw new IllegalArgumentException("correct answer \"" + token + "\" is not one of the options.");
            }
            if (!picked.contains(index)) {
                picked.add(index);
            }
        }
        if (picked.isEmpty()) {
            throw new IllegalArgumentException("say which option is correct in \"correct\" (e.g. \"B\").");
        }
        return picked.stream().sorted().toList();
    }

    private static JsonNode first(JsonNode n, String... names) {
        for (String name : names) {
            JsonNode v = n.get(name);
            if (v != null && !v.isNull()) {
                return v;
            }
        }
        return null;
    }

    private static String text(JsonNode n, String... names) {
        JsonNode v = first(n, names);
        return v == null ? "" : v.asText().trim();
    }

    /** A list of strings; a single string is a list of one, a comma-separated "a, b" tag string is split. */
    private static List<String> strings(JsonNode n) {
        List<String> out = new ArrayList<>();
        if (n == null || n.isNull()) {
            return out;
        }
        if (n.isArray()) {
            n.forEach(v -> {
                String s = cell(v);
                if (!s.isBlank()) {
                    out.add(s.trim());
                }
            });
        } else if (!n.asText().isBlank()) {
            out.add(n.asText().trim());
        }
        return out;
    }

    /** Tags as a list, or as one "loops, python" string. */
    private static List<String> tags(JsonNode n) {
        return strings(n).stream().flatMap(t -> java.util.Arrays.stream(t.split(","))).map(String::trim)
                .filter(t -> !t.isEmpty()).toList();
    }

    private static String cell(JsonNode v) {
        return v == null || v.isNull() ? "" : v.asText();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}

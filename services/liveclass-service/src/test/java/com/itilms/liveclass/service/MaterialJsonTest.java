package com.itilms.liveclass.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.itilms.common.exception.BusinessRuleException;
import com.itilms.liveclass.entity.QuestionType;

class MaterialJsonTest {

    private static MaterialSheet.Result parse(String json) {
        return MaterialJson.parse(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void theExampleFileUploadsCleanlyWithEverythingInIt() {
        MaterialSheet.Result result = MaterialJson.parse(MaterialJson.example().getBytes(StandardCharsets.UTF_8));

        assertThat(result.errors()).isEmpty();
        assertThat(result.items()).hasSize(7);

        var mcq = result.items().get(0);
        assertThat(mcq.question().correctOptions()).containsExactly(2);
        assertThat(mcq.question().snippet()).contains("range(3)");
        assertThat(mcq.folder()).isEqualTo("Python/Loops/MCQ");
        assertThat(mcq.tags()).containsExactly("python", "loops");

        assertThat(result.items().get(1).question().correctOptions()).containsExactly(0, 1, 3);
        assertThat(result.items().get(2).question().correctOptions()).containsExactly(0);
        assertThat(result.items().get(3).question().acceptedAnswers()).containsExactly("6", "six");

        var python = result.items().get(4);
        assertThat(python.question().testCases()).hasSize(2);
        assertThat(python.question().allowLanguageChoice()).isTrue();

        var sql = result.items().get(5);
        assertThat(sql.question().language()).isEqualTo("sql");
        assertThat(sql.question().dataset()).extracting(t -> t.name()).containsExactly("employees", "departments");
        // Numbers in the rows become the text a student sees.
        assertThat(sql.question().dataset().get(0).rows().get(0)).containsExactly("1", "Asha", "50000", "1");
        assertThat(sql.question().setupSql()).contains("CREATE TABLE employees");
        assertThat(sql.question().testCases().get(0).expectedOutput()).isEqualTo("Asha|Sales\nMeera|Sales");
        assertThat(sql.question().allowLanguageChoice()).isNull();

        assertThat(result.items().get(6).question().type()).isEqualTo(QuestionType.OTHER);
    }

    @Test
    void theAiPromptCarriesTheExampleAndNoLeftoverPlaceholder() {
        String prompt = MaterialJson.aiPrompt();

        assertThat(prompt).doesNotContain("{{EXAMPLE}}").contains("\"questions\"").contains("Employees above 45000");
    }

    @Test
    void aBareListAndForgivingAnswerSpellingsAreAccepted() {
        MaterialSheet.Result result = parse("""
                [ {"type":"mcq","question":"Pick the fruit","options":["Car","Apple"],"correct":"Apple"},
                  {"type":"MULTIPLE_SELECT","question":"Primes","options":["Two","Three","Four"],"correct":"1, 2"},
                  {"type":"TRUE_FALSE","question":"Sky is blue","correct":"True"},
                  {"type":"SHORT_ANSWER","question":"2+2","correct":"4","marks":"3","tags":"a, b"} ]""");

        assertThat(result.errors()).isEmpty();
        assertThat(result.items().get(0).question().correctOptions()).containsExactly(1);
        assertThat(result.items().get(1).question().correctOptions()).containsExactly(0, 1);
        assertThat(result.items().get(2).question().correctOptions()).containsExactly(0);
        assertThat(result.items().get(3).question().marks()).isEqualTo(3);
        assertThat(result.items().get(3).tags()).containsExactly("a", "b");
    }

    @Test
    void eachBadQuestionIsReportedByItsNumberAndTheGoodOnesStillLoad() {
        MaterialSheet.Result result = parse("""
                {"questions":[
                  {"type":"MCQ","question":"ok","options":["a","b"],"correct":"A"},
                  {"type":"MCQ","question":"two right","options":["a","b"],"correct":["A","B"]},
                  {"type":"MCQ","question":"no key","options":["a","b"]},
                  {"type":"CODING","question":"cobol?","language":"cobol"},
                  {"type":"MCQ","question":"stray tests","options":["a","b"],"correct":"A",
                   "testCases":[{"input":"","expectedOutput":"x"}]},
                  {"type":"CODING","question":"no output","testCases":[{"input":"1"}]},
                  {"type":"OTHER","question":""},
                  {"type":"OTHER","question":"ragged","tables":[{"name":"t","columns":["a","b"],"rows":[[1]]}]}
                ]}""");

        assertThat(result.items()).extracting(i -> i.question().prompt()).containsExactly("ok");
        assertThat(result.errors()).hasSize(7);
        assertThat(result.errors()).extracting(MaterialSheet.RowError::message)
                .anyMatch(m -> m.startsWith("Question 2:") && m.contains("exactly one"))
                .anyMatch(m -> m.startsWith("Question 3:") && m.contains("which option is correct"))
                .anyMatch(m -> m.startsWith("Question 4:") && m.contains("cobol"))
                .anyMatch(m -> m.startsWith("Question 5:") && m.contains("only for CODING"))
                .anyMatch(m -> m.startsWith("Question 6:") && m.contains("expectedOutput"))
                .anyMatch(m -> m.startsWith("Question 7:") && m.contains("empty"))
                .anyMatch(m -> m.startsWith("Question 8:") && m.contains("every row"));
    }

    @Test
    void textAroundTheJsonIsRefusedWithAHelpfulMessage() {
        assertThatThrownBy(() -> parse("{\"questions\": [ {\"type\": \"MCQ\", } ]}"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("not valid JSON");
        assertThatThrownBy(() -> parse("{\"nothing\": []}"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("list of questions");
        assertThat(MaterialJson.looksLikeJson("  {\"a\":1}".getBytes(StandardCharsets.UTF_8))).isTrue();
        assertThat(MaterialJson.looksLikeJson("Title,Type\n".getBytes(StandardCharsets.UTF_8))).isFalse();
    }
}

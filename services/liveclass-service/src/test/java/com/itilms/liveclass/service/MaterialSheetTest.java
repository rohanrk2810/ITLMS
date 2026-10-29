package com.itilms.liveclass.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.itilms.liveclass.entity.QuestionType;

class MaterialSheetTest {

    private static final String NL = "\n";

    @Test
    void theTemplateItselfUploadsCleanly() throws IOException {
        MaterialSheet.Result result = MaterialSheet.parse(new ByteArrayInputStream(MaterialSheet.template()));

        assertThat(result.errors()).isEmpty();
        assertThat(result.items()).hasSize(4);

        var mcq = result.items().get(0);
        assertThat(mcq.question().type()).isEqualTo(QuestionType.MCQ);
        assertThat(mcq.question().options()).containsExactly("0 1 2", "1 2 3", "0 2 4", "Error");
        assertThat(mcq.question().correctOptions()).containsExactly(2);
        assertThat(mcq.question().snippet()).contains("range(3)");
        assertThat(mcq.tags()).containsExactly("python", "loops");

        var sql = result.items().get(1);
        assertThat(sql.shared()).isTrue();
        assertThat(sql.question().dataset()).hasSize(1);
        assertThat(sql.question().dataset().get(0).columns()).containsExactly("id", "name", "salary");
        assertThat(sql.question().dataset().get(0).rows()).hasSize(3);

        assertThat(result.items().get(2).question().correctOptions()).containsExactly(0);

        var coding = result.items().get(3);
        assertThat(coding.question().type()).isEqualTo(QuestionType.CODING);
        assertThat(coding.question().language()).isEqualTo("python");
        assertThat(coding.question().starterCode()).contains("input().split()");
        assertThat(coding.question().allowLanguageChoice()).isTrue();
        assertThat(coding.question().testCases()).hasSize(2);
        assertThat(coding.question().testCases().get(1).hidden()).isTrue();
        assertThat(coding.question().testCases().get(1).weight()).isEqualTo(2);
        assertThat(coding.folder()).isEqualTo("Python/Basics/Coding");
        assertThat(mcq.folder()).isEqualTo("Python/Loops/MCQ");
    }

    @Test
    void badRowsAreSkippedWithTheirRowNumberAndGoodRowsStillLoad() throws IOException {
        byte[] file = sheet(
                new String[]{"", "MCQ", "Fine?", "yes", "no", "", "", "", "", "", "", "A"},
                new String[]{"", "MCQ", "Two right?", "a", "b", "", "", "", "", "", "", "A,B"},
                new String[]{"", "MCQ", "Letter out of range", "a", "b", "", "", "", "", "", "", "E"},
                new String[]{"", "ESSAY", "Unknown type"},
                new String[]{"", "TRUE_FALSE", "Maybe?", "", "", "", "", "", "", "", "", "perhaps"},
                new String[]{},
                new String[]{"", "MULTIPLE_SELECT", "Pick primes", "2", "3", "4", "", "", "", "", "", "A,B", "3"});

        MaterialSheet.Result result = MaterialSheet.parse(new ByteArrayInputStream(file));

        assertThat(result.items()).extracting(i -> i.question().prompt()).containsExactly("Fine?", "Pick primes");
        assertThat(result.items().get(1).question().marks()).isEqualTo(3);
        // Row 1 is the heading, so the first data row is row 2.
        assertThat(result.errors()).extracting(MaterialSheet.RowError::row).containsExactly(3, 4, 5, 6);
    }

    @Test
    void severalTablesAndCodingFieldsAreRead() throws IOException {
        String[] row = new String[27];
        java.util.Arrays.fill(row, "");
        row[1] = "SHORT_ANSWER"; row[2] = "Join them"; row[19] = "a"; row[20] = "id" + NL + "1"; row[23] = "b"; row[24] = "id,x" + NL + "1,2";
        String[] bad = new String[27];
        java.util.Arrays.fill(bad, "");
        bad[1] = "MCQ"; bad[2] = "q"; bad[3] = "x"; bad[4] = "y"; bad[11] = "A"; bad[21] = "python";

        MaterialSheet.Result result = MaterialSheet.parse(new ByteArrayInputStream(sheet(row, bad)));

        assertThat(result.items().get(0).question().dataset()).extracting(t -> t.name()).containsExactly("a", "b");
        assertThat(result.errors()).extracting(MaterialSheet.RowError::row).containsExactly(3);
    }

    @Test
    void testCasesAreCheckedAgainstTheirQuestion() throws IOException {
        String[] coding = blank(30);
        coding[1] = "CODING"; coding[2] = "Print hi"; coding[21] = "java"; coding[29] = " Java / OOPs ";
        String[] wrongLanguage = blank(30);
        wrongLanguage[1] = "CODING"; wrongLanguage[2] = "Print hi"; wrongLanguage[21] = "cobol";
        String[] mcq = blank(30);
        mcq[1] = "MCQ"; mcq[2] = "q"; mcq[3] = "x"; mcq[4] = "y"; mcq[11] = "A";

        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet questions = book.createSheet("Questions");
            questions.createRow(0).createCell(0).setCellValue("Title");
            String[][] rows = {coding, wrongLanguage, mcq};
            for (int r = 0; r < rows.length; r++) {
                Row row = questions.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) {
                    if (!rows[r][c].isEmpty()) {
                        row.createCell(c).setCellValue(rows[r][c]);
                    }
                }
            }
            Sheet cases = book.createSheet("Test cases");
            cases.createRow(0).createCell(0).setCellValue("Question row");
            String[][] caseRows = {{"2", "", "hi"}, {"4", "", "x"}, {"2", "", ""}, {"abc", "", "x"}};
            for (int r = 0; r < caseRows.length; r++) {
                Row row = cases.createRow(r + 1);
                for (int c = 0; c < caseRows[r].length; c++) {
                    row.createCell(c).setCellValue(caseRows[r][c]);
                }
            }
            book.write(out);

            MaterialSheet.Result result = MaterialSheet.parse(new ByteArrayInputStream(out.toByteArray()));

            assertThat(result.items()).extracting(i -> i.question().prompt()).containsExactly("Print hi");
            assertThat(result.items().get(0).question().testCases()).hasSize(1);
            assertThat(result.items().get(0).folder()).isEqualTo("Java/OOPs");
            // Row 3 has an unknown compiler; the MCQ on row 4 was handed test cases; two test rows are malformed.
            assertThat(result.errors()).extracting(MaterialSheet.RowError::row).contains(3, 4);
            assertThat(result.errors()).extracting(MaterialSheet.RowError::message)
                    .anyMatch(m -> m.contains("cobol"))
                    .anyMatch(m -> m.contains("Expected output is empty"))
                    .anyMatch(m -> m.contains("must be the row number"));
        }
    }

    private static String[] blank(int length) {
        String[] row = new String[length];
        java.util.Arrays.fill(row, "");
        return row;
    }

    @Test
    void shortAnswerKeysSplitOnABar() throws IOException {
        byte[] file = sheet(new String[]{"", "SHORT_ANSWER", "3 * 2?", "", "", "", "", "", "", "", "", "6|six"});

        var item = MaterialSheet.parse(new ByteArrayInputStream(file)).items().get(0);

        assertThat(item.question().acceptedAnswers()).containsExactly("6", "six");
    }

    private static byte[] sheet(String[]... rows) throws IOException {
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = book.createSheet("Questions");
            sheet.createRow(0).createCell(0).setCellValue("Title");
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) {
                    if (!rows[r][c].isEmpty()) {
                        row.createCell(c).setCellValue(rows[r][c]);
                    }
                }
            }
            book.write(out);
            return out.toByteArray();
        }
    }
}

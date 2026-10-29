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

    @Test
    void theTemplateItselfUploadsCleanly() throws IOException {
        MaterialSheet.Result result = MaterialSheet.parse(new ByteArrayInputStream(MaterialSheet.template()));

        assertThat(result.errors()).isEmpty();
        assertThat(result.items()).hasSize(3);

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

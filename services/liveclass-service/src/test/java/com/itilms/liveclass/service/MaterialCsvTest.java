package com.itilms.liveclass.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;

import com.itilms.common.exception.BusinessRuleException;
import com.itilms.liveclass.entity.QuestionType;
import com.itilms.liveclass.service.MaterialCsv.NamedFile;

class MaterialCsvTest {

    @Test
    void theCsvTemplatesUploadBackToTheSameQuestionsAsTheExcelOne() throws IOException {
        List<NamedFile> files = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(MaterialCsv.templateZip()))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                files.add(new NamedFile(entry.getName(), zip.readAllBytes()));
            }
        }
        assertThat(files).extracting(NamedFile::name)
                .containsExactly("questions.csv", "test-cases.csv", "tables.csv");

        MaterialSheet.Result result = MaterialSheet.parse(
                new ByteArrayInputStream(MaterialCsv.toWorkbook(files)));

        assertThat(result.errors()).isEmpty();
        assertThat(result.items()).hasSize(4);
        // A snippet with line breaks, a table in one cell, a second table and test cases all survive the CSV trip.
        assertThat(result.items().get(0).question().snippet()).contains("range(3)").contains("print(i * 2)");
        assertThat(result.items().get(1).question().dataset()).extracting(t -> t.name())
                .containsExactly("employees", "departments");
        var coding = result.items().get(3);
        assertThat(coding.question().type()).isEqualTo(QuestionType.CODING);
        assertThat(coding.question().testCases()).hasSize(2);
    }

    @Test
    void aSemicolonSeparatedFileWithABomAndQuotedLineBreaksIsRead() {
        String text = "﻿Title;Type;Question\r\nQ1;MCQ;\"line one\r\nline two; still the question\"\r\n";

        List<List<String>> records = MaterialCsv.parse(text);

        assertThat(records).hasSize(2);
        assertThat(records.get(0)).containsExactly("Title", "Type", "Question");
        assertThat(records.get(1).get(2)).isEqualTo("line one\r\nline two; still the question");
    }

    @Test
    void anUnknownFileOrMissingQuestionsFileIsRefusedWithAReason() {
        byte[] mystery = "a,b\n1,2\n".getBytes(StandardCharsets.UTF_8);
        byte[] testsOnly = "Question row,Input,Expected output\n2,1,1\n".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> MaterialCsv.toWorkbook(List.of(new NamedFile("x.csv", mystery))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Could not tell what \"x.csv\" is");
        assertThatThrownBy(() -> MaterialCsv.toWorkbook(List.of(new NamedFile("t.csv", testsOnly))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("questions CSV");
    }
}

package com.itilms.reporting.service.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** One table, three formats - each round-tripped back to confirm the bytes really hold what was asked for. */
class TabularExporterTest {

    private static final List<String> HEADERS = List.of("Time", "Action");
    private static final List<List<String>> ROWS = List.of(
            List.of("2026-09-18T10:00:00Z", "LOGIN"),
            List.of("has,comma \"and quote\"", "MULTI\nLINE"));

    @Test
    @DisplayName("CSV escapes commas, quotes and newlines, and keeps every row")
    void csvEscapesSpecialCharacters() {
        String csv = new String(TabularExporter.csv(HEADERS, ROWS), StandardCharsets.UTF_8);
        String[] lines = csv.split("\r\n");

        assertThat(lines).hasSize(3);
        assertThat(lines[0]).isEqualTo("Time,Action");
        assertThat(lines[2]).isEqualTo("\"has,comma \"\"and quote\"\"\",\"MULTI\nLINE\"");
    }

    @Test
    @DisplayName("Excel carries a header row plus one row per record, readable back with POI")
    void excelRoundTripsThroughPoi() throws Exception {
        byte[] bytes = TabularExporter.excel("Sheet 1", HEADERS, ROWS);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Time");
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("LOGIN");
            assertThat(sheet.getLastRowNum()).isEqualTo(ROWS.size());
        }
    }

    @Test
    @DisplayName("An empty table still produces a valid, readable workbook")
    void excelHandlesNoRows() throws Exception {
        byte[] bytes = TabularExporter.excel("Empty", HEADERS, List.of());
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getSheetAt(0).getLastRowNum()).isZero();
        }
    }

    @Test
    @DisplayName("PDF output is a real PDF document")
    void pdfProducesAPdfDocument() {
        byte[] bytes = TabularExporter.pdf("Audit log", HEADERS, ROWS);
        assertThat(bytes).isNotEmpty();
        assertThat(new String(bytes, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    private static final Letterhead LETTERHEAD = new Letterhead(
            "GH Test College", "Jalgaon", List.of("1 College Road, Jalgaon", "0257 1234567  |  office@college.test"));

    @Test
    @DisplayName("Excel with a letterhead puts the institute's name above the table, and the table is still intact")
    void excelCarriesTheLetterheadAboveTheTable() throws Exception {
        byte[] bytes = TabularExporter.excel("Audit log", LETTERHEAD, HEADERS, ROWS);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("GH Test College");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("Jalgaon");
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("1 College Road, Jalgaon");
            // name, tagline, two lines, a blank row, then the header
            assertThat(sheet.getRow(5).getCell(0).getStringCellValue()).isEqualTo("Time");
            assertThat(sheet.getRow(6).getCell(1).getStringCellValue()).isEqualTo("LOGIN");
            assertThat(sheet.getLastRowNum()).isEqualTo(5 + ROWS.size());
        }
    }

    @Test
    @DisplayName("Without a letterhead the Excel file is exactly as before")
    void excelWithoutALetterheadHasTheHeaderFirst() throws Exception {
        byte[] bytes = TabularExporter.excel("Audit log", Letterhead.NONE, HEADERS, ROWS);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).isEqualTo("Time");
        }
    }

    @Test
    @DisplayName("A PDF with a letterhead is a real PDF and is larger than one without")
    void pdfWithALetterheadIsAPdf() {
        byte[] with = TabularExporter.pdf("Audit log", LETTERHEAD, HEADERS, ROWS);
        byte[] without = TabularExporter.pdf("Audit log", Letterhead.NONE, HEADERS, ROWS);
        assertThat(new String(with, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        assertThat(with.length).isGreaterThan(without.length);
    }
}

package com.itilms.liveclass.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.itilms.common.exception.BusinessRuleException;

/**
 * The CSV way into the question bank. A CSV holds one table, so the three parts of the Excel template are three
 * files: the questions, their test cases and their big tables. Uploaded together they are stitched into the same
 * workbook the Excel import reads, so both routes share every rule and every error message.
 */
public final class MaterialCsv {

    private MaterialCsv() {
    }

    /** True for an .xlsx (a zip, which starts with "PK"); anything else is read as CSV text. */
    public static boolean isWorkbook(byte[] file) {
        return file.length > 3 && file[0] == 'P' && file[1] == 'K';
    }

    /** The uploaded CSV files as one workbook: Questions, and Test cases / Tables when those files came too. */
    public static byte[] toWorkbook(List<NamedFile> files) throws IOException {
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            boolean hasQuestions = false;
            for (NamedFile file : files) {
                List<List<String>> records = parse(new String(file.bytes(), StandardCharsets.UTF_8));
                String sheetName = sheetFor(records);
                if (sheetName == null) {
                    throw new BusinessRuleException("Could not tell what \"" + file.name() + "\" is. Start from the CSV "
                            + "templates: the questions file begins with the heading Title, the test cases file with "
                            + "Question row, Input and the tables file with Question row, Table name.");
                }
                if (book.getSheet(sheetName) != null) {
                    throw new BusinessRuleException("Two of the files are " + sheetName.toLowerCase() + " files; send one of each.");
                }
                hasQuestions |= sheetName.equals("Questions");
                write(book.createSheet(sheetName), records, file.name());
            }
            if (!hasQuestions) {
                throw new BusinessRuleException("Send the questions CSV (the one whose first heading is Title).");
            }
            // The importer reads Questions first; keep it first however the files arrived.
            book.setSheetOrder("Questions", 0);
            book.write(out);
            return out.toByteArray();
        }
    }

    public record NamedFile(String name, byte[] bytes) {
    }

    private static String sheetFor(List<List<String>> records) {
        if (records.isEmpty()) {
            return null;
        }
        List<String> head = records.get(0);
        String first = head.isEmpty() ? "" : head.get(0).trim().toLowerCase();
        String second = head.size() < 2 ? "" : head.get(1).trim().toLowerCase();
        if (first.equals("title")) {
            return "Questions";
        }
        if (first.equals("question row") && second.equals("input")) {
            return "Test cases";
        }
        if (first.equals("question row") && second.equals("table name")) {
            return "Tables";
        }
        return null;
    }

    private static void write(Sheet sheet, List<List<String>> records, String fileName) {
        // Blank lines stay as blank rows, so a row number in the file is the row number the importer reports.
        for (int r = 0; r < records.size(); r++) {
            List<String> record = records.get(r);
            if (record.size() == 1 && record.get(0).isEmpty()) {
                continue;
            }
            Row row = sheet.createRow(r);
            for (int c = 0; c < record.size(); c++) {
                String value = record.get(c);
                if (value.isEmpty()) {
                    continue;
                }
                if (value.length() > 32_000) {
                    throw new BusinessRuleException("Row " + (r + 1) + " of \"" + fileName + "\" has a value over 32,000 "
                            + "characters. Put a big table in the tables file, one line per row, instead.");
                }
                row.createCell(c).setCellValue(value);
            }
        }
    }

    /**
     * RFC 4180 CSV: quoted values may hold commas, doubled quotes and line breaks. A leading byte-order mark (which
     * Excel adds) is dropped, and ";" is taken as the separator when the heading line has no comma (Excel in some
     * regions saves that way).
     */
    static List<List<String>> parse(String text) {
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        int lineEnd = text.indexOf('\n');
        String firstLine = lineEnd < 0 ? text : text.substring(0, lineEnd);
        char sep = firstLine.contains(",") ? ',' : firstLine.contains(";") ? ';' : firstLine.contains("\t") ? '\t' : ',';

        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    cell.append(c);
                }
            } else if (c == '"' && cell.length() == 0) {
                quoted = true;
            } else if (c == sep) {
                record.add(cell.toString());
                cell.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                record.add(cell.toString());
                cell.setLength(0);
                records.add(record);
                record = new ArrayList<>();
            } else {
                cell.append(c);
            }
        }
        if (cell.length() > 0 || !record.isEmpty()) {
            record.add(cell.toString());
            records.add(record);
        }
        return records;
    }

    // -----------------------------------------------------------------
    // Templates
    // -----------------------------------------------------------------

    /** A zip of questions.csv, test-cases.csv and tables.csv, made from the Excel template so the two never differ. */
    public static byte[] templateZip() throws IOException {
        try (Workbook book = new XSSFWorkbook(new ByteArrayInputStream(MaterialSheet.template()));
             ByteArrayOutputStream out = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(out)) {
            String[][] parts = {{"Questions", "questions.csv"}, {"Test cases", "test-cases.csv"}, {"Tables", "tables.csv"}};
            for (String[] part : parts) {
                zip.putNextEntry(new ZipEntry(part[1]));
                // The byte-order mark makes Excel open the file as UTF-8 instead of mangling non-English text.
                zip.write("﻿".getBytes(StandardCharsets.UTF_8));
                zip.write(toCsv(book.getSheet(part[0])).getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            zip.finish();
            return out.toByteArray();
        }
    }

    private static String toCsv(Sheet sheet) {
        DataFormatter f = new DataFormatter();
        StringBuilder csv = new StringBuilder();
        for (int r = 0; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            int width = row == null ? 0 : row.getLastCellNum();
            // Every line is as wide as the heading, so a spreadsheet shows a clean grid.
            width = Math.max(width, sheet.getRow(0).getLastCellNum());
            for (int c = 0; c < width; c++) {
                if (c > 0) {
                    csv.append(',');
                }
                Cell cell = row == null ? null : row.getCell(c);
                csv.append(quote(cell == null ? "" : f.formatCellValue(cell)));
            }
            csv.append("\r\n");
        }
        return csv.toString();
    }

    private static String quote(String value) {
        boolean needs = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        return needs ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
    }
}

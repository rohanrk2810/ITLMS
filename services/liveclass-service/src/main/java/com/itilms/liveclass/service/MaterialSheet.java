package com.itilms.liveclass.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.itilms.liveclass.dto.request.AskQuestionRequest;
import com.itilms.liveclass.dto.request.DataTable;
import com.itilms.liveclass.dto.request.MaterialRequest;
import com.itilms.liveclass.entity.QuestionType;

/**
 * The Excel format for filling the question bank in bulk: one question per row, the columns named in {@link #HEADERS}.
 * {@link #parse} turns a sheet into bank items and a list of the rows it could not use, so a few bad rows never lose
 * the good ones; {@link #template} writes the sheet trainers start from.
 */
public final class MaterialSheet {

    public static final int MAX_ROWS = 500;
    static final int MAX_OPTIONS = 8;

    // Column positions. Options sit in 3..10 so the answer key can name them A..H.
    static final int TITLE = 0, TYPE = 1, QUESTION = 2, OPTION_1 = 3, CORRECT = 11, MARKS = 12, EXPLANATION = 13,
            TOPIC = 14, TAGS = 15, DIFFICULTY = 16, SHARED = 17, CODE = 18, TABLE_NAME = 19, TABLE_DATA = 20;

    static final String[] HEADERS = {
            "Title", "Type", "Question", "Option A", "Option B", "Option C", "Option D", "Option E", "Option F",
            "Option G", "Option H", "Correct answer", "Marks", "Explanation", "Topic", "Tags", "Difficulty", "Share",
            "Code to show", "Table name", "Table data"};

    private MaterialSheet() {
    }

    /** What an upload produced: the items to save, and a message per row that was skipped. */
    public record Result(List<MaterialRequest> items, List<RowError> errors) {
    }

    public record RowError(int row, String message) {
    }

    public static Result parse(InputStream in) throws IOException {
        List<MaterialRequest> items = new ArrayList<>();
        List<RowError> errors = new ArrayList<>();
        DataFormatter format = new DataFormatter(Locale.ROOT);
        try (Workbook book = new XSSFWorkbook(in)) {
            Sheet sheet = book.getSheetAt(0);
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isBlank(row, format)) {
                    continue;
                }
                if (items.size() + errors.size() >= MAX_ROWS) {
                    errors.add(new RowError(r + 1, "Only the first " + MAX_ROWS + " rows are read; upload the rest separately."));
                    break;
                }
                try {
                    items.add(toItem(row, format));
                } catch (IllegalArgumentException ex) {
                    errors.add(new RowError(r + 1, ex.getMessage()));
                }
            }
        }
        return new Result(items, errors);
    }

    private static MaterialRequest toItem(Row row, DataFormatter f) {
        String prompt = text(row, QUESTION, f);
        if (prompt.isEmpty()) {
            throw new IllegalArgumentException("The question is empty.");
        }
        QuestionType type = typeOf(text(row, TYPE, f));

        List<String> options = null;
        List<Integer> correct = null;
        List<String> accepted = null;
        String key = text(row, CORRECT, f);

        if (type == QuestionType.MCQ || type == QuestionType.MULTIPLE_SELECT) {
            options = new ArrayList<>();
            for (int c = OPTION_1; c < OPTION_1 + MAX_OPTIONS; c++) {
                String option = text(row, c, f);
                if (!option.isEmpty()) {
                    options.add(option);
                }
            }
            if (options.size() < 2) {
                throw new IllegalArgumentException("Give at least two options.");
            }
            correct = letters(key, options.size());
            if (type == QuestionType.MCQ && correct.size() != 1) {
                throw new IllegalArgumentException("Single choice needs exactly one correct letter (e.g. B); use MULTIPLE_SELECT for more.");
            }
        } else if (type == QuestionType.TRUE_FALSE) {
            String k = key.toLowerCase(Locale.ROOT);
            if (!k.equals("true") && !k.equals("false")) {
                throw new IllegalArgumentException("For TRUE_FALSE write True or False in Correct answer.");
            }
            correct = List.of(k.equals("true") ? 0 : 1);
        } else if (type == QuestionType.SHORT_ANSWER && !key.isEmpty()) {
            accepted = List.of(key.split("\\|")).stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        }

        Integer marks = null;
        String marksText = text(row, MARKS, f);
        if (!marksText.isEmpty()) {
            try {
                marks = (int) Math.round(Double.parseDouble(marksText));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Marks must be a number.");
            }
            if (marks < 1 || marks > 100) {
                throw new IllegalArgumentException("Marks must be between 1 and 100.");
            }
        }

        String difficulty = text(row, DIFFICULTY, f).toUpperCase(Locale.ROOT);
        if (!difficulty.isEmpty() && !List.of("EASY", "MEDIUM", "HARD").contains(difficulty)) {
            throw new IllegalArgumentException("Difficulty must be EASY, MEDIUM or HARD.");
        }

        String code = text(row, CODE, f);
        List<DataTable> dataset = tableOf(text(row, TABLE_NAME, f), text(row, TABLE_DATA, f));

        AskQuestionRequest question = new AskQuestionRequest(type, prompt, options, correct, accepted, null, null,
                blankToNull(text(row, EXPLANATION, f)), marks, blankToNull(code), code.isEmpty() ? null : "text", dataset);

        String title = text(row, TITLE, f);
        if (title.isEmpty()) {
            title = prompt.length() > 60 ? prompt.substring(0, 60) : prompt;
        }
        List<String> tags = text(row, TAGS, f).isEmpty() ? List.of()
                : List.of(text(row, TAGS, f).split(",")).stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        String share = text(row, SHARED, f).toLowerCase(Locale.ROOT);
        boolean shared = share.equals("yes") || share.equals("y") || share.equals("true");
        return new MaterialRequest(title.length() > 160 ? title.substring(0, 160) : title, question, null,
                blankToNull(text(row, TOPIC, f)), tags, difficulty.isEmpty() ? null : difficulty, shared);
    }

    private static QuestionType typeOf(String raw) {
        String t = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        return switch (t) {
            case "", "MCQ", "SINGLE_CHOICE" -> QuestionType.MCQ;
            case "MULTIPLE_SELECT", "MULTI_CHOICE", "MULTIPLE" -> QuestionType.MULTIPLE_SELECT;
            case "TRUE_FALSE", "TRUE/FALSE" -> QuestionType.TRUE_FALSE;
            case "SHORT_ANSWER", "SHORT" -> QuestionType.SHORT_ANSWER;
            case "OTHER", "OPEN" -> QuestionType.OTHER;
            default -> throw new IllegalArgumentException(
                    "Unknown type \"" + raw + "\". Use MCQ, MULTIPLE_SELECT, TRUE_FALSE, SHORT_ANSWER or OTHER.");
        };
    }

    /** "B" or "A,C" or "2" or "1,3" - a letter or a number per right option, counted from A / 1. */
    private static List<Integer> letters(String key, int optionCount) {
        if (key.isEmpty()) {
            throw new IllegalArgumentException("Say which option is correct (e.g. B).");
        }
        List<Integer> picked = new ArrayList<>();
        for (String part : key.split("[,;\\s]+")) {
            if (part.isEmpty()) {
                continue;
            }
            int index;
            if (part.length() == 1 && Character.isLetter(part.charAt(0))) {
                index = Character.toUpperCase(part.charAt(0)) - 'A';
            } else {
                try {
                    index = Integer.parseInt(part) - 1;
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("\"" + part + "\" is not an option letter.");
                }
            }
            if (index < 0 || index >= optionCount) {
                throw new IllegalArgumentException("Correct answer \"" + part + "\" is not one of the " + optionCount + " options.");
            }
            if (!picked.contains(index)) {
                picked.add(index);
            }
        }
        return picked.stream().sorted().toList();
    }

    /** First line of the cell is the column names; each line after is a row. Commas or tabs between values. */
    private static List<DataTable> tableOf(String name, String data) {
        if (name.isEmpty() && data.isEmpty()) {
            return null;
        }
        if (name.isEmpty() || data.isEmpty()) {
            throw new IllegalArgumentException("A table needs both a name and its data.");
        }
        List<String> lines = List.of(data.split("\\r?\\n")).stream().filter(l -> !l.isBlank()).toList();
        List<String> columns = split(lines.get(0));
        List<List<String>> rows = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            List<String> cells = split(line);
            List<String> padded = new ArrayList<>();
            for (int i = 0; i < columns.size(); i++) {
                padded.add(i < cells.size() ? cells.get(i) : "");
            }
            rows.add(padded);
        }
        if (columns.size() > 12 || rows.size() > 50) {
            throw new IllegalArgumentException("A table can have up to 12 columns and 50 rows.");
        }
        return List.of(new DataTable(name, columns, rows));
    }

    private static List<String> split(String line) {
        return List.of(line.split(line.contains("\t") ? "\t" : ",", -1)).stream().map(String::trim).toList();
    }

    private static String text(Row row, int column, DataFormatter f) {
        Cell cell = row.getCell(column);
        return cell == null ? "" : f.formatCellValue(cell).trim();
    }

    private static boolean isBlank(Row row, DataFormatter f) {
        for (int c = 0; c < HEADERS.length; c++) {
            if (!text(row, c, f).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    // -----------------------------------------------------------------
    // The template
    // -----------------------------------------------------------------

    /** The sheet trainers fill in: headings, three example rows and a page of instructions. */
    public static byte[] template() throws IOException {
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = book.createSheet("Questions");
            CellStyle bold = book.createCellStyle();
            Font font = book.createFont();
            font.setBold(true);
            bold.setFont(font);
            CellStyle wrap = book.createCellStyle();
            wrap.setWrapText(true);

            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(bold);
                sheet.setColumnWidth(i, i == QUESTION || i == CODE || i == TABLE_DATA ? 11000 : 4800);
            }
            sheet.createFreezePane(0, 1);

            example(sheet, wrap, 1, new String[]{"Loop output", "MCQ", "What does this print?", "0 1 2", "1 2 3", "0 2 4",
                    "Error", "", "", "", "", "C", "1", "range starts at 0 and steps by 1, so i*2 gives 0 2 4.", "Python loops",
                    "python, loops", "EASY", "No", "for i in range(3):\n    print(i * 2)", "", ""});
            example(sheet, wrap, 2, new String[]{"Salary query", "SHORT_ANSWER", "Write the query that lists names earning above 45000.",
                    "", "", "", "", "", "", "", "", "SELECT name FROM employees WHERE salary > 45000", "2", "", "SQL basics",
                    "sql, where", "MEDIUM", "Yes", "", "employees", "id,name,salary\n1,Asha,50000\n2,Ravi,42000\n3,Meera,47000"});
            example(sheet, wrap, 3, new String[]{"Java is typed", "TRUE_FALSE", "Java is a statically typed language.",
                    "", "", "", "", "", "", "", "", "True", "1", "", "Java basics", "java", "EASY", "No", "", "", ""});

            Sheet help = book.createSheet("How to fill");
            String[] lines = {
                    "One question per row, starting from row 2. Delete the three example rows before you upload.",
                    "",
                    "Type: MCQ (one right option), MULTIPLE_SELECT, TRUE_FALSE, SHORT_ANSWER or OTHER (open question). Blank means MCQ.",
                    "Options A to H: fill only the ones you need (at least 2 for MCQ and MULTIPLE_SELECT).",
                    "Correct answer: MCQ - one letter, e.g. B. MULTIPLE_SELECT - letters, e.g. A,C. TRUE_FALSE - True or False.",
                    "   SHORT_ANSWER - the right answer; put | between answers that are also right, e.g. 6|six. Blank = you mark it yourself.",
                    "Marks: 1 to 100. Blank means 1.",
                    "Tags: separated by commas. Difficulty: EASY, MEDIUM or HARD. Share: Yes lets other trainers use the question.",
                    "Code to show: code the students see above the question.",
                    "Table name and Table data: a small database shown as a table. First line of Table data is the column names, then one row per line",
                    "   (press Alt+Enter inside the cell for a new line). Values are separated by commas.",
                    "Title is optional; the start of the question is used when it is blank.",
                    "Rows with a mistake are skipped and listed with the reason; the other rows are still saved."};
            for (int i = 0; i < lines.length; i++) {
                help.createRow(i).createCell(0).setCellValue(lines[i]);
            }
            help.setColumnWidth(0, 30000);

            book.write(out);
            return out.toByteArray();
        }
    }

    private static void example(Sheet sheet, CellStyle wrap, int rowIndex, String[] values) {
        Row row = sheet.createRow(rowIndex);
        for (int i = 0; i < values.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(values[i]);
            cell.setCellStyle(wrap);
        }
    }
}

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
import com.itilms.liveclass.dto.request.AskQuestionRequest.CodeTestCase;
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
    private static final String NL = "\n";

    // Column positions. Options sit in 3..10 so the answer key can name them A..H.
    static final int TITLE = 0, TYPE = 1, QUESTION = 2, OPTION_1 = 3, CORRECT = 11, MARKS = 12, EXPLANATION = 13,
            TOPIC = 14, TAGS = 15, DIFFICULTY = 16, SHARED = 17, CODE = 18, TABLE_NAME = 19, TABLE_DATA = 20,
            LANGUAGE = 21, STARTER = 22, TABLE_2_NAME = 23, TABLE_2_DATA = 24, TABLE_3_NAME = 25, TABLE_3_DATA = 26,
            SQL_SETUP = 27, LANG_CHOICE = 28, FOLDER = 29;

    /** The compilers a coding question can name; the same set tests accept. */
    static final List<String> LANGUAGES = List.of("java", "python", "c", "cpp", "csharp", "sql");

    static final String[] HEADERS = {
            "Title", "Type", "Question", "Option A", "Option B", "Option C", "Option D", "Option E", "Option F",
            "Option G", "Option H", "Correct answer", "Marks", "Explanation", "Topic", "Tags", "Difficulty", "Share",
            "Code to show", "Table name", "Table data", "Language (coding)", "Starter code (coding)", "Table 2 name",
            "Table 2 data", "Table 3 name", "Table 3 data", "SQL setup (coding)", "Let students pick language (coding)",
            "Folder"};
    static final String[] TEST_HEADERS = {"Question row", "Input", "Expected output", "Hidden", "Weight"};

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
            java.util.Map<Integer, List<CodeTestCase>> cases = testCases(book, format, errors);
            java.util.Map<Integer, List<DataTable>> bigTables = tablesSheet(book, format, errors);
            java.util.Set<Integer> codingRows = new java.util.HashSet<>();
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
                    MaterialRequest item = toItem(row, format, cases.getOrDefault(r + 1, List.of()),
                            bigTables.getOrDefault(r + 1, List.of()));
                    if (item.question().type() == QuestionType.CODING) {
                        codingRows.add(r + 1);
                    }
                    items.add(item);
                } catch (IllegalArgumentException ex) {
                    errors.add(new RowError(r + 1, ex.getMessage()));
                }
            }
            for (Integer questionRow : cases.keySet()) {
                if (!codingRows.contains(questionRow)) {
                    errors.add(new RowError(questionRow, "Test cases point at this row, but it is not a CODING question that was read."));
                }
            }
        }
        return new Result(items, errors);
    }

    private static MaterialRequest toItem(Row row, DataFormatter f, List<CodeTestCase> cases, List<DataTable> sheetTables) {
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
        List<DataTable> dataset = new ArrayList<>();
        for (int[] pair : new int[][]{{TABLE_NAME, TABLE_DATA}, {TABLE_2_NAME, TABLE_2_DATA}, {TABLE_3_NAME, TABLE_3_DATA}}) {
            dataset.addAll(tableOf(text(row, pair[0], f), text(row, pair[1], f)));
        }
        dataset.addAll(sheetTables);
        try {
            DatasetRules.validate(dataset);
        } catch (com.itilms.common.exception.BusinessRuleException ex) {
            throw new IllegalArgumentException(ex.getMessage());
        }
        String language = text(row, LANGUAGE, f).toLowerCase(Locale.ROOT);
        String starter = text(row, STARTER, f);
        String setup = text(row, SQL_SETUP, f);
        String choice = text(row, LANG_CHOICE, f).toLowerCase(Locale.ROOT);
        if (type != QuestionType.CODING
                && (!language.isEmpty() || !starter.isEmpty() || !setup.isEmpty() || !choice.isEmpty() || !cases.isEmpty())) {
            throw new IllegalArgumentException(
                    "Language, starter code, SQL setup and test cases are only for CODING questions.");
        }
        if (type == QuestionType.CODING) {
            if (language.isEmpty()) {
                language = "python";
            }
            if (!LANGUAGES.contains(language)) {
                throw new IllegalArgumentException(
                        "Language \"" + language + "\" is not available. Use java, python, c, cpp, csharp or sql.");
            }
            if (!setup.isEmpty() && !language.equals("sql")) {
                throw new IllegalArgumentException("SQL setup is only for the sql language.");
            }
        }
        String folder;
        try {
            folder = LiveMaterialService.normalizeFolder(text(row, FOLDER, f));
        } catch (com.itilms.common.exception.BusinessRuleException ex) {
            throw new IllegalArgumentException(ex.getMessage());
        }

        AskQuestionRequest question = new AskQuestionRequest(type, prompt, options, correct, accepted,
                type == QuestionType.CODING ? language : null,
                type == QuestionType.CODING ? blankToNull(starter) : null,
                blankToNull(text(row, EXPLANATION, f)), marks, blankToNull(code), code.isEmpty() ? null : "text",
                dataset.isEmpty() ? null : dataset,
                type == QuestionType.CODING && !cases.isEmpty() ? cases : null,
                type == QuestionType.CODING ? blankToNull(setup) : null,
                type == QuestionType.CODING && !language.equals("sql")
                        ? !(choice.equals("no") || choice.equals("n") || choice.equals("false")) : null);

        String title = text(row, TITLE, f);
        if (title.isEmpty()) {
            title = prompt.length() > 60 ? prompt.substring(0, 60) : prompt;
        }
        List<String> tags = text(row, TAGS, f).isEmpty() ? List.of()
                : List.of(text(row, TAGS, f).split(",")).stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        String share = text(row, SHARED, f).toLowerCase(Locale.ROOT);
        boolean shared = share.equals("yes") || share.equals("y") || share.equals("true");
        return new MaterialRequest(title.length() > 160 ? title.substring(0, 160) : title, question, null,
                blankToNull(text(row, TOPIC, f)), tags, difficulty.isEmpty() ? null : difficulty, shared, folder);
    }

    /** The "Test cases" sheet: each row belongs to the CODING question on the Questions row it names. */
    private static java.util.Map<Integer, List<CodeTestCase>> testCases(Workbook book, DataFormatter f, List<RowError> errors) {
        java.util.Map<Integer, List<CodeTestCase>> byRow = new java.util.LinkedHashMap<>();
        Sheet sheet = null;
        for (int i = 0; i < book.getNumberOfSheets(); i++) {
            if ("Test cases".equalsIgnoreCase(book.getSheetName(i))) {
                sheet = book.getSheetAt(i);
            }
        }
        if (sheet == null) {
            return byRow;
        }
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            String ref = text(row, 0, f);
            String input = text(row, 1, f);
            String expected = text(row, 2, f);
            if (ref.isEmpty() && input.isEmpty() && expected.isEmpty()) {
                continue;
            }
            String where = "Test cases sheet, row " + (r + 1) + ": ";
            try {
                int questionRow = (int) Math.round(Double.parseDouble(ref));
                if (questionRow < 2) {
                    throw new NumberFormatException();
                }
                if (expected.isEmpty()) {
                    throw new IllegalArgumentException("Expected output is empty.");
                }
                String hidden = text(row, 3, f).toLowerCase(Locale.ROOT);
                String weightText = text(row, 4, f);
                int weight = weightText.isEmpty() ? 1 : (int) Math.round(Double.parseDouble(weightText));
                if (weight < 1 || weight > 100) {
                    throw new IllegalArgumentException("Weight must be between 1 and 100.");
                }
                List<CodeTestCase> list = byRow.computeIfAbsent(questionRow, k -> new ArrayList<>());
                if (list.size() >= 10) {
                    throw new IllegalArgumentException("A question takes at most 10 test cases.");
                }
                list.add(new CodeTestCase(input, expected, hidden.equals("yes") || hidden.equals("y") || hidden.equals("true"), weight));
            } catch (NumberFormatException ex) {
                errors.add(new RowError(r + 1, where + "\"Question row\" must be the row number of the question on the Questions sheet."));
            } catch (IllegalArgumentException ex) {
                errors.add(new RowError(r + 1, where + ex.getMessage()));
            }
        }
        return byRow;
    }

    private static QuestionType typeOf(String raw) {
        String t = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        return switch (t) {
            case "", "MCQ", "SINGLE_CHOICE" -> QuestionType.MCQ;
            case "MULTIPLE_SELECT", "MULTI_CHOICE", "MULTIPLE" -> QuestionType.MULTIPLE_SELECT;
            case "TRUE_FALSE", "TRUE/FALSE" -> QuestionType.TRUE_FALSE;
            case "SHORT_ANSWER", "SHORT" -> QuestionType.SHORT_ANSWER;
            case "OTHER", "OPEN" -> QuestionType.OTHER;
            case "CODING", "CODE" -> QuestionType.CODING;
            default -> throw new IllegalArgumentException(
                    "Unknown type \"" + raw + "\". Use MCQ, MULTIPLE_SELECT, TRUE_FALSE, SHORT_ANSWER, CODING or OTHER.");
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

    /**
     * The "Tables" sheet, for databases too big for one cell (a cell holds about 32,000 characters). Columns: Question
     * row, Table name, then the values. The first row of a table is its column names and the rows after it are its
     * data; a new table starts when the question row or the name changes.
     */
    private static java.util.Map<Integer, List<DataTable>> tablesSheet(Workbook book, DataFormatter f, List<RowError> errors) {
        java.util.Map<Integer, List<DataTable>> byRow = new java.util.LinkedHashMap<>();
        Sheet sheet = null;
        for (int i = 0; i < book.getNumberOfSheets(); i++) {
            if ("Tables".equalsIgnoreCase(book.getSheetName(i))) {
                sheet = book.getSheetAt(i);
            }
        }
        if (sheet == null) {
            return byRow;
        }
        int currentRow = -1;
        String currentName = null;
        List<String> columns = null;
        List<List<String>> rows = null;
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            String ref = text(row, 0, f);
            String name = text(row, 1, f);
            if (ref.isEmpty() && name.isEmpty()) {
                continue;
            }
            int questionRow;
            try {
                questionRow = (int) Math.round(Double.parseDouble(ref));
                if (questionRow < 2 || name.isEmpty()) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException ex) {
                errors.add(new RowError(r + 1, "Tables sheet, row " + (r + 1)
                        + ": needs the Question row number and the Table name."));
                continue;
            }
            if (questionRow != currentRow || !name.equals(currentName)) {
                int width = lastFilled(row);
                columns = new ArrayList<>();
                for (int c = 2; c < width; c++) {
                    columns.add(text(row, c, f));
                }
                rows = new ArrayList<>();
                byRow.computeIfAbsent(questionRow, k -> new ArrayList<>()).add(new DataTable(name, columns, rows));
                currentRow = questionRow;
                currentName = name;
                continue;
            }
            List<String> values = new ArrayList<>();
            for (int c = 0; c < columns.size(); c++) {
                values.add(text(row, c + 2, f));
            }
            rows.add(values);
        }
        return byRow;
    }

    /** One past the last non-empty cell of the row. */
    private static int lastFilled(Row row) {
        int last = row.getLastCellNum();
        DataFormatter f = new DataFormatter(Locale.ROOT);
        while (last > 2 && text(row, last - 1, f).isEmpty()) {
            last--;
        }
        return last;
    }

    /** First line of the cell is the column names; each line after is a row. Commas or tabs between values. */
    private static List<DataTable> tableOf(String name, String data) {
        if (name.isEmpty() && data.isEmpty()) {
            return List.of();
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
        return List.of(new DataTable(name, columns, rows));
    }

    /** Splits a line on tabs (pasted from a spreadsheet) or commas, honouring "quoted, values" as CSV files do. */
    static List<String> split(String line) {
        char sep = line.indexOf('\t') >= 0 ? '\t' : ',';
        List<String> out = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    cell.append(c);
                }
            } else if (c == '"' && cell.toString().isBlank()) {
                quoted = true;
                cell.setLength(0);
            } else if (c == sep) {
                out.add(cell.toString().trim());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        out.add(cell.toString().trim());
        return out;
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

    /** The sheet trainers fill in: headings, four example rows and a page of instructions. */
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
            example(sheet, wrap, 4, new String[]{"Sum of three numbers", "CODING",
                    "Read three numbers from one line and print their sum.",
                    "", "", "", "", "", "", "", "", "", "5", "", "Input and output", "python, input", "EASY", "No",
                    "", "", "", "python", "numbers = input().split()" + NL + "# print the sum"});
            folder(sheet, 1, "Python/Loops/MCQ");
            folder(sheet, 2, "SQL/Basics/Short answer");
            folder(sheet, 3, "Java/Basics/True-False");
            folder(sheet, 4, "Python/Basics/Coding");

            // A second table for the SQL example (Questions row 3), on the sheet meant for big databases.
            Sheet tables = book.createSheet("Tables");
            String[] tableHeader = {"Question row", "Table name", "Values (first row = column names)"};
            Row tablesHead = tables.createRow(0);
            for (int i = 0; i < tableHeader.length; i++) {
                Cell cell = tablesHead.createCell(i);
                cell.setCellValue(tableHeader[i]);
                cell.setCellStyle(bold);
                tables.setColumnWidth(i, i == 2 ? 9000 : 4800);
            }
            tables.createFreezePane(0, 1);
            tableRow(tables, 1, "3", "departments", "id", "name");
            tableRow(tables, 2, "3", "departments", "1", "Sales");
            tableRow(tables, 3, "3", "departments", "2", "Support");

            // Row 5 of the Questions sheet is the coding example above; its test cases name that row.
            Sheet tests = book.createSheet("Test cases");
            Row testHeader = tests.createRow(0);
            for (int i = 0; i < TEST_HEADERS.length; i++) {
                Cell cell = testHeader.createCell(i);
                cell.setCellValue(TEST_HEADERS[i]);
                cell.setCellStyle(bold);
                tests.setColumnWidth(i, i == 0 ? 4800 : 9000);
            }
            tests.createFreezePane(0, 1);
            testCase(tests, 1, "5", "1 2 3", "6", "No", "1");
            testCase(tests, 2, "5", "10 20 30", "60", "Yes", "2");

            Sheet help = book.createSheet("How to fill");
            String[] lines = {
                    "One question per row, starting from row 2. Delete the four example rows before you upload.",
                    "",
                    "Type: MCQ (one right option), MULTIPLE_SELECT, TRUE_FALSE, SHORT_ANSWER, CODING (student writes code) or OTHER (open question). Blank means MCQ.",
                    "Options A to H: fill only the ones you need (at least 2 for MCQ and MULTIPLE_SELECT).",
                    "Correct answer: MCQ - one letter, e.g. B. MULTIPLE_SELECT - letters, e.g. A,C. TRUE_FALSE - True or False.",
                    "   SHORT_ANSWER - the right answer; put | between answers that are also right, e.g. 6|six. Blank = you mark it yourself.",
                    "Marks: 1 to 100. Blank means 1.",
                    "Tags: separated by commas. Difficulty: EASY, MEDIUM or HARD. Share: Yes lets other trainers use the question.",
                    "Code to show: code the students see above the question.",
                    "Table name and Table data: a small database shown as a table. First line of Table data is the column names, then one row per line",
                    "   (press Alt+Enter inside the cell for a new line). Values are separated by commas. Use Table 2 and Table 3 for more tables.",
                    "CODING questions: Language is the compiler - java, python, c, cpp, csharp or sql (blank = python). Starter code is what the editor opens with.",
                    "   SQL setup (sql only) is the CREATE TABLE / INSERT statements that run before the student query. Let students pick language: blank or Yes lets them use any language, No keeps them to the one named.",
                    "Test cases go on the Test cases sheet, one row per case: Question row is the row number of the CODING question on the Questions sheet,",
                    "   then Input (what is typed in), Expected output (what a right program prints), Hidden (Yes = student sees only pass/fail) and Weight (1 to 100).",
                    "   Up to 10 per question. A test uses them to mark the code; without them the trainer reads the code.",
                    "Folder: where the question is filed, e.g. Java/OOPs/Coding. Folders are made for you. Blank = the top level.",
                    "Big databases: one cell holds only about 32,000 characters, so put large tables on the Tables sheet instead.",
                    "   One row per line of the table: Question row, Table name, then the values across. The first row of a table is its column names;",
                    "   the rows after it are the data. Up to 20 tables, 60 columns and 20,000 rows each. Put the Question row number on every line.",
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

    private static void tableRow(Sheet sheet, int rowIndex, String questionRow, String name, String... values) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(questionRow);
        row.createCell(1).setCellValue(name);
        for (int i = 0; i < values.length; i++) {
            row.createCell(2 + i).setCellValue(values[i]);
        }
    }

    private static void folder(Sheet sheet, int rowIndex, String path) {
        sheet.getRow(rowIndex).createCell(FOLDER).setCellValue(path);
    }

    private static void testCase(Sheet sheet, int rowIndex, String questionRow, String input, String expected,
                                 String hidden, String weight) {
        Row row = sheet.createRow(rowIndex);
        String[] values = {questionRow, input, expected, hidden, weight};
        for (int i = 0; i < values.length; i++) {
            row.createCell(i).setCellValue(values[i]);
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

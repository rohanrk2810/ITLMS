package com.itilms.codeexec.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Cuts a script into the statements Oracle is sent one at a time, the way SQL*Plus would.
 *
 * <p>A plain statement ends at a {@code ;}. A PL/SQL block ({@code BEGIN}, {@code DECLARE}, or a
 * {@code CREATE} of a procedure, function, package, trigger or type) contains {@code ;} itself, so it ends at a
 * line holding only {@code /}. Semicolons inside quotes and comments end nothing. SQL*Plus-only commands such as
 * {@code SET SERVEROUTPUT ON} are dropped: the driver would reject them.
 */
final class SqlScript {

    private static final Pattern BLOCK_START = Pattern.compile(
            "^(BEGIN|DECLARE|CREATE\\s+(OR\\s+REPLACE\\s+)?((NON)?EDITIONABLE\\s+)?(PROCEDURE|FUNCTION|PACKAGE|TRIGGER|TYPE))\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern CLIENT_COMMAND = Pattern.compile(
            "^(SET\\s+(SERVEROUTPUT|ECHO|FEEDBACK|PAGESIZE|PAGES|LINESIZE|LINES|HEADING|HEAD|VERIFY|TIMING|TERMOUT|AUTOCOMMIT|DEFINE|SQLBLANKLINES)"
                    + "|SPOOL|PROMPT|REM|REMARK|EXIT|QUIT|WHENEVER|COLUMN|COL|CLEAR|DEFINE|TTITLE|BTITLE)\\b.*",
            Pattern.CASE_INSENSITIVE);

    private SqlScript() {
    }

    static List<String> statements(String script) {
        List<String> result = new ArrayList<>();
        String text = script.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder current = new StringBuilder();
        boolean hasCode = false;
        int i = 0;
        int n = text.length();

        while (i < n) {
            char c = text.charAt(i);

            if (i == 0 || text.charAt(i - 1) == '\n') {
                int end = text.indexOf('\n', i);
                String line = text.substring(i, end < 0 ? n : end).strip();
                if (!hasCode && CLIENT_COMMAND.matcher(line).matches()) {
                    i = end < 0 ? n : end + 1;
                    continue;
                }
                if (line.equals("/")) {
                    flush(result, current, hasCode);
                    current.setLength(0);
                    hasCode = false;
                    i = end < 0 ? n : end + 1;
                    continue;
                }
            }

            if (c == '-' && i + 1 < n && text.charAt(i + 1) == '-') {
                int end = text.indexOf('\n', i);
                int stop = end < 0 ? n : end;
                current.append(text, i, stop);
                i = stop;
            } else if (c == '/' && i + 1 < n && text.charAt(i + 1) == '*') {
                int end = text.indexOf("*/", i + 2);
                int stop = end < 0 ? n : end + 2;
                current.append(text, i, stop);
                i = stop;
            } else if (c == '\'' || c == '"') {
                int stop = endOfQuoted(text, i, c);
                current.append(text, i, stop);
                hasCode = true;
                i = stop;
            } else if (c == ';' && !isBlock(current)) {
                flush(result, current, hasCode);
                current.setLength(0);
                hasCode = false;
                i++;
            } else {
                current.append(c);
                hasCode |= !Character.isWhitespace(c);
                i++;
            }
        }
        flush(result, current, hasCode);
        return result;
    }

    private static boolean isBlock(StringBuilder current) {
        return BLOCK_START.matcher(withoutLeadingComments(current.toString())).find();
    }

    private static String withoutLeadingComments(String text) {
        String s = text.stripLeading();
        while (true) {
            if (s.startsWith("--")) {
                int end = s.indexOf('\n');
                if (end < 0) {
                    return "";
                }
                s = s.substring(end + 1).stripLeading();
            } else if (s.startsWith("/*")) {
                int end = s.indexOf("*/");
                if (end < 0) {
                    return "";
                }
                s = s.substring(end + 2).stripLeading();
            } else {
                return s;
            }
        }
    }

    private static int endOfQuoted(String text, int start, char quote) {
        int i = start + 1;
        while (i < text.length()) {
            if (text.charAt(i) == quote) {
                if (i + 1 < text.length() && text.charAt(i + 1) == quote) {
                    i += 2;
                    continue;
                }
                return i + 1;
            }
            i++;
        }
        return text.length();
    }

    private static void flush(List<String> result, StringBuilder current, boolean hasCode) {
        if (!hasCode) {
            return;
        }
        String statement = current.toString().strip();
        if (!statement.isEmpty()) {
            result.add(statement);
        }
    }
}

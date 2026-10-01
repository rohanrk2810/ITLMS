package com.itilms.codeexec.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Small string helpers: balanced brackets, splitting on top-level commas, telling constants from variables. */
final class Text {

    private static final Pattern IDENT = Pattern.compile("[A-Za-z_]\\w*");
    private static final Pattern ALL_CAPS = Pattern.compile("[A-Z][A-Z0-9_]+");

    private Text() {
    }

    /** Index of the bracket closing the one at {@code open}, or -1. */
    static int matching(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** How many brackets are still open at the end of the text. */
    static int depth(String s) {
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
            }
        }
        return depth;
    }

    /** Splits on a separator that is not inside any bracket. */
    static List<String> splitTop(String s, char sep) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
            } else if (c == sep && depth == 0) {
                parts.add(s.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(s.substring(start));
        return parts;
    }

    /** The first ':' outside brackets (and not part of ':='), which ends a Python block header. */
    static int topLevelColon(String s) {
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
            } else if (c == ':' && depth == 0 && !(i + 1 < s.length() && s.charAt(i + 1) == '=')) {
                return i;
            }
        }
        return -1;
    }

    /** The text inside the first pair of brackets that opens at or after {@code from}, or null. */
    static String argsAfter(String s, int open) {
        int close = matching(s, open);
        return close < 0 ? null : s.substring(open + 1, close);
    }

    /**
     * True when the expression cannot depend on the input: numbers, and names written in CAPITALS of two or more
     * letters (a constant). A lone capital such as N is usually the input size, so it counts as a variable.
     */
    static boolean isConstant(String expr) {
        if (expr == null || expr.isBlank()) {
            return false;
        }
        String e = expr.replaceAll("0[xX][0-9a-fA-F]+|\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?[lLfFuU]*", " ");
        var m = IDENT.matcher(e);
        while (m.find()) {
            String id = m.group();
            if (!ALL_CAPS.matcher(id).matches() && !List.of("Integer", "Long", "Math", "int", "long", "char", "unsigned",
                    "double", "float", "sizeof", "INT_MAX", "int32_t").contains(id)) {
                return false;
            }
        }
        return true;
    }

    static String shorten(String s, int max) {
        String t = s.replaceAll("\\s+", " ").strip();
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }
}

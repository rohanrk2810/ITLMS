package com.itilms.codeexec.analysis;

/**
 * Removes comments and the insides of string and character literals so that nothing in a message or a comment
 * is mistaken for code. Line breaks are kept, so line numbers in the explanation stay right.
 */
final class SourceCleaner {

    private SourceCleaner() {
    }

    static String clean(String src, boolean python) {
        int n = src.length();
        StringBuilder out = new StringBuilder(n);
        int i = 0;
        while (i < n) {
            char c = src.charAt(i);
            if (python && c == '#') {
                while (i < n && src.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }
            if (!python && c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                while (i < n && src.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }
            if (!python && c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                int end = src.indexOf("*/", i + 2);
                int stop = end < 0 ? n : end + 2;
                for (int k = i; k < stop; k++) {
                    out.append(src.charAt(k) == '\n' ? '\n' : ' ');
                }
                i = stop;
                continue;
            }
            if (c == '"' || c == '\'') {
                String triple = "" + c + c + c;
                if ((python || c == '"') && src.startsWith(triple, i)) {
                    int end = src.indexOf(triple, i + 3);
                    int stop = end < 0 ? n : end + 3;
                    out.append(c);
                    for (int k = i + 1; k < stop - 1; k++) {
                        out.append(src.charAt(k) == '\n' ? '\n' : ' ');
                    }
                    out.append(c);
                    i = stop;
                    continue;
                }
                out.append(c);
                int j = i + 1;
                while (j < n && src.charAt(j) != c && src.charAt(j) != '\n') {
                    if (src.charAt(j) == '\\' && j + 1 < n) {
                        out.append(' ');
                        j++;
                    }
                    out.append(' ');
                    j++;
                }
                if (j < n && src.charAt(j) == c) {
                    out.append(c);
                    j++;
                }
                i = j;
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }
}

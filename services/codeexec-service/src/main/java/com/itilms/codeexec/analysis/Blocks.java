package com.itilms.codeexec.analysis;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A rough structure of a program: nested blocks (a loop, an if, a function) holding statements. It is built from
 * braces for C-family languages and from indentation for Python. It does not understand the language - it only
 * recovers enough shape to tell which statements sit inside which loops.
 */
final class Blocks {

    private Blocks() {
    }

    interface Item {
        int line();
    }

    record Stmt(String text, int line) implements Item {
    }

    static final class Block implements Item {
        final String header;
        final int line;
        final List<Item> items = new ArrayList<>();
        /** Python decorators written above this block, e.g. "@lru_cache(None)". */
        String decorators = "";

        Block(String header, int line) {
            this.header = header;
            this.line = line;
        }

        @Override
        public int line() {
            return line;
        }

        /** Every statement and header below this block, joined: used to look for patterns anywhere inside. */
        String allText() {
            StringBuilder sb = new StringBuilder(header).append('\n');
            collect(this, sb);
            return sb.toString();
        }

        private static void collect(Block b, StringBuilder sb) {
            for (Item item : b.items) {
                if (item instanceof Stmt s) {
                    sb.append(s.text()).append('\n');
                } else if (item instanceof Block child) {
                    sb.append(child.header).append('\n');
                    collect(child, sb);
                }
            }
        }
    }

    // ------------------------------------------------------------------ C-family

    static Block parseBraces(String s) {
        Block root = new Block("", 1);
        Deque<Block> stack = new ArrayDeque<>();
        stack.push(root);
        StringBuilder pending = new StringBuilder();
        int paren = 0;
        int textBrace = 0;
        int line = 1;
        int pendingLine = 1;
        boolean afterDo = false;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n') {
                line++;
            }
            if (pending.length() == 0) {
                if (Character.isWhitespace(c)) {
                    continue;
                }
                pendingLine = line;
            }
            switch (c) {
                case '(' -> {
                    paren++;
                    pending.append(c);
                }
                case ')' -> {
                    paren = Math.max(0, paren - 1);
                    pending.append(c);
                }
                case '{' -> {
                    if (paren > 0 || textBrace > 0 || isInitializer(pending)) {
                        textBrace++;
                        pending.append(c);
                    } else {
                        Block b = new Block(pending.toString().trim(), pendingLine);
                        stack.peek().items.add(b);
                        stack.push(b);
                        pending.setLength(0);
                        afterDo = false;
                    }
                }
                case '}' -> {
                    if (textBrace > 0) {
                        textBrace--;
                        pending.append(c);
                    } else {
                        flush(stack.peek(), pending, pendingLine, afterDo);
                        afterDo = false;
                        if (stack.size() > 1) {
                            afterDo = stack.pop().header.equals("do");
                        }
                    }
                }
                case ';' -> {
                    if (paren > 0 || textBrace > 0) {
                        pending.append(c);
                    } else {
                        flush(stack.peek(), pending, pendingLine, afterDo);
                        afterDo = false;
                    }
                }
                default -> pending.append(c == '\n' || c == '\r' || c == '\t' ? ' ' : c);
            }
        }
        flush(stack.peek(), pending, pendingLine, afterDo);
        return root;
    }

    private static boolean isInitializer(CharSequence pending) {
        String t = pending.toString().trim();
        return t.endsWith("=") || t.endsWith("]") || t.endsWith(",");
    }

    /** Turns the pending text into a statement. Returns true when it consumed the "while (...)" that ends a do-loop. */
    private static boolean flush(Block into, StringBuilder pending, int line, boolean afterDo) {
        String text = pending.toString().trim();
        pending.setLength(0);
        if (text.isEmpty()) {
            return false;
        }
        if (afterDo && text.startsWith("while")) {
            return true;
        }
        into.items.add(toItem(text, line));
        return false;
    }

    /** A statement, or - when it is a loop written without braces - a loop block holding its body (parsed the same way). */
    private static Item toItem(String text, int line) {
        if (text.matches("(?s)^(for|while|foreach)\\b\\s*\\(.*")) {
            int open = text.indexOf('(');
            int close = Text.matching(text, open);
            if (close > 0 && close < text.length() - 1) {
                Block loop = new Block(text.substring(0, close + 1), line);
                loop.items.add(toItem(text.substring(close + 1).trim(), line));
                return loop;
            }
        }
        return new Stmt(text, line);
    }

    // ------------------------------------------------------------------ Python

    private static final Pattern PY_HEADER =
            Pattern.compile("^(?:async\\s+)?(def|class|for|while|if|elif|else|try|except|finally|with|match|case)\\b.*");

    static Block parsePython(String s) {
        Block root = new Block("<module>", 1);
        List<Block> blocks = new ArrayList<>();
        List<Integer> indents = new ArrayList<>();
        blocks.add(root);
        indents.add(-1);
        StringBuilder decorators = new StringBuilder();

        String[] lines = s.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            int startLine = i + 1;
            // A statement can continue over several lines inside brackets or after a backslash.
            while ((Text.depth(raw) > 0 || raw.stripTrailing().endsWith("\\")) && i + 1 < lines.length) {
                i++;
                raw = raw.stripTrailing().replaceAll("\\\\$", "") + " " + lines[i].strip();
            }
            if (raw.isBlank()) {
                continue;
            }
            int indent = indentOf(raw);
            String text = raw.strip();

            while (blocks.size() > 1 && indents.get(indents.size() - 1) >= indent) {
                blocks.remove(blocks.size() - 1);
                indents.remove(indents.size() - 1);
            }
            Block parent = blocks.get(blocks.size() - 1);

            if (text.startsWith("@")) {
                decorators.append(text).append(' ');
                continue;
            }
            if (PY_HEADER.matcher(text).matches()) {
                int colon = Text.topLevelColon(text);
                if (colon > 0) {
                    String header = text.substring(0, colon).strip();
                    String rest = text.substring(colon + 1).strip();
                    Block b = new Block(header, startLine);
                    b.decorators = decorators.toString();
                    decorators.setLength(0);
                    parent.items.add(b);
                    if (rest.isEmpty()) {
                        blocks.add(b);
                        indents.add(indent);
                    } else {
                        b.items.add(new Stmt(rest, startLine));
                    }
                    continue;
                }
            }
            decorators.setLength(0);
            parent.items.add(new Stmt(text, startLine));
        }
        return root;
    }

    private static int indentOf(String line) {
        int n = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == ' ') {
                n++;
            } else if (c == '\t') {
                n += 4;
            } else {
                break;
            }
        }
        return n;
    }
}

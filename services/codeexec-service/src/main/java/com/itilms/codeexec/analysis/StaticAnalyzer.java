package com.itilms.codeexec.analysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.itilms.codeexec.analysis.Blocks.Block;
import com.itilms.codeexec.analysis.Blocks.Item;
import com.itilms.codeexec.analysis.Blocks.Stmt;
import com.itilms.codeexec.dto.AnalysisResponse;
import com.itilms.codeexec.dto.AnalysisResponse.Model;
import com.itilms.codeexec.dto.AnalysisResponse.Suggestion;

/**
 * Estimates time and space complexity by reading the source. Nothing is ever run.
 *
 * <p>It is a set of heuristics, not a proof: it counts how deeply loops nest, spots halving loops, sorting calls,
 * recursion and the library calls that hide a scan, and says plainly how sure it is. It cannot know how large the
 * input really is, so a loop over a fixed-size table and a loop over the input look different only when the bound
 * is written as a literal or a CAPITALISED constant.
 */
public final class StaticAnalyzer {

    /** Source longer than this is not analysed: the analysis is for exercises, and the limit bounds the work. */
    public static final int MAX_SOURCE_CHARS = 30_000;

    private StaticAnalyzer() {
    }

    public static AnalysisResponse analyze(String language, String source) {
        String lang = language == null ? "" : language.toUpperCase();
        if (!List.of("JAVA", "PYTHON", "C", "CPP", "CSHARP").contains(lang)) {
            return unsupported(lang);
        }
        if (source == null || source.isBlank()) {
            return unknown(lang, "There is no code to analyse.");
        }
        if (source.length() > MAX_SOURCE_CHARS) {
            return unknown(lang, "The code is too long to analyse here.");
        }
        try {
            return new Run(lang, source).execute();
        } catch (RuntimeException | StackOverflowError e) {
            // An analysis must never break a run: when it cannot decide, it says so.
            return unknown(lang, "The code could not be analysed (unusual structure).");
        }
    }

    // ------------------------------------------------------------------ small value types

    private enum Kind { LIST, SET, MAP, STRING, ARRAY, SB }

    private enum Tag { SORT, LIST_LOOKUP, FRONT_OP, CONCAT, OTHER }

    /** How often a loop runs. {@code why} is null for a loop bounded by a constant, which costs nothing extra. */
    private record Factor(Cx cx, String why, boolean uncertain) {
    }

    private record LoopInfo(Factor factor, String once, String perIteration) {
    }

    private record Op(Tag tag, Cx cx, String why) {
    }

    private record Finding(String kind, int line, String snippet) {
    }

    /** A growth rate together with the steps that lead to it, outermost first. */
    private record Cost(Cx cx, List<String> why) {

        static final Cost ONE = new Cost(Cx.ONE, List.of());

        Cost max(Cost other) {
            return cx.compareTo(other.cx) >= 0 ? this : other;
        }

        Cost times(Factor f) {
            if (f.cx().isConstant() && f.why() == null) {
                return this;
            }
            List<String> steps = new ArrayList<>();
            if (f.why() != null) {
                steps.add(f.why());
            }
            steps.addAll(why);
            return new Cost(cx.times(f.cx()), steps);
        }
    }

    private static final class Ctx {
        static final Ctx EMPTY = new Ctx(List.of(), 0);

        final List<Factor> loops;
        /** How many enclosing loops run about n times or more. */
        final int linear;

        Ctx(List<Factor> loops, int linear) {
            this.loops = loops;
            this.linear = linear;
        }

        Ctx push(Factor f) {
            List<Factor> next = new ArrayList<>(loops);
            next.add(f);
            return new Ctx(next, linear + (f.cx().compareTo(Cx.N) >= 0 ? 1 : 0));
        }

        Cx product() {
            Cx cx = Cx.ONE;
            for (Factor f : loops) {
                cx = cx.times(f.cx());
            }
            return cx;
        }
    }

    private static final class Fn {
        final String name;
        final Block block;
        Cost time;
        Cost space;
        boolean timeBusy;
        boolean spaceBusy;
        boolean mutual;
        boolean memo;
        final List<String> selfArgs = new ArrayList<>();
        final Set<String> calls = new LinkedHashSet<>();

        Fn(String name, Block block) {
            this.name = name;
            this.block = block;
        }
    }

    // ------------------------------------------------------------------ patterns

    private static final Pattern PY_FN = Pattern.compile("^(?:async\\s+)?def\\s+(\\w+)");
    private static final Pattern C_FN = Pattern.compile(
            "^(?!(?:if|for|while|switch|catch|else|do|try|synchronized|foreach|lock|using|new|return)\\b)"
                    + "[^=]*?\\b(\\w+)\\s*\\([^()]*\\)\\s*(?:const|noexcept|override|final|throws\\s+[\\w.,\\s]+)*\\s*$");
    private static final Set<String> NOT_FUNCTIONS = Set.of("if", "for", "while", "switch", "catch", "return", "sizeof",
            "synchronized", "lock", "using", "foreach", "else", "do", "try", "new");

    private static final Pattern CALL = Pattern.compile("\\b([A-Za-z_]\\w*)\\s*\\(");
    private static final Pattern SORT = Pattern.compile(
            "\\b(sort|sorted|stable_sort|qsort|Sort|OrderBy|OrderByDescending)\\s*\\(");
    private static final Pattern LIB_JAVA = Pattern.compile(
            "\\b(?:Arrays|Collections)\\s*\\.\\s*(?:fill|copyOf|copyOfRange|equals|stream|max|min|reverse|shuffle|frequency)\\s*\\("
                    + "|\\.\\s*(?:stream|toCharArray|substring|addAll|clone|split|toArray|ToArray|ToList|Substring|Split|Reverse|"
                    + "Sum|Max|Min|Select|Where|reverse|containsAll|removeAll|retainAll)\\s*\\("
                    + "|\\bString\\s*\\.\\s*join\\s*\\(|\\bstring\\s*\\.\\s*Join\\s*\\(");
    private static final Pattern LIB_C = Pattern.compile(
            "(?<![.>\\w])(?:std\\s*::\\s*)?(strlen|strcpy|strncpy|strcmp|strcat|memset|memcpy|reverse|accumulate|fill|copy|"
                    + "max_element|min_element|count|find|equal|iota|unique|rotate)\\s*\\(|\\.\\s*substr\\s*\\(");
    private static final Pattern LIB_PY = Pattern.compile(
            "\\b(?:sum|any|all|list|set|tuple|reversed|dict)\\s*\\(\\s*[^)\\s]"
                    + "|\\.\\s*(?:join|split|reverse|copy|splitlines|strip|replace)\\s*\\(|\\[[^\\[\\]]*:[^\\[\\]]*\\]");
    private static final Pattern PY_MAXMIN = Pattern.compile("\\b(?:max|min)\\s*\\(");
    private static final Pattern METHOD = Pattern.compile("\\b(\\w+)\\s*\\.\\s*(\\w+)\\s*\\(");
    private static final Pattern PY_IN = Pattern.compile("\\bin\\s+(\\w+)\\b");
    private static final Pattern PY_GEN = Pattern.compile("\\bfor\\s+[\\w\\s,()*]+?\\s+in\\s+");
    private static final Pattern MEMO = Pattern.compile(
            "\\b(memo|memoize|memoized|cache|lru_cache|dp|computed|visited|seen)\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern GROW = Pattern.compile(
            "\\b(\\w+)\\s*\\.\\s*(?:add|addLast|addFirst|offer|offerLast|push|put|putIfAbsent|append|appendleft|extend|"
                    + "push_back|emplace_back|emplace|enqueue|Enqueue|Add|Push|Append|AddRange|setdefault)\\s*\\(");
    private static final Pattern INDEXED_WRITE = Pattern.compile(
            "\\b(\\w+)\\s*\\[[^\\]]+\\]\\s*(?:\\+\\+|--|[-+*]?=(?!=))");
    private static final Pattern NEW_ARRAY = Pattern.compile("\\bnew\\s+[\\w.]+\\s*(?:<[^()]*>)?\\s*((?:\\[[^\\[\\]]*\\])+)");
    private static final Pattern FIXED_ARRAY = Pattern.compile(
            "\\b(?!return\\b|else\\b|case\\b|throw\\b|new\\b|delete\\b|goto\\b)\\w+\\s*[*&]?\\s+\\w+\\s*((?:\\[[^\\[\\]]+\\])+)\\s*(?:;|=|,|$)");
    private static final Pattern VECTOR_CTOR = Pattern.compile("\\bvector\\s*<[^;()]*>\\s*&?\\s*\\w+\\s*\\(([^;]*)\\)\\s*(?:;|$)");
    private static final Pattern MALLOC = Pattern.compile("\\b(?:malloc|calloc|realloc)\\s*\\(([^;]*)\\)");
    private static final Pattern COPY_ALLOC = Pattern.compile(
            "\\.\\s*(?:toCharArray|clone|toArray|ToArray|ToList|split|Split|copy)\\s*\\(\\s*\\)|\\bArrays\\s*\\.\\s*copyOf"
                    + "|\\bnew\\s+(?:ArrayList|HashMap|HashSet|LinkedList|ArrayDeque|TreeMap|TreeSet|PriorityQueue|Stack|Vector)"
                    + "\\s*<[^()]*>\\s*\\(\\s*[^)\\s]"
                    + "|\\b(?:sorted|list|set|tuple|dict|reversed)\\s*\\(\\s*[^)\\s]|\\[\\s*:\\s*\\]");
    private static final Pattern PY_REPEAT = Pattern.compile("\\[[^\\[\\]]*\\]\\s*\\*\\s*([\\w.()+\\-*/ ]+?)(?=\\s*(?:for\\b|[,\\]\\)]|$))");
    private static final Pattern PY_REPEAT_LEFT = Pattern.compile("(\\w+)\\s*\\*\\s*\\[[^\\[\\]]*\\]");

    // ------------------------------------------------------------------ one analysis

    private static final class Run {
        final String lang;
        final boolean python;
        final String source;
        final Map<String, Fn> fns = new LinkedHashMap<>();
        final Map<String, Kind> kinds = new HashMap<>();
        final Map<Block, LoopInfo> loopInfos = new IdentityHashMap<>();
        final List<Finding> findings = new ArrayList<>();
        final Set<String> notes = new LinkedHashSet<>();
        boolean uncertain;
        boolean usedRecursion;
        boolean usedLibrary;
        boolean exponentialWithoutMemo;
        String exponentialFn;

        Run(String lang, String source) {
            this.lang = lang;
            this.python = lang.equals("PYTHON");
            this.source = source;
        }

        AnalysisResponse execute() {
            String cleaned = SourceCleaner.clean(source, python);
            Block root = python ? Blocks.parsePython(cleaned) : Blocks.parseBraces(cleaned);
            collectFns(root);
            if (python) {
                fns.put("<module>", new Fn("<module>", root));
            } else if (fns.isEmpty()) {
                fns.put("<snippet>", new Fn("<snippet>", root));
            }
            collectKinds(cleaned);
            for (Fn fn : fns.values()) {
                fn.memo = MEMO.matcher(fn.block.decorators + " " + fn.block.allText()).find();
                for (String called : calledNames(gather(fn.block))) {
                    if (fns.containsKey(called)) {
                        fn.calls.add(called);
                    }
                }
            }

            Set<String> called = new HashSet<>();
            for (Fn fn : fns.values()) {
                for (String c : fn.calls) {
                    if (!c.equals(fn.name)) {
                        called.add(c);
                    }
                }
            }
            Cost time = Cost.ONE;
            Cost space = Cost.ONE;
            for (Fn fn : fns.values()) {
                if (!called.contains(fn.name)) {
                    time = time.max(timeOf(fn));
                    space = space.max(spaceOf(fn));
                }
            }
            return respond(time, space);
        }

        // -------------------------------------------------------------- functions

        void collectFns(Block block) {
            for (Item item : block.items) {
                if (item instanceof Block child) {
                    String name = fnName(child);
                    if (name != null && !fns.containsKey(name)) {
                        fns.put(name, new Fn(name, child));
                    }
                    collectFns(child);
                }
            }
        }

        String fnName(Block b) {
            Matcher m = python ? PY_FN.matcher(b.header) : C_FN.matcher(b.header);
            if (!(python ? m.find() : m.matches())) {
                return null;
            }
            String name = m.group(1);
            return NOT_FUNCTIONS.contains(name) ? null : name;
        }

        boolean isFn(Block b) {
            return fnName(b) != null;
        }

        /** The text of a function's own statements, leaving out nested functions. */
        String gather(Block b) {
            StringBuilder sb = new StringBuilder();
            gather(b, sb);
            return sb.toString();
        }

        void gather(Block b, StringBuilder sb) {
            for (Item item : b.items) {
                if (item instanceof Stmt s) {
                    sb.append(s.text()).append('\n');
                } else if (item instanceof Block child && !isFn(child)) {
                    sb.append(child.header).append('\n');
                    gather(child, sb);
                }
            }
        }

        List<String> calledNames(String text) {
            List<String> names = new ArrayList<>();
            Matcher m = CALL.matcher(text);
            while (m.find()) {
                names.add(m.group(1));
            }
            return names;
        }

        // -------------------------------------------------------------- variable kinds

        void collectKinds(String s) {
            if (python) {
                kind(s, "\\b(\\w+)\\s*(?::[^=\\n]+)?=\\s*(?:\\[|list\\(|deque\\()", Kind.LIST);
                kind(s, "\\b(\\w+)\\s*=\\s*set\\(", Kind.SET);
                kind(s, "\\b(\\w+)\\s*=\\s*\\{[^}:]*\\}", Kind.SET);
                kind(s, "\\b(\\w+)\\s*=\\s*(?:\\{\\s*\\}|dict\\(|defaultdict\\(|Counter\\(|\\{[^}]*:[^}]*\\})", Kind.MAP);
                kind(s, "\\b(\\w+)\\s*=\\s*[\"']", Kind.STRING);
                kind(s, "\\b(\\w+)\\s*:\\s*(?:List|list)\\b", Kind.LIST);
                kind(s, "\\b(\\w+)\\s*:\\s*(?:Set|set)\\b", Kind.SET);
                kind(s, "\\b(\\w+)\\s*:\\s*(?:Dict|dict)\\b", Kind.MAP);
                kind(s, "\\b(\\w+)\\s*:\\s*str\\b", Kind.STRING);
                return;
            }
            String gen = "<[^;=()]*>";
            kind(s, "\\b(?:List|ArrayList|LinkedList|Vector|Deque|ArrayDeque|Stack|Queue|PriorityQueue|IList|Collection|"
                    + "vector|deque|list|stack|queue)\\s*" + gen + "\\s*&?\\s*(\\w+)", Kind.LIST);
            kind(s, "\\b(?:Set|HashSet|TreeSet|LinkedHashSet|SortedSet|ISet|set|unordered_set|multiset)\\s*" + gen
                    + "\\s*&?\\s*(\\w+)", Kind.SET);
            kind(s, "\\b(?:Map|HashMap|TreeMap|LinkedHashMap|Dictionary|IDictionary|SortedMap|map|unordered_map|multimap)\\s*"
                    + gen + "\\s*&?\\s*(\\w+)", Kind.MAP);
            kind(s, "\\b(?:String|string)\\s*&?\\s*(\\w+)", Kind.STRING);
            kind(s, "\\bStringBuilder\\s+(\\w+)", Kind.SB);
            kind(s, "\\b(?:int|long|char|double|float|boolean|byte|short|bool|unsigned)\\s*(?:\\[\\s*\\]\\s*)+(\\w+)", Kind.ARRAY);
            kind(s, "\\b(?:int|long|char|double|float|boolean|byte|short|bool)\\s+(\\w+)\\s*\\[", Kind.ARRAY);
        }

        void kind(String s, String regex, Kind kind) {
            Matcher m = Pattern.compile(regex).matcher(s);
            while (m.find()) {
                kinds.put(m.group(1), kind);
            }
        }

        // -------------------------------------------------------------- loops

        LoopInfo loopInfo(Block b) {
            return loopInfos.computeIfAbsent(b, this::computeLoopInfo);
        }

        boolean isLoop(Block b) {
            return b.header.matches("^(?:for|while|do|foreach)\\b.*") || (b.header.equals("do"));
        }

        LoopInfo computeLoopInfo(Block b) {
            String h = b.header;
            String where = "line " + b.line + ": `" + Text.shorten(h, 60) + "`";
            if (h.startsWith("do") && !h.startsWith("double")) {
                return new LoopInfo(whileFactor(b, "", where), "", "");
            }
            if (h.startsWith("while")) {
                String cond = python ? h.substring(5).strip() : parenContent(h);
                return new LoopInfo(whileFactor(b, cond, where), "", cond);
            }
            if (python) {
                String iter = h.replaceFirst("^for\\s+[\\w\\s,()*]+?\\s+in\\s+", "");
                return new LoopInfo(iterableFactor(iter, where), iter, "");
            }
            String inner = parenContent(h);
            if (h.startsWith("foreach")) {
                String iter = inner.replaceFirst("^.*?\\bin\\b", "");
                return new LoopInfo(iterableFactor(iter, where), iter, "");
            }
            List<String> parts = Text.splitTop(inner, ';');
            if (parts.size() < 3) {
                int colon = Text.topLevelColon(inner);
                String iter = colon > 0 ? inner.substring(colon + 1) : inner;
                return new LoopInfo(iterableFactor(iter, where), iter, "");
            }
            return new LoopInfo(cForFactor(parts.get(0), parts.get(1), parts.get(2), where), parts.get(0),
                    parts.get(1) + ";" + parts.get(2));
        }

        String parenContent(String h) {
            int open = h.indexOf('(');
            if (open < 0) {
                return "";
            }
            String inner = Text.argsAfter(h, open);
            return inner == null ? "" : inner;
        }

        Factor cForFactor(String init, String cond, String update, String where) {
            if (cond.isBlank()) {
                uncertain = true;
                return new Factor(Cx.N, where + " has no end condition, so it is assumed to run about n times", true);
            }
            String upd = update.replaceAll("\\s+", " ");
            boolean halving = Pattern.compile("(?:\\*=|/=|<<=|>>=|%=)|=\\s*\\w+\\s*(?:\\*|/|<<|>>)\\s*\\w").matcher(upd).find();
            Matcher var = Pattern.compile("(?:\\w+\\s+)*(\\w+)\\s*=\\s*([^;,]+)").matcher(init);
            String loopVar = null;
            String start = "";
            if (var.find()) {
                loopVar = var.group(1);
                start = var.group(2);
            }
            boolean constantBound = false;
            for (String conjunct : cond.split("&&|\\band\\b")) {
                Matcher cmp = Pattern.compile("^(.*?)(<=|>=|!=|<|>)(.*)$").matcher(conjunct.strip());
                if (cmp.find()) {
                    String left = cmp.group(1).strip();
                    String right = cmp.group(3).strip();
                    boolean varOnLeft = loopVar != null && left.matches(".*\\b" + Pattern.quote(loopVar) + "\\b.*");
                    String bound = varOnLeft ? right : left;
                    if (Text.isConstant(bound) && (start.isBlank() || Text.isConstant(start))) {
                        constantBound = true;
                    }
                    if (loopVar != null && cond.matches(".*\\b" + Pattern.quote(loopVar) + "\\s*\\*\\s*" + Pattern.quote(loopVar)
                            + ".*") || cond.contains("sqrt")) {
                        return new Factor(Cx.SQRT, where + " stops at the square root of n, so it runs about \u221An times", false);
                    }
                }
            }
            if (constantBound) {
                return new Factor(Cx.ONE, null, false);
            }
            if (halving) {
                return new Factor(Cx.LOG, where + " multiplies or divides its counter each step, so it runs about log n times", false);
            }
            return new Factor(Cx.N, where + " runs about n times", false);
        }

        Factor whileFactor(Block b, String cond, String where) {
            String body = b.allText();
            Set<String> condVars = new HashSet<>();
            Matcher id = Pattern.compile("[A-Za-z_]\\w*").matcher(cond);
            while (id.find()) {
                condVars.add(id.group());
            }
            if (cond.isBlank()) {
                // A do-while: its condition was dropped by the parser, so look at every variable assigned in the body.
                Matcher assigned = Pattern.compile("\\b([A-Za-z_]\\w*)\\s*(?:[-+*/%]|<<|>>)?=(?!=)").matcher(body);
                while (assigned.find()) {
                    condVars.add(assigned.group(1));
                }
            }
            if (Pattern.compile("(\\w+)\\s*\\*\\s*\\1\\s*(?:<|<=)").matcher(cond).find() || cond.contains("sqrt")) {
                return new Factor(Cx.SQRT, where + " stops at the square root of n, so it runs about \u221An times", false);
            }
            // Variables set from a "divide by two" expression (a midpoint), for binary search.
            Set<String> midpoints = new HashSet<>();
            Matcher mid = Pattern.compile("\\b([A-Za-z_]\\w*)\\s*=\\s*[^;=]*(?://\\s*2|/\\s*2|>>\\s*1)").matcher(body);
            while (mid.find()) {
                midpoints.add(mid.group(1));
            }
            boolean halving = false;
            for (String v : condVars) {
                String q = Pattern.quote(v);
                if (Pattern.compile("\\b" + q + "\\s*(?://=|/=|\\*=|>>=|<<=|%=)").matcher(body).find()
                        || Pattern.compile("\\b" + q + "\\s*=\\s*[^;=]*(?://|/|>>|<<|%|\\*)\\s*\\w").matcher(body).find()) {
                    halving = true;
                }
                for (String m : midpoints) {
                    if (!m.equals(v) && Pattern.compile("\\b" + q + "\\s*=\\s*[^;=]*\\b" + Pattern.quote(m) + "\\b").matcher(body).find()) {
                        halving = true;
                    }
                }
            }
            if (halving) {
                return new Factor(Cx.LOG, where + " halves (or multiplies) its range each step, so it runs about log n times", false);
            }
            boolean endless = cond.matches("\\s*(?:true|1|True|)\\s*");
            boolean progresses = false;
            for (String v : condVars) {
                if (Pattern.compile("\\b" + Pattern.quote(v) + "\\s*(?:\\+\\+|--|[-+*/]=|=(?!=))|(?:\\+\\+|--)\\s*" + Pattern.quote(v)).matcher(body).find()) {
                    progresses = true;
                    break;
                }
            }
            boolean reads = cond.matches(".*(?:hasNext|hasNextInt|read|eof|EOF|!= *-1|input|getline|cin).*");
            if (endless || (!progresses && !reads)) {
                uncertain = true;
                return new Factor(Cx.N, where + " has no clear end, so it is assumed to run about n times", true);
            }
            return new Factor(Cx.N, where + " runs about n times", false);
        }

        Factor iterableFactor(String expr, String where) {
            String e = expr.strip();
            if (e.startsWith("range(") || e.startsWith("xrange(")) {
                String args = Text.argsAfter(e, e.indexOf('('));
                if (args != null) {
                    if (e.contains("sqrt") || e.contains("**0.5") || e.contains("** 0.5") || e.contains("isqrt")) {
                        return new Factor(Cx.SQRT, where + " stops at the square root of n, so it runs about \u221An times", false);
                    }
                    boolean allConst = true;
                    for (String a : Text.splitTop(args, ',')) {
                        allConst &= Text.isConstant(a);
                    }
                    if (allConst) {
                        return new Factor(Cx.ONE, null, false);
                    }
                }
                return new Factor(Cx.N, where + " runs about n times", false);
            }
            if (e.matches("^[\"'][^\"']*[\"']$") || e.matches("^[\\[(][\\d\\s,.\"']*[\\])]$")) {
                return new Factor(Cx.ONE, null, false);
            }
            return new Factor(Cx.N, where + " goes over every element, about n times", false);
        }

        // -------------------------------------------------------------- time

        Cost timeOf(Fn fn) {
            if (fn.time != null) {
                return fn.time;
            }
            if (fn.timeBusy) {
                fn.mutual = true;
                return Cost.ONE;
            }
            fn.timeBusy = true;
            Cost body = blockCost(fn.block, Ctx.EMPTY, fn);
            fn.time = recursiveTime(fn, body);
            fn.timeBusy = false;
            return fn.time;
        }

        Cost blockCost(Block block, Ctx ctx, Fn fn) {
            Cost best = Cost.ONE;
            for (Item item : block.items) {
                if (item instanceof Stmt s) {
                    best = best.max(stmtCost(s.text(), s.line(), ctx, fn));
                } else if (item instanceof Block child) {
                    if (isFn(child)) {
                        continue;
                    }
                    if (isLoop(child)) {
                        LoopInfo info = loopInfo(child);
                        Cost once = info.once().isBlank() ? Cost.ONE : stmtCost(info.once(), child.line, ctx, fn);
                        Ctx inner = ctx.push(info.factor());
                        Cost perIteration = info.perIteration().isBlank() ? Cost.ONE
                                : stmtCost(info.perIteration(), child.line, inner, fn);
                        Cost body = blockCost(child, inner, fn).max(perIteration);
                        best = best.max(once).max(body.times(info.factor()));
                    } else {
                        Cost header = stmtCost(child.header, child.line, ctx, fn);
                        best = best.max(header).max(blockCost(child, ctx, fn));
                    }
                }
            }
            return best;
        }

        Cost stmtCost(String text, int line, Ctx ctx, Fn fn) {
            if (text == null || text.isBlank()) {
                return Cost.ONE;
            }
            String t = text;
            Cx generated = Cx.ONE;
            List<String> generatorSteps = new ArrayList<>();
            if (python) {
                for (Factor g : generators(t, line)) {
                    generated = generated.times(g.cx());
                    if (g.why() != null) {
                        generatorSteps.add(g.why());
                    }
                }
                t = PY_GEN.matcher(t).replaceAll("for_ ");
            }

            Cost best = Cost.ONE;
            for (Op op : detectOps(t, line, ctx)) {
                if (op.cx().compareTo(best.cx()) > 0) {
                    best = new Cost(op.cx(), List.of(op.why()));
                }
                if (op.tag() == Tag.OTHER && !op.cx().isConstant()) {
                    usedLibrary = true;
                }
            }
            Cx made = allocation(t);
            if (made.compareTo(best.cx()) > 0) {
                best = new Cost(made, List.of("line " + line + ": `" + Text.shorten(window(t, 0), 50)
                        + "` creates storage that grows with the input, and setting it up takes about "
                        + made.label().replace("O(", "").replace(")", "") + " steps"));
            }
            collect(t, line, ctx, best);

            for (String name : calledNames(t)) {
                if (name.equals(fn.name)) {
                    for (String args : argsOfCalls(t, name)) {
                        fn.selfArgs.add(args);
                    }
                } else if (fns.containsKey(name)) {
                    best = best.max(timeOf(fns.get(name)));
                }
            }

            if (!generated.isConstant()) {
                List<String> steps = new ArrayList<>(generatorSteps);
                steps.addAll(best.why());
                return new Cost(generated.times(best.cx()), steps);
            }
            return best;
        }

        List<Factor> generators(String t, int line) {
            List<Factor> out = new ArrayList<>();
            Matcher m = PY_GEN.matcher(t);
            while (m.find()) {
                int depth = 0;
                int end = m.end();
                int i = end;
                for (; i < t.length(); i++) {
                    char c = t.charAt(i);
                    if (c == '(' || c == '[' || c == '{') {
                        depth++;
                    } else if (c == ')' || c == ']' || c == '}') {
                        if (depth == 0) {
                            break;
                        }
                        depth--;
                    } else if (depth == 0 && (c == ',' || t.startsWith(" if ", i) || t.startsWith(" for ", i)
                            || t.startsWith(" else ", i))) {
                        break;
                    }
                }
                String where = "line " + line + ": the comprehension `" + Text.shorten(t.substring(m.start(), i), 50) + "`";
                out.add(iterableFactor(t.substring(end, i), where));
            }
            return out;
        }

        List<String> argsOfCalls(String text, String name) {
            List<String> out = new ArrayList<>();
            Matcher m = Pattern.compile("\\b" + Pattern.quote(name) + "\\s*\\(").matcher(text);
            while (m.find()) {
                String args = Text.argsAfter(text, m.end() - 1);
                if (args != null) {
                    out.add(args);
                }
            }
            return out;
        }

        /** Every costly operation in one statement, with what it costs. */
        List<Op> detectOps(String t, int line, Ctx ctx) {
            List<Op> ops = new ArrayList<>();
            String at = "line " + line + ": ";

            Matcher sort = SORT.matcher(t);
            while (sort.find()) {
                // The student's own function called "sort" is analysed like any other function, not assumed to be n log n.
                if (!fns.containsKey(sort.group(1))) {
                    ops.add(new Op(Tag.SORT, Cx.N_LOG, at + "`" + Text.shorten(window(t, sort.start()), 50)
                            + "` sorts the data, which takes about n log n steps"));
                    break;
                }
            }

            Matcher lib = (python ? LIB_PY : lang.equals("C") || lang.equals("CPP") ? LIB_C : LIB_JAVA).matcher(t);
            while (lib.find()) {
                // A function the student wrote with a library-like name (a union-find "find", say) is not a library scan.
                boolean userFn = lib.groupCount() >= 1 && lib.group(1) != null && fns.containsKey(lib.group(1));
                if (!userFn) {
                    ops.add(new Op(Tag.OTHER, Cx.N, at + "`" + Text.shorten(window(t, lib.start()), 50)
                            + "` reads or copies the whole collection, about n steps"));
                    break;
                }
            }
            if (python) {
                Matcher mm = PY_MAXMIN.matcher(t);
                while (mm.find()) {
                    String args = Text.argsAfter(t, mm.end() - 1);
                    // max(a, b) compares two values; max(items) scans a collection.
                    if (args != null && !args.isBlank() && Text.splitTop(args, ',').size() == 1) {
                        ops.add(new Op(Tag.OTHER, Cx.N, at + "`" + Text.shorten(window(t, mm.start()), 50)
                                + "` looks at every item of the collection, about n steps"));
                        break;
                    }
                }
            }

            Matcher m = METHOD.matcher(t);
            while (m.find()) {
                Kind kind = kinds.get(m.group(1));
                if (kind == null) {
                    continue;
                }
                String method = m.group(2);
                String args = Text.argsAfter(t, m.end() - 1);
                String arg = args == null ? "" : args.strip();
                boolean linear = switch (kind) {
                    case LIST -> Set.of("contains", "indexOf", "lastIndexOf", "Contains", "IndexOf", "index", "count",
                            "insert", "erase", "removeAll", "retainAll", "containsAll", "Remove").contains(method)
                            || (method.equals("remove") && !arg.isEmpty())
                            || (method.equals("pop") && arg.equals("0"))
                            || (method.equals("add") && arg.startsWith("0,"));
                    case STRING -> Set.of("contains", "indexOf", "lastIndexOf", "find", "replace", "replaceAll", "split",
                            "substring", "Substring", "Contains", "IndexOf", "Replace", "Split", "count", "index",
                            "concat").contains(method);
                    case MAP -> method.equals("containsValue");
                    case SB -> Set.of("insert", "reverse", "indexOf").contains(method);
                    default -> false;
                };
                if (linear) {
                    boolean front = (method.equals("pop") && arg.equals("0")) || (method.equals("add") && arg.startsWith("0,"))
                            || (method.equals("insert") && arg.startsWith("0")) || (method.equals("remove") && arg.equals("0"));
                    String what = kind == Kind.STRING ? "string" : "list";
                    ops.add(new Op(front ? Tag.FRONT_OP : kind == Kind.LIST ? Tag.LIST_LOOKUP : Tag.OTHER, Cx.N,
                            at + "`" + Text.shorten(window(t, m.start()), 50) + "` scans the whole " + what
                                    + " each time, about n steps"));
                }
            }

            if (python) {
                Matcher in = PY_IN.matcher(t);
                while (in.find()) {
                    Kind kind = kinds.get(in.group(1));
                    if (kind == Kind.LIST || kind == Kind.STRING) {
                        ops.add(new Op(kind == Kind.LIST ? Tag.LIST_LOOKUP : Tag.OTHER, Cx.N,
                                at + "`in " + in.group(1) + "` searches the whole " + (kind == Kind.LIST ? "list" : "string")
                                        + " each time, about n steps"));
                    }
                }
            } else if (lang.equals("JAVA") || lang.equals("CSHARP")) {
                Matcher concat = Pattern.compile("\\b(\\w+)\\s*\\+=|\\b(\\w+)\\s*=\\s*\\2\\s*\\+").matcher(t);
                while (concat.find()) {
                    String v = concat.group(1) != null ? concat.group(1) : concat.group(2);
                    if (kinds.get(v) == Kind.STRING && ctx.linear >= 1) {
                        ops.add(new Op(Tag.CONCAT, Cx.N, at + "`" + Text.shorten(window(t, concat.start()), 40)
                                + "` builds a new string each time, copying it, about n steps"));
                    }
                }
            }
            return ops;
        }

        String window(String t, int from) {
            int end = Math.min(t.length(), from + 60);
            return t.substring(Math.max(0, from), end);
        }

        // -------------------------------------------------------------- recursion

        Cost recursiveTime(Fn fn, Cost body) {
            List<String> calls = fn.selfArgs;
            Cx w = body.cx();
            if (calls.isEmpty()) {
                if (fn.mutual) {
                    usedRecursion = true;
                    uncertain = true;
                    return new Cost(w.times(Cx.N), prepend("`" + fn.name + "` and another function call each other, "
                            + "so the depth is assumed to be about n", body));
                }
                return body;
            }
            usedRecursion = true;
            // Calls in different branches of an if/else run one at a time, so count them along a single path.
            int count = Math.max(1, callsPerPath(fn.block, fn.name));
            Shape shape = shapeOf(calls);
            String me = "`" + fn.name + "`";
            String callsWord = count == 1 ? "once" : count == 2 ? "twice" : count + " times";
            switch (shape) {
                case DIV -> {
                    if (count >= 2) {
                        Cx total = w.compareTo(Cx.N) >= 0 ? w.times(Cx.LOG) : Cx.N;
                        return new Cost(total, prepend(me + " calls itself " + callsWord + " on halves of the input"
                                + (w.compareTo(Cx.N) >= 0 ? ", with about n work to combine them at each level" : "")
                                + ", so the input is split in a tree", body));
                    }
                    Cx total = w.isConstant() ? Cx.LOG : w;
                    return new Cost(total, prepend(me + " calls itself once on half of the input, so there are about "
                            + "log n calls", body));
                }
                case TRAV -> {
                    return new Cost(w.isConstant() ? Cx.N : w.times(Cx.N),
                            prepend(me + " visits each node (it follows the structure's links), about n calls", body));
                }
                case SUB -> {
                    if (count == 1 || fn.memo) {
                        return new Cost(w.isConstant() ? Cx.N : w.times(Cx.N), prepend(me + " calls itself with a slightly "
                                + "smaller input" + (count > 1 ? " but remembers its results (memoisation)" : "")
                                + ", about n calls", body));
                    }
                    exponentialWithoutMemo = true;
                    exponentialFn = fn.name;
                    return new Cost(Cx.EXP, prepend(me + " calls itself " + callsWord + " with a slightly smaller input and "
                            + "does not remember results, so the calls roughly double at each level", body));
                }
                default -> {
                    uncertain = true;
                    if (count == 1) {
                        return new Cost(w.isConstant() ? Cx.N : w.times(Cx.N), prepend(me + " calls itself once; the depth "
                                + "is assumed to be about n", body));
                    }
                    return new Cost(w.compareTo(Cx.N) >= 0 ? w : Cx.N, prepend(me + " calls itself " + callsWord
                            + "; each call is assumed to handle its own share of the input", body));
                }
            }
        }

        /**
         * The most times the function can call itself in one run of its body. The branches of an if / else-if / else
         * are alternatives (the largest counts), statements one after another add up, and a call inside a loop is
         * counted twice because the loop repeats it.
         */
        int callsPerPath(Block block, String name) {
            Pattern self = Pattern.compile("\\b" + Pattern.quote(name) + "\\s*\\(");
            int total = 0;
            int group = 0;
            for (Item item : block.items) {
                String lead = item instanceof Stmt s ? s.text() : ((Block) item).header;
                if (item instanceof Block b && isFn(b)) {
                    continue;
                }
                int here = 0;
                Matcher m = self.matcher(item instanceof Stmt s ? s.text() : ((Block) item).header);
                while (m.find()) {
                    here++;
                }
                if (item instanceof Block child) {
                    int inside = callsPerPath(child, name);
                    here += isLoop(child) && inside > 0 ? inside * 2 : inside;
                }
                boolean alternative = lead.matches("^(?:else|elif)\\b.*");
                boolean starts = lead.matches("^if\\b.*") || lead.matches("^(?:case|default)\\b.*");
                if (alternative) {
                    group = Math.max(group, here);
                } else {
                    total += group;
                    group = starts ? here : 0;
                    if (!starts) {
                        total += here;
                    }
                }
            }
            return total + group;
        }

        List<String> prepend(String step, Cost body) {
            List<String> steps = new ArrayList<>();
            steps.add(step);
            steps.addAll(body.why());
            return steps;
        }

        private enum Shape { DIV, TRAV, SUB, OTHER }

        Shape shapeOf(List<String> calls) {
            boolean div = false;
            boolean trav = false;
            boolean sub = false;
            for (String a : calls) {
                if (Pattern.compile("(?://|/|>>)|\\b(?:mid|middle|m|pivot|half)\\b").matcher(a).find()) {
                    div = true;
                } else if (Pattern.compile("(?:\\.|->)\\s*(?:left|right|next|children|child|Left|Right|Next)\\b|\\b(?:node|root)\\b")
                        .matcher(a).find()) {
                    trav = true;
                } else if (Pattern.compile("\\w\\s*-\\s*\\w|\\w\\s*\\+\\s*1\\b").matcher(a).find()) {
                    sub = true;
                }
            }
            return div ? Shape.DIV : trav ? Shape.TRAV : sub ? Shape.SUB : Shape.OTHER;
        }

        // -------------------------------------------------------------- space

        Cost spaceOf(Fn fn) {
            if (fn.space != null) {
                return fn.space;
            }
            timeOf(fn);
            if (fn.spaceBusy) {
                return Cost.ONE;
            }
            fn.spaceBusy = true;
            Cost body = spaceBlock(fn.block, Ctx.EMPTY, fn);
            Cost result = body;
            if (!fn.selfArgs.isEmpty() || fn.mutual) {
                Shape shape = shapeOf(fn.selfArgs);
                boolean shallow = !fn.selfArgs.isEmpty() && shape == Shape.DIV;
                Cost stack = shallow
                        ? new Cost(Cx.LOG, List.of("the recursion of `" + fn.name + "` goes about log n calls deep, and each "
                                + "call keeps its variables on the stack"))
                        : new Cost(Cx.N, List.of("the recursion of `" + fn.name + "` can go about n calls deep, and each call "
                                + "keeps its variables on the stack"));
                result = result.max(stack);
                if (fn.memo && fn.selfArgs.size() > 1) {
                    result = result.max(new Cost(Cx.N, List.of("`" + fn.name + "` stores its results in a table of about n "
                            + "entries (memoisation)")));
                }
            }
            fn.space = result;
            fn.spaceBusy = false;
            return result;
        }

        Cost spaceBlock(Block block, Ctx ctx, Fn fn) {
            Cost best = Cost.ONE;
            for (Item item : block.items) {
                if (item instanceof Stmt s) {
                    best = best.max(stmtSpace(s.text(), s.line(), ctx, fn));
                } else if (item instanceof Block child) {
                    if (isFn(child)) {
                        continue;
                    }
                    if (isLoop(child)) {
                        best = best.max(spaceBlock(child, ctx.push(loopInfo(child).factor()), fn));
                    } else {
                        best = best.max(stmtSpace(child.header, child.line, ctx, fn)).max(spaceBlock(child, ctx, fn));
                    }
                }
            }
            return best;
        }

        Cost stmtSpace(String text, int line, Ctx ctx, Fn fn) {
            if (text == null || text.isBlank()) {
                return Cost.ONE;
            }
            String at = "line " + line + ": ";
            Cost best = Cost.ONE;

            Cx base = Cx.ONE;
            List<String> baseSteps = new ArrayList<>();
            String t = text;
            if (python) {
                int firstFor = text.indexOf(" for ");
                if (firstFor > 0 && (text.lastIndexOf('[', firstFor) >= 0 || text.lastIndexOf('{', firstFor) >= 0)) {
                    for (Factor g : generators(text, line)) {
                        base = base.times(g.cx());
                        if (g.why() != null) {
                            baseSteps.add(g.why());
                        }
                    }
                }
                t = PY_GEN.matcher(text).replaceAll("for_ ");
            }

            Cx alloc = allocation(t);
            if (!alloc.isConstant()) {
                String what = Text.shorten(window(t, 0), 50);
                List<String> steps = new ArrayList<>(baseSteps);
                steps.add(at + "`" + what + "` creates storage that grows with the input, about " + alloc.label());
                best = best.max(new Cost(base.times(alloc), steps));
            } else if (!base.isConstant()) {
                List<String> steps = new ArrayList<>(baseSteps);
                best = best.max(new Cost(base, steps));
            }

            Cx loops = ctx.product();
            if (!loops.isConstant()) {
                Matcher g = GROW.matcher(t);
                while (g.find()) {
                    Kind kind = kinds.get(g.group(1));
                    if (kind == Kind.ARRAY) {
                        continue;
                    }
                    best = best.max(new Cost(loops, List.of(at + "`" + Text.shorten(window(t, g.start()), 40)
                            + "` adds an item on each pass of the loop(s) around it, so the collection grows to about "
                            + loops.label().replace("O(", "").replace(")", "") + " items")));
                }
                Matcher w = INDEXED_WRITE.matcher(t);
                while (w.find()) {
                    if (kinds.get(w.group(1)) == Kind.MAP) {
                        best = best.max(new Cost(loops, List.of(at + "`" + Text.shorten(window(t, w.start()), 40)
                                + "` stores a new key on each pass, so the map grows to about "
                                + loops.label().replace("O(", "").replace(")", "") + " entries")));
                    }
                }
                if (!python && (lang.equals("JAVA") || lang.equals("CSHARP"))) {
                    Matcher c = Pattern.compile("\\b(\\w+)\\s*\\+=").matcher(t);
                    while (c.find()) {
                        if (kinds.get(c.group(1)) == Kind.STRING) {
                            best = best.max(new Cost(loops, List.of(at + "the string `" + c.group(1) + "` grows on each pass "
                                    + "of the loop, to about n characters")));
                        }
                    }
                }
            }

            for (String name : calledNames(t)) {
                if (!name.equals(fn.name) && fns.containsKey(name)) {
                    best = best.max(spaceOf(fns.get(name)));
                }
            }
            return best;
        }

        /** The size of the storage a statement creates (not counting what it adds to existing collections). */
        Cx allocation(String t) {
            Cx best = Cx.ONE;
            Matcher na = NEW_ARRAY.matcher(t);
            while (na.find()) {
                best = best.max(dimensions(na.group(1)));
            }
            if (!python) {
                Matcher fa = FIXED_ARRAY.matcher(t);
                while (fa.find()) {
                    best = best.max(dimensions(fa.group(1)));
                }
                Matcher v = VECTOR_CTOR.matcher(t);
                while (v.find()) {
                    best = best.max(vectorSize(v.group(1)));
                }
                Matcher mal = MALLOC.matcher(t);
                while (mal.find()) {
                    best = best.max(Text.isConstant(mal.group(1)) || !mal.group(1).matches(".*[A-Za-z_].*") ? Cx.ONE : Cx.N);
                }
            }
            if (COPY_ALLOC.matcher(t).find()) {
                best = best.max(Cx.N);
            }
            if (python) {
                Matcher rep = PY_REPEAT.matcher(t);
                while (rep.find()) {
                    best = best.max(Text.isConstant(rep.group(1)) ? Cx.ONE : Cx.N);
                }
                Matcher left = PY_REPEAT_LEFT.matcher(t);
                while (left.find()) {
                    best = best.max(Text.isConstant(left.group(1)) ? Cx.ONE : Cx.N);
                }
            }
            return best;
        }

        Cx dimensions(String dims) {
            Cx cx = Cx.ONE;
            Matcher d = Pattern.compile("\\[([^\\[\\]]*)\\]").matcher(dims);
            while (d.find()) {
                String size = d.group(1);
                if (!size.isBlank() && !Text.isConstant(size)) {
                    cx = cx.times(Cx.N);
                }
            }
            return cx;
        }

        Cx vectorSize(String args) {
            List<String> parts = Text.splitTop(args, ',');
            Cx size = parts.get(0).isBlank() || Text.isConstant(parts.get(0)) ? Cx.ONE : Cx.N;
            if (parts.size() > 1) {
                Matcher inner = VECTOR_CTOR.matcher(parts.get(1));
                Matcher nested = Pattern.compile("vector\\s*<[^()]*>\\s*\\(([^)]*)").matcher(parts.get(1));
                if (nested.find()) {
                    size = size.times(vectorSize(nested.group(1)));
                } else if (inner.find()) {
                    size = size.times(vectorSize(inner.group(1)));
                }
            }
            return size;
        }

        // -------------------------------------------------------------- findings and suggestions

        void collect(String t, int line, Ctx ctx, Cost cost) {
            if (ctx.linear >= 1) {
                for (Op op : detectOps(t, line, ctx)) {
                    switch (op.tag()) {
                        case LIST_LOOKUP -> add("LIST_LOOKUP", line, t);
                        case SORT -> add("SORT_IN_LOOP", line, t);
                        case FRONT_OP -> add("FRONT_OP", line, t);
                        case CONCAT -> add("CONCAT", line, t);
                        default -> { }
                    }
                }
            }
            if (ctx.linear >= 2) {
                String flat = t.replaceAll("\\s+", "");
                if (Pattern.compile("(\\w+)\\[(\\w+)\\][-+](\\w+)\\[(\\w+)\\]==").matcher(flat).find()
                        || pairEquality(flat)) {
                    add("PAIR", line, t);
                }
                if (Pattern.compile("\\b(?:temp|tmp|swap)\\b").matcher(t).find()
                        || Pattern.compile("\\w+\\[[^\\]]+\\],\\w+\\[[^\\]]+\\]=\\w+\\[").matcher(flat).find()) {
                    add("BUBBLE", line, t);
                }
            }
        }

        boolean pairEquality(String flat) {
            Matcher m = Pattern.compile("(\\w+)\\[(\\w+)\\]==(\\w+)\\[(\\w+)\\]").matcher(flat);
            while (m.find()) {
                if (m.group(1).equals(m.group(3)) && !m.group(2).equals(m.group(4))) {
                    return true;
                }
            }
            return false;
        }

        void add(String kind, int line, String snippet) {
            for (Finding f : findings) {
                if (f.kind().equals(kind)) {
                    return;
                }
            }
            findings.add(new Finding(kind, line, Text.shorten(snippet, 70)));
        }

        List<Suggestion> suggest(Cx time, Cx space) {
            List<Suggestion> out = new ArrayList<>();
            for (Finding f : findings) {
                Suggestion s = switch (f.kind()) {
                    case "PAIR" -> better(time, space, Cx.N, space.max(Cx.N), "Look values up instead of searching for them",
                            "Line " + f.line() + " compares pairs of elements with two nested loops. Keep the values you have "
                                    + "already seen in " + hashMap() + " and, for each element, look up the value it needs "
                                    + "in one step. One pass over the input is enough.");
                    case "LIST_LOOKUP" -> better(time, space, time.dropOneN(), space.max(Cx.N),
                            "Use a set or map for the lookups",
                            "Line " + f.line() + " searches a list inside a loop, scanning it each time. Put the items in "
                                    + hashSet() + " or " + hashMap() + " once; a lookup then takes about one step instead of n.");
                    case "SORT_IN_LOOP" -> better(time, space, Cx.N_LOG.max(time.dropOneN()), space,
                            "Sort once, outside the loop",
                            "Line " + f.line() + " sorts inside a loop, repeating the work. If the data does not change between "
                                    + "passes, sort it once before the loop.");
                    case "FRONT_OP" -> better(time, space, time.dropOneN(), space,
                            "Avoid inserting or removing at the front of a list",
                            "Line " + f.line() + " adds or removes at the start of a list, which moves every other item each "
                                    + "time. Use a deque (a queue) or keep an index that moves forward instead.");
                    case "CONCAT" -> better(time, space, time.dropOneN(), space, "Build the string with a builder",
                            "Line " + f.line() + " joins strings with + inside a loop, copying the whole string each time. "
                                    + "Use " + builder() + " and convert to a string once at the end.");
                    case "BUBBLE" -> time.compareTo(Cx.N2) >= 0
                            ? better(time, space, Cx.N_LOG, space, "Use a faster sorting method",
                            "Line " + f.line() + " swaps elements inside nested loops, which looks like bubble or selection "
                                    + "sort. The built-in sort (or merge sort) takes about n log n steps.")
                            : null;
                    default -> null;
                };
                if (s != null) {
                    out.add(s);
                }
            }
            if (exponentialWithoutMemo) {
                out.add(new Suggestion("Remember results you have already computed",
                        "`" + exponentialFn + "` calls itself more than once with smaller inputs and recomputes the same "
                                + "sub-problems. Store each result in a table (memoisation) or build the answer from the "
                                + "bottom up (dynamic programming), so each sub-problem is solved once.",
                        time.label(), space.label(), Cx.N.label(), space.max(Cx.N).label(), model(Cx.N), model(space.max(Cx.N))));
            }
            return out;
        }

        Suggestion better(Cx time, Cx space, Cx betterTime, Cx betterSpace, String title, String explanation) {
            if (betterTime.compareTo(time) >= 0) {
                return null;
            }
            return new Suggestion(title, explanation, time.label(), space.label(), betterTime.label(), betterSpace.label(),
                    model(betterTime), model(betterSpace));
        }

        String hashMap() {
            return switch (lang) {
                case "JAVA" -> "a HashMap";
                case "CSHARP" -> "a Dictionary";
                case "CPP" -> "an unordered_map";
                case "PYTHON" -> "a dict";
                default -> "a hash table";
            };
        }

        String hashSet() {
            return switch (lang) {
                case "JAVA", "CSHARP" -> "a HashSet";
                case "CPP" -> "an unordered_set";
                case "PYTHON" -> "a set";
                default -> "a hash table";
            };
        }

        String builder() {
            return switch (lang) {
                case "JAVA", "CSHARP" -> "a StringBuilder";
                case "PYTHON" -> "a list of parts and ''.join(parts)";
                case "CPP" -> "one std::string and append to it";
                default -> "a character buffer with a write index";
            };
        }

        // -------------------------------------------------------------- the answer

        AnalysisResponse respond(Cost time, Cost space) {
            List<Suggestion> suggestions = suggest(time.cx(), space.cx());
            String confidence = uncertain ? "LOW" : usedRecursion || usedLibrary ? "MEDIUM" : "HIGH";

            String verdict;
            String message;
            if (uncertain && time.cx().compareTo(Cx.N) >= 0 && suggestions.isEmpty()) {
                verdict = "UNKNOWN";
                message = "Some loops or recursion could not be read clearly, so treat this as a rough guide.";
            } else if (!suggestions.isEmpty()) {
                verdict = "COULD_BE_BETTER";
                message = "This works, but there may be a faster way (see the suggestions).";
            } else if (time.cx().compareTo(Cx.N2) >= 0) {
                verdict = "COULD_BE_BETTER";
                message = "The running time grows quickly with the input; check whether a faster approach exists.";
            } else {
                verdict = "EFFICIENT";
                message = "Efficient solution.";
            }

            if (usedRecursion) {
                notes.add("Recursion is estimated from how the function calls itself; the exact depth depends on the data.");
            }
            if (usedLibrary) {
                notes.add("Library calls that scan or copy a collection were counted as about n steps each.");
            }
            if (uncertain) {
                notes.add("At least one loop has no clear end; it was assumed to run about n times.");
            }
            notes.add("Sets and maps are treated as constant-time lookups; sorted trees and heaps (log n) are not counted.");
            notes.add("n means the size of the input. Memory used to hold the input itself is counted only when the code "
                    + "copies it into new storage.");

            return new AnalysisResponse(lang, true, true, confidence,
                    time.cx().label(), model(time.cx()), timeReason(time), time.why(),
                    space.cx().label(), model(space.cx()), spaceReason(space), space.why(),
                    verdict, message, suggestions, new ArrayList<>(notes));
        }

        String timeReason(Cost c) {
            if (c.cx().isConstant()) {
                return "No loop or recursion depends on the size of the input, so the number of steps stays the same "
                        + "however large the input is.";
            }
            List<String> steps = c.why();
            if (steps.isEmpty()) {
                return "The running time grows as " + c.cx().label() + ".";
            }
            StringBuilder sb = new StringBuilder(sentence(steps.get(0)));
            for (int i = 1; i < steps.size(); i++) {
                sb.append(" Inside it, ").append(lowerFirst(sentence(steps.get(i))));
            }
            sb.append(steps.size() > 1 ? " These multiply, giving " : " So the running time grows as ").append(c.cx().label())
                    .append('.');
            return sb.toString();
        }

        String spaceReason(Cost c) {
            if (c.cx().isConstant()) {
                return "Only a fixed number of variables are used, so the extra memory does not grow with the input.";
            }
            List<String> steps = c.why();
            String first = steps.isEmpty() ? "" : sentence(steps.get(0)) + " ";
            return first + "So the extra memory grows as " + c.cx().label() + ".";
        }

        String sentence(String s) {
            String t = s.strip();
            return t.endsWith(".") ? t : t + ".";
        }

        String lowerFirst(String s) {
            return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
        }
    }

    // ------------------------------------------------------------------ answers without an analysis

    private static Model model(Cx cx) {
        return new Model(cx.p2(), cx.log(), cx.exp());
    }

    private static AnalysisResponse unsupported(String lang) {
        return new AnalysisResponse(lang, false, true, "LOW", null, null, null, List.of(), null, null, null, List.of(),
                "UNKNOWN", "Complexity analysis is not available for " + (lang.isEmpty() ? "this language" : lang) + ".",
                List.of(), List.of());
    }

    private static AnalysisResponse unknown(String lang, String message) {
        return new AnalysisResponse(lang, true, true, "LOW", null, null, null, List.of(), null, null, null, List.of(),
                "UNKNOWN", message, List.of(), List.of());
    }
}

package com.itilms.codeexec.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.itilms.codeexec.dto.AnalysisResponse;

class StaticAnalyzerTest {

    private static AnalysisResponse java(String body) {
        return StaticAnalyzer.analyze("JAVA", "public class Main {\n" + body + "\n}");
    }

    private static AnalysisResponse python(String code) {
        return StaticAnalyzer.analyze("PYTHON", code);
    }

    private static AnalysisResponse cpp(String code) {
        return StaticAnalyzer.analyze("CPP", code);
    }

    private static void is(AnalysisResponse r, String time, String space) {
        assertThat(r.timeComplexity()).as("time of this code: " + r.timeReason()).isEqualTo(time);
        assertThat(r.spaceComplexity()).as("space of this code: " + r.spaceReason()).isEqualTo(space);
    }

    // ------------------------------------------------------------------ loops

    @Nested
    @DisplayName("Loops")
    class Loops {

        @Test
        void aStraightLineProgramIsConstant() {
            is(java("public static void main(String[] a) { int x = 1; int y = x + 2; System.out.println(y); }"),
                    "O(1)", "O(1)");
        }

        @Test
        void oneLoopOverTheInputIsLinear() {
            is(java("static int sum(int[] a) { int s = 0; for (int i = 0; i < a.length; i++) { s += a[i]; } return s; }"),
                    "O(n)", "O(1)");
        }

        @Test
        void nestedLoopsMultiply() {
            is(java("static int f(int n) { int c = 0; for (int i = 0; i < n; i++) { for (int j = 0; j < n; j++) { c++; } } return c; }"),
                    "O(n\u00B2)", "O(1)");
            is(java("static int f(int n) { int c = 0; for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) for (int k = 0; k < n; k++) c++; return c; }"),
                    "O(n\u00B3)", "O(1)");
        }

        @Test
        void loopsOneAfterAnotherDoNotMultiply() {
            is(java("static int f(int n) { int c = 0; for (int i = 0; i < n; i++) { c++; } for (int j = 0; j < n; j++) { c++; } return c; }"),
                    "O(n)", "O(1)");
        }

        @Test
        void aLoopWithAFixedBoundIsConstant() {
            is(java("static int f(int n) { int c = 0; for (int i = 0; i < 26; i++) { c += i; } return c; }"), "O(1)", "O(1)");
            is(java("static int f(int n) { int c = 0; for (int i = 0; i < n; i++) { for (int j = 0; j < 10; j++) { c++; } } return c; }"),
                    "O(n)", "O(1)");
        }

        @Test
        void aDoublingLoopIsLogarithmic() {
            is(java("static int f(int n) { int c = 0; for (int i = 1; i < n; i *= 2) { c++; } return c; }"), "O(log n)", "O(1)");
            is(java("static int f(int n) { int c = 0; while (n > 1) { n /= 2; c++; } return c; }"), "O(log n)", "O(1)");
        }

        @Test
        void aLogLoopInsideALinearLoopIsNLogN() {
            is(java("static int f(int n) { int c = 0; for (int i = 0; i < n; i++) { for (int j = 1; j < n; j *= 2) { c++; } } return c; }"),
                    "O(n log n)", "O(1)");
        }

        @Test
        void aLoopUpToTheSquareRootIsSqrtN() {
            is(java("static boolean prime(int n) { for (int i = 2; i * i <= n; i++) { if (n % i == 0) return false; } return true; }"),
                    "O(\u221An)", "O(1)");
        }

        @Test
        void binarySearchIsLogarithmic() {
            is(java("static int find(int[] a, int t) { int lo = 0, hi = a.length - 1; while (lo <= hi) { int mid = (lo + hi) / 2; "
                    + "if (a[mid] == t) return mid; else if (a[mid] < t) lo = mid + 1; else hi = mid - 1; } return -1; }"),
                    "O(log n)", "O(1)");
        }

        @Test
        void euclidsGcdIsLogarithmic() {
            is(java("static int gcd(int a, int b) { while (b != 0) { int t = b; b = a % b; a = t; } return a; }"),
                    "O(log n)", "O(1)");
        }

        @Test
        void aDoWhileIsHandled() {
            is(java("static int f(int n) { int i = 0; do { i++; } while (i < n); return i; }"), "O(n)", "O(1)");
        }

        @Test
        void aForEachIsLinear() {
            is(java("static int f(java.util.List<Integer> xs) { int s = 0; for (int x : xs) { s += x; } return s; }"), "O(n)", "O(1)");
        }

        @Test
        void commentsAndStringsAreNotCode() {
            is(java("static void f() { // for (int i = 0; i < n; i++) { }\n String s = \"for (;;) { while(true) {} }\"; /* while (true) {} */ }"),
                    "O(1)", "O(1)");
        }
    }

    // ------------------------------------------------------------------ hidden costs

    @Nested
    @DisplayName("Library calls and data structures")
    class Hidden {

        @Test
        void sortingIsNLogN() {
            is(java("static void f(int[] a) { java.util.Arrays.sort(a); }"), "O(n log n)", "O(1)");
        }

        @Test
        void sortingInsideALoopMultiplies() {
            var r = java("static void f(int[] a, int n) { for (int i = 0; i < n; i++) { java.util.Arrays.sort(a); } }");
            assertThat(r.timeComplexity()).isEqualTo("O(n\u00B2 log n)");
            assertThat(r.suggestions()).extracting(s -> s.title()).contains("Sort once, outside the loop");
        }

        @Test
        void searchingAListInsideALoopIsQuadratic() {
            var r = java("static int f(java.util.List<Integer> list, int[] a) { int c = 0; for (int i = 0; i < a.length; i++) "
                    + "{ if (list.contains(a[i])) c++; } return c; }");
            assertThat(r.timeComplexity()).isEqualTo("O(n\u00B2)");
            assertThat(r.suggestions()).extracting(s -> s.title()).contains("Use a set or map for the lookups");
        }

        @Test
        void aHashSetLookupInsideALoopStaysLinear() {
            var r = java("static int f(java.util.Set<Integer> set, int[] a) { int c = 0; for (int i = 0; i < a.length; i++) "
                    + "{ if (set.contains(a[i])) c++; } return c; }");
            assertThat(r.timeComplexity()).isEqualTo("O(n)");
            assertThat(r.suggestions()).isEmpty();
        }

        @Test
        void stringConcatenationInALoopIsFlagged() {
            var r = java("static String f(int n) { String s = \"\"; for (int i = 0; i < n; i++) { s += i; } return s; }");
            assertThat(r.timeComplexity()).isEqualTo("O(n\u00B2)");
            assertThat(r.suggestions()).extracting(s -> s.title()).contains("Build the string with a builder");
        }

        @Test
        void aNewArraySizedByTheInputIsLinearSpace() {
            is(java("static int[] f(int n) { int[] a = new int[n]; for (int i = 0; i < n; i++) { a[i] = i; } return a; }"),
                    "O(n)", "O(n)");
            is(java("static int[][] f(int n) { int[][] a = new int[n][n]; return a; }"), "O(n\u00B2)", "O(n\u00B2)");
            is(java("static int[] f(int n) { int[] a = new int[26]; return a; }"), "O(1)", "O(1)");
        }

        @Test
        void addingToACollectionInALoopIsLinearSpace() {
            is(java("static java.util.List<Integer> f(int n) { java.util.List<Integer> r = new java.util.ArrayList<>(); "
                    + "for (int i = 0; i < n; i++) { r.add(i); } return r; }"), "O(n)", "O(n)");
        }

        @Test
        void aMapFilledInALoopIsLinearSpace() {
            is(java("static int f(int[] a) { java.util.Map<Integer,Integer> m = new java.util.HashMap<>(); "
                    + "for (int i = 0; i < a.length; i++) { m.put(a[i], i); } return m.size(); }"), "O(n)", "O(n)");
        }
    }

    // ------------------------------------------------------------------ recursion

    @Nested
    @DisplayName("Recursion")
    class Recursion {

        @Test
        void aSingleRecursiveCallDownByOneIsLinear() {
            var r = java("static int fact(int n) { if (n <= 1) return 1; return n * fact(n - 1); }");
            assertThat(r.timeComplexity()).isEqualTo("O(n)");
            assertThat(r.spaceComplexity()).isEqualTo("O(n)");
            assertThat(r.confidence()).isEqualTo("MEDIUM");
        }

        @Test
        void fibonacciWithoutMemoIsExponential() {
            var r = java("static int fib(int n) { if (n <= 1) return n; return fib(n - 1) + fib(n - 2); }");
            assertThat(r.timeComplexity()).isEqualTo("O(2\u207F)");
            assertThat(r.suggestions()).extracting(s -> s.title()).contains("Remember results you have already computed");
        }

        @Test
        void fibonacciWithMemoIsLinear() {
            var r = java("static int[] memo = new int[100]; static int fib(int n) { if (n <= 1) return n; "
                    + "if (memo[n] != 0) return memo[n]; memo[n] = fib(n - 1) + fib(n - 2); return memo[n]; }");
            assertThat(r.timeComplexity()).isEqualTo("O(n)");
        }

        @Test
        void recursiveBinarySearchIsLogarithmic() {
            var r = java("static int bs(int[] a, int t, int lo, int hi) { if (lo > hi) return -1; int mid = (lo + hi) / 2; "
                    + "if (a[mid] == t) return mid; else if (a[mid] < t) return bs(a, t, mid + 1, hi); else return bs(a, t, lo, mid - 1); }");
            is(r, "O(log n)", "O(log n)");
        }

        @Test
        void mergeSortIsNLogN() {
            var r = java("static void sort(int[] a, int l, int r) { if (l >= r) return; int m = (l + r) / 2; sort(a, l, m); sort(a, m + 1, r); "
                    + "int[] tmp = new int[r - l + 1]; int i = l, j = m + 1, k = 0; "
                    + "while (i <= m && j <= r) { if (a[i] <= a[j]) tmp[k++] = a[i++]; else tmp[k++] = a[j++]; } "
                    + "while (i <= m) tmp[k++] = a[i++]; while (j <= r) tmp[k++] = a[j++]; "
                    + "for (int x = 0; x < tmp.length; x++) a[l + x] = tmp[x]; }");
            assertThat(r.timeComplexity()).as(String.join(" | ", r.timeSteps())).isEqualTo("O(n log n)");
            assertThat(r.spaceComplexity()).isEqualTo("O(n)");
        }

        @Test
        void aTreeTraversalIsLinear() {
            var r = java("static class Node { Node left, right; int v; } static int count(Node node) { if (node == null) return 0; "
                    + "return 1 + count(node.left) + count(node.right); }");
            assertThat(r.timeComplexity()).isEqualTo("O(n)");
        }

        @Test
        void towersOfHanoiIsExponential() {
            var r = java("static void hanoi(int n, int a, int b, int c) { if (n == 0) return; hanoi(n - 1, a, c, b); "
                    + "hanoi(n - 1, c, b, a); }");
            assertThat(r.timeComplexity()).isEqualTo("O(2\u207F)");
        }
    }

    // ------------------------------------------------------------------ suggestions

    @Nested
    @DisplayName("Better-solution suggestions")
    class Suggestions {

        @Test
        void twoSumWithNestedLoopsSuggestsAHashMap() {
            var r = java("static int[] twoSum(int[] nums, int target) { for (int i = 0; i < nums.length; i++) { "
                    + "for (int j = i + 1; j < nums.length; j++) { if (nums[i] + nums[j] == target) { return new int[]{i, j}; } } } return null; }");
            is(r, "O(n\u00B2)", "O(1)");
            assertThat(r.verdict()).isEqualTo("COULD_BE_BETTER");
            var s = r.suggestions().get(0);
            assertThat(s.title()).isEqualTo("Look values up instead of searching for them");
            assertThat(s.currentTime()).isEqualTo("O(n\u00B2)");
            assertThat(s.betterTime()).isEqualTo("O(n)");
            assertThat(s.betterSpace()).isEqualTo("O(n)");
            assertThat(s.explanation()).contains("HashMap");
        }

        @Test
        void twoSumWithAMapIsEfficient() {
            var r = java("static int[] twoSum(int[] nums, int target) { java.util.Map<Integer,Integer> seen = new java.util.HashMap<>(); "
                    + "for (int i = 0; i < nums.length; i++) { int need = target - nums[i]; if (seen.containsKey(need)) { return new int[]{seen.get(need), i}; } "
                    + "seen.put(nums[i], i); } return null; }");
            is(r, "O(n)", "O(n)");
            assertThat(r.verdict()).isEqualTo("EFFICIENT");
            assertThat(r.suggestions()).isEmpty();
        }

        @Test
        void bubbleSortSuggestsABetterSort() {
            var r = java("static void bubble(int[] a) { for (int i = 0; i < a.length; i++) { for (int j = 0; j < a.length - 1 - i; j++) { "
                    + "if (a[j] > a[j + 1]) { int temp = a[j]; a[j] = a[j + 1]; a[j + 1] = temp; } } } }");
            assertThat(r.timeComplexity()).isEqualTo("O(n\u00B2)");
            assertThat(r.suggestions()).extracting(s -> s.betterTime()).contains("O(n log n)");
        }

        @Test
        void aFindingIsNotInventedForCleanCode() {
            var r = java("static int max(int[] a) { int m = a[0]; for (int i = 1; i < a.length; i++) { if (a[i] > m) m = a[i]; } return m; }");
            assertThat(r.suggestions()).isEmpty();
            assertThat(r.verdictMessage()).isEqualTo("Efficient solution.");
        }
    }

    // ------------------------------------------------------------------ Python

    @Nested
    @DisplayName("Python")
    class Py {

        @Test
        void loopsAndNesting() {
            is(python("def f(n):\n    total = 0\n    for i in range(n):\n        total += i\n    return total\n"), "O(n)", "O(1)");
            is(python("def f(n):\n    c = 0\n    for i in range(n):\n        for j in range(n):\n            c += 1\n    return c\n"),
                    "O(n\u00B2)", "O(1)");
            is(python("def f():\n    c = 0\n    for i in range(10):\n        c += i\n    return c\n"), "O(1)", "O(1)");
        }

        @Test
        void aHalvingWhileIsLogarithmic() {
            is(python("def f(n):\n    c = 0\n    while n > 1:\n        n //= 2\n        c += 1\n    return c\n"), "O(log n)", "O(1)");
        }

        @Test
        void twoSumNestedAndWithADict() {
            var slow = python("def two_sum(nums, target):\n    for i in range(len(nums)):\n        for j in range(i + 1, len(nums)):\n"
                    + "            if nums[i] + nums[j] == target:\n                return [i, j]\n");
            is(slow, "O(n\u00B2)", "O(1)");
            assertThat(slow.suggestions()).isNotEmpty();
            var fast = python("def two_sum(nums, target):\n    seen = {}\n    for i, x in enumerate(nums):\n        if target - x in seen:\n"
                    + "            return [seen[target - x], i]\n        seen[x] = i\n");
            is(fast, "O(n)", "O(n)");
        }

        @Test
        void membershipInAListIsLinearButInASetIsNot() {
            var list = python("def f(a):\n    seen = []\n    for x in a:\n        if x in seen:\n            return True\n        seen.append(x)\n    return False\n");
            assertThat(list.timeComplexity()).isEqualTo("O(n\u00B2)");
            var set = python("def f(a):\n    seen = set()\n    for x in a:\n        if x in seen:\n            return True\n        seen.add(x)\n    return False\n");
            is(set, "O(n)", "O(n)");
        }

        @Test
        void listComprehensionsAndRepeats() {
            is(python("def f(n):\n    return [i * i for i in range(n)]\n"), "O(n)", "O(n)");
            is(python("def f(n):\n    dp = [0] * (n + 1)\n    return dp\n"), "O(n)", "O(n)");
            is(python("def f(n, m):\n    grid = [[0] * m for _ in range(n)]\n    return grid\n"), "O(n\u00B2)", "O(n\u00B2)");
        }

        @Test
        void recursionAndMemoisation() {
            var slow = python("def fib(n):\n    if n <= 1:\n        return n\n    return fib(n - 1) + fib(n - 2)\n");
            assertThat(slow.timeComplexity()).isEqualTo("O(2\u207F)");
            var fast = python("from functools import lru_cache\n@lru_cache(None)\ndef fib(n):\n    if n <= 1:\n        return n\n"
                    + "    return fib(n - 1) + fib(n - 2)\n");
            assertThat(fast.timeComplexity()).isEqualTo("O(n)");
        }

        @Test
        void sortedAndMaxOverACollection() {
            assertThat(python("def f(a):\n    return sorted(a)\n").timeComplexity()).isEqualTo("O(n log n)");
            assertThat(python("def f(a):\n    return max(a)\n").timeComplexity()).isEqualTo("O(n)");
            assertThat(python("def f(a, b):\n    return max(a, b)\n").timeComplexity()).isEqualTo("O(1)");
        }

        @Test
        void helperFunctionsCountAtTheirCallSite() {
            var r = python("def linear(a):\n    s = 0\n    for x in a:\n        s += x\n    return s\n"
                    + "def main(a):\n    for i in range(len(a)):\n        linear(a)\n");
            assertThat(r.timeComplexity()).isEqualTo("O(n\u00B2)");
        }
    }

    // ------------------------------------------------------------------ C and C++

    @Nested
    @DisplayName("C and C++")
    class CFamily {

        @Test
        void loops() {
            is(cpp("int sum(vector<int>& a) { int s = 0; for (int i = 0; i < a.size(); i++) { s += a[i]; } return s; }"),
                    "O(n)", "O(1)");
            is(cpp("int f(int n) { int c = 0; for (int i = 0; i < n; i++) { for (int j = 0; j < n; j++) { c++; } } return c; }"),
                    "O(n\u00B2)", "O(1)");
        }

        @Test
        void sortAndVectors() {
            is(cpp("void f(vector<int>& a) { sort(a.begin(), a.end()); }"), "O(n log n)", "O(1)");
            is(cpp("int f(int n) { vector<int> dp(n + 1, 0); return dp[0]; }"), "O(n)", "O(n)");
            is(cpp("int f(int n) { vector<vector<int>> dp(n, vector<int>(n, 0)); return dp[0][0]; }"), "O(n\u00B2)", "O(n\u00B2)");
        }

        @Test
        void aUnionFindNamedFindIsNotALibraryScan() {
            var r = cpp("int parent[100]; int find(int x) { return parent[x] == x ? x : parent[x] = find(parent[x]); } "
                    + "int main() { int n = 5; for (int i = 0; i < n; i++) { find(i); } return 0; }");
            assertThat(r.timeReason()).doesNotContain("reads or copies");
        }

        @Test
        void cLoopsAndFixedArrays() {
            var r = StaticAnalyzer.analyze("C", "#include <stdio.h>\nint main() { int a[1000]; int n; scanf(\"%d\", &n); "
                    + "for (int i = 0; i < n; i++) { for (int j = 0; j < n; j++) { a[0]++; } } return 0; }");
            is(r, "O(n\u00B2)", "O(1)");
        }
    }

    // ------------------------------------------------------------------ the answer itself

    @Nested
    @DisplayName("What the answer says")
    class Answer {

        @Test
        void itIsAlwaysLabelledAnEstimateWithAReason() {
            var r = java("static int f(int[] a) { int s = 0; for (int i = 0; i < a.length; i++) { s += a[i]; } return s; }");
            assertThat(r.estimated()).isTrue();
            assertThat(r.timeReason()).contains("line 2").contains("n times");
            assertThat(r.notes()).isNotEmpty();
            assertThat(r.confidence()).isEqualTo("HIGH");
        }

        @Test
        void aLoopWithNoClearEndLowersTheConfidence() {
            var r = java("static void f() { while (true) { int x = 1; } }");
            assertThat(r.confidence()).isEqualTo("LOW");
            assertThat(r.notes()).anyMatch(n -> n.contains("no clear end"));
        }

        @Test
        void sqlIsNotAnalysedAndNothingIsInvented() {
            var r = StaticAnalyzer.analyze("SQL", "select * from t");
            assertThat(r.supported()).isFalse();
            assertThat(r.timeComplexity()).isNull();
        }

        @Test
        void emptyOrBrokenCodeNeverThrows() {
            assertThat(StaticAnalyzer.analyze("JAVA", "").verdict()).isEqualTo("UNKNOWN");
            assertThat(StaticAnalyzer.analyze("JAVA", "}}}{{{ for (((").verdict()).isNotNull();
            assertThat(StaticAnalyzer.analyze("PYTHON", "def f(:\n  for\n").verdict()).isNotNull();
        }

        @Test
        void hugeCodeIsRefusedNotAnalysed() {
            String huge = "int x;\n".repeat(StaticAnalyzer.MAX_SOURCE_CHARS);
            assertThat(StaticAnalyzer.analyze("JAVA", huge).verdictMessage()).contains("too long");
        }

        @Test
        void hostileInputDoesNotHangTheAnalyser() {
            String nasty = "for (".repeat(3000) + "a".repeat(5000);
            long start = System.nanoTime();
            StaticAnalyzer.analyze("JAVA", nasty);
            StaticAnalyzer.analyze("PYTHON", "for x in " + "(".repeat(3000) + "\n" + "[".repeat(3000));
            assertThat((System.nanoTime() - start) / 1_000_000).as("milliseconds").isLessThan(3000);
        }
    }
}

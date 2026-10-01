# Code analysis (LeetCode-style result panel)

After a student runs code in the practice editor, or runs the tests of a coding question in a test, the result panel shows what happened, how long it took, how much memory it used, and an **estimate** of the time and space complexity with the reasons, plus suggestions where the analyser sees a way to improve.

## What is measured and what is estimated
| Shown | Comes from |
|---|---|
| Test cases passed, execution time, memory | The sandbox (Piston/Judge0), as before. For a test run the time and memory are the slowest/largest across cases; each case is listed in "Test case timings". |
| Time and space complexity, reasons, suggestions | `POST /api/code/analyze`, a static analyser that **reads the source and never runs it**. Always labelled *Estimated* with a confidence level. |

Untrusted code still runs only in the existing sandbox. The analyser has no execution path at all, so it cannot weaken that: it parses text, and it is capped at 30,000 characters and returns "could not be analysed" instead of throwing.

## How the analyser decides (codeexec-service, `com.itilms.codeexec.analysis`)
It strips comments and string contents, rebuilds the nesting of blocks (braces, or indentation for Python), and works out:
- **Loops**: nested loops multiply, sequential ones do not. A loop bounded by a literal or a CAPITALISED constant is constant; `i *= 2`, `n /= 2`, a midpoint-driven binary search, or `a % b` (Euclid) is log n; `i * i <= n` is the square root; a while loop with no visible end is assumed to run n times and lowers the confidence.
- **Hidden costs**: sorting is n log n; scanning a list/string (`contains`, `indexOf`, `x in list`, `remove`, `substring`, string `+=` in Java/C#) costs n; library calls that read or copy a whole collection cost n. Sets and maps count as constant lookups. A function the student named `sort` or `find` is analysed as their own code, not assumed to be the library one.
- **Recursion**: calls are counted along one execution path (the branches of an if/else are alternatives). Halving gives log n or n log n, one call down by one gives n, two calls down by one with no memoisation gives 2^n, tree/link traversal gives n. Memoisation (`memo`, `cache`, `lru_cache`, `dp`...) turns the exponential case into n.
- **Space**: arrays and vectors sized by the input, collections that grow inside loops, comprehensions, copies, and recursion depth (log n for halving, otherwise n).
- **Suggestions** (advice only, never applied): pair search in nested loops to a hash map; list lookup in a loop to a set/map; sort inside a loop; insert/remove at the front; string concatenation in a loop; bubble/selection sort; exponential recursion to memoisation or DP. Each shows "Your solution: time/space" against "Possible optimized solution: time/space".

## Limits (stated in the panel's notes too)
It is a heuristic. It cannot know how large the real input is, so a loop over a fixed table and one over the input look the same unless the bound is a literal or a CAPITALISED constant; a single capital letter such as `N` is treated as the input size. Tree-based collections and heaps (log n operations) are not counted; unusual structure can mislead it. Where it is unsure it says so (LOW confidence) rather than guessing quietly.

## Charts
- *Growth chart* (View Explanation / Optimize Solution): the usual growth rates faintly, this code's rate heavy, the suggested rate dashed. It shows shape, not measured time.
- *Test case timings*: one bar per test for time and for memory, green for passed, red for failed.

## "Faster than X%" (test runs only)
assessment-service (V5, `coding_run_stats`) keeps each student's best total runtime and largest memory for a coding question, but only from a run that **passed every test**. A passing run is compared with the other students' bests: "faster than N%" is the share of others who were slower (a tie does not beat anyone), and a ten-column histogram shows where the student sits.
- **Not shown until it means something:** below `itilms.assessment.comparison-min-sample` (default 20) students the server returns only the count, and the panel says how many more are needed.
- Only numbers are stored and returned: no code, no names. Memory is compared only among students who have a memory figure.
- Runtime is the sum of the test cases' times (the sandbox measures each case on its own), so it is for comparing solutions, not a benchmark.

## Not covered
The live-class coding question has no run button (the student only submits code for the trainer to mark) and assignments have no code editor, so neither shows this panel yet.

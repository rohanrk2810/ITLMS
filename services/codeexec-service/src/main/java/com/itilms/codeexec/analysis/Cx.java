package com.itilms.codeexec.analysis;

/**
 * A growth rate of the form n^(p2/2) * (log n)^log, or exponential. {@code p2} is twice the power of n so that
 * a square root (p2 = 1) can be written: n is 2, n squared is 4, the square root of n is 1.
 */
public record Cx(int p2, int log, boolean exp) implements Comparable<Cx> {

    public static final Cx ONE = new Cx(0, 0, false);
    public static final Cx LOG = new Cx(0, 1, false);
    public static final Cx SQRT = new Cx(1, 0, false);
    public static final Cx N = new Cx(2, 0, false);
    public static final Cx N_LOG = new Cx(2, 1, false);
    public static final Cx N2 = new Cx(4, 0, false);
    public static final Cx EXP = new Cx(0, 0, true);

    /** Work done inside a loop that runs this often multiplies. */
    public Cx times(Cx other) {
        if (exp || other.exp) {
            return EXP;
        }
        return new Cx(p2 + other.p2, log + other.log, false);
    }

    public Cx max(Cx other) {
        return compareTo(other) >= 0 ? this : other;
    }

    /** One factor of n fewer, never going below n itself (a single pass over the input is the floor here). */
    public Cx dropOneN() {
        if (exp || p2 < 4) {
            return this;
        }
        return new Cx(p2 - 2, log, false);
    }

    @Override
    public int compareTo(Cx other) {
        if (exp != other.exp) {
            return exp ? 1 : -1;
        }
        if (exp) {
            return 0;
        }
        if (p2 != other.p2) {
            return Integer.compare(p2, other.p2);
        }
        return Integer.compare(log, other.log);
    }

    public boolean isConstant() {
        return !exp && p2 == 0 && log == 0;
    }

    /** "O(n log n)", "O(n²)", "O(2ⁿ)". */
    public String label() {
        if (exp) {
            return "O(2ⁿ)";
        }
        String poly = switch (p2) {
            case 0 -> "";
            case 1 -> "√n";
            case 2 -> "n";
            case 3 -> "n√n";
            case 4 -> "n²";
            case 6 -> "n³";
            default -> p2 % 2 == 0 ? "n^" + (p2 / 2) : "n^" + (p2 / 2.0);
        };
        String logPart = switch (log) {
            case 0 -> "";
            case 1 -> "log n";
            default -> "log^" + log + " n";
        };
        String body = (poly + " " + logPart).trim();
        return "O(" + (body.isEmpty() ? "1" : body) + ")";
    }
}

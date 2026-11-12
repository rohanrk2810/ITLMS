package com.itilms.finance.ledger;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

/**
 * Decides which book entry a bank statement line belongs to. Pure, so the rules can be tested.
 *
 * <p>A line matches an entry of the same amount when the entry's reference (a UPI transaction id, a cheque number)
 * appears in the line's reference or description. Failing that, when exactly one unmatched entry of that amount falls
 * within three days of the line. Two equally good candidates are never guessed between: they are left for a person.
 */
public final class BankMatcher {

    /** How many days apart a bank line and a book entry may be to be matched by amount alone. */
    public static final int WINDOW_DAYS = 3;

    private BankMatcher() {
    }

    public record Candidate(Long id, BigDecimal amount, LocalDate date, String reference) {
    }

    /** The id of the entry the line belongs to, or null when there is none or it is ambiguous. */
    public static Long match(BigDecimal amount, LocalDate lineDate, String lineText, List<Candidate> candidates) {
        List<Candidate> sameAmount = candidates.stream().filter(c -> c.amount().compareTo(amount) == 0).toList();
        String text = lineText == null ? "" : lineText.toLowerCase(Locale.ROOT);
        List<Candidate> byReference = sameAmount.stream()
                .filter(c -> c.reference() != null && c.reference().trim().length() >= 4
                        && text.contains(c.reference().trim().toLowerCase(Locale.ROOT)))
                .toList();
        if (byReference.size() == 1) {
            return byReference.get(0).id();
        }
        if (byReference.size() > 1) {
            return null;
        }
        List<Candidate> near = sameAmount.stream()
                .filter(c -> Math.abs(ChronoUnit.DAYS.between(c.date(), lineDate)) <= WINDOW_DAYS)
                .toList();
        return near.size() == 1 ? near.get(0).id() : null;
    }
}

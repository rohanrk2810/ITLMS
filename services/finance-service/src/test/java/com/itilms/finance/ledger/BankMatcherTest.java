package com.itilms.finance.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class BankMatcherTest {

    private static BankMatcher.Candidate c(long id, String amount, String date, String ref) {
        return new BankMatcher.Candidate(id, new BigDecimal(amount), LocalDate.parse(date), ref);
    }

    private static final LocalDate DAY = LocalDate.of(2026, 3, 10);

    @Test
    void matchesByReferenceFoundInTheDescription() {
        var found = BankMatcher.match(new BigDecimal("5000"), DAY, "UPI/412345678901/ASHA", List.of(
                c(1, "5000", "2026-03-01", "412345678901"), c(2, "5000", "2026-03-09", "999999999999")));

        assertThat(found).isEqualTo(1L);
    }

    @Test
    void aSingleEntryOfTheSameAmountWithinThreeDaysIsMatched() {
        assertThat(BankMatcher.match(new BigDecimal("700"), DAY, "NEFT", List.of(c(7, "700", "2026-03-08", null)))).isEqualTo(7L);
        assertThat(BankMatcher.match(new BigDecimal("700"), DAY, "NEFT", List.of(c(7, "700", "2026-03-01", null)))).isNull();
    }

    @Test
    void twoEqualCandidatesAreNeverGuessedBetween() {
        assertThat(BankMatcher.match(new BigDecimal("700"), DAY, "NEFT",
                List.of(c(1, "700", "2026-03-09", null), c(2, "700", "2026-03-10", null)))).isNull();
    }

    @Test
    void aDifferentAmountIsNeverMatched() {
        assertThat(BankMatcher.match(new BigDecimal("700"), DAY, "412345678901",
                List.of(c(1, "701", "2026-03-10", "412345678901")))).isNull();
    }
}

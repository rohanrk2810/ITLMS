package com.itilms.finance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.common.event.EventPublisher;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.common.security.SecurityUtils;
import com.itilms.finance.entity.BankLine;
import com.itilms.finance.entity.Expense;
import com.itilms.finance.entity.Payment;
import com.itilms.finance.entity.PaymentMethod;
import com.itilms.finance.entity.PaymentStatus;
import com.itilms.finance.entity.Refund;
import com.itilms.finance.ledger.BankMatcher;
import com.itilms.finance.repository.BankLineRepository;
import com.itilms.finance.repository.ExpenseRepository;
import com.itilms.finance.repository.PaymentRepository;
import com.itilms.finance.repository.RefundRepository;

import lombok.RequiredArgsConstructor;

/**
 * Matching the bank statement to the books. Statement lines are imported (a CSV the desk downloads from the bank);
 * each is matched to the payment, expense or refund it belongs to, automatically when it is clear and by hand when
 * it is not. What is left over on either side is what to chase: money the bank shows that was never recorded, and a
 * payment recorded as received by bank that never reached the statement.
 */
@Service
@RequiredArgsConstructor
public class BankReconciliationService {

    /** Methods whose money goes through the bank; cash does not. */
    static final Set<PaymentMethod> BANK_METHODS =
            Set.of(PaymentMethod.BANK_TRANSFER, PaymentMethod.UPI, PaymentMethod.CARD, PaymentMethod.CHEQUE, PaymentMethod.ONLINE);

    private final BankLineRepository lines;
    private final PaymentRepository payments;
    private final ExpenseRepository expenses;
    private final RefundRepository refunds;
    private final EventPublisher events;

    public record LineInput(LocalDate date, String description, String reference, BigDecimal debit, BigDecimal credit) {
    }

    public record ImportResult(int imported, int duplicates, int matched) {
    }

    public record LineView(Long id, LocalDate date, String description, String reference, BigDecimal debit,
                           BigDecimal credit, String matchedKind, Long matchedId) {
        static LineView from(BankLine l) {
            return new LineView(l.getId(), l.getLineDate(), l.getDescription(), l.getReference(), l.getDebit(),
                    l.getCredit(), l.getMatchedKind(), l.getMatchedId());
        }
    }

    public record Entry(String kind, Long id, LocalDate date, String label, String reference, BigDecimal amount) {
    }

    public record Summary(int lines, int matched, int unmatched, BigDecimal unmatchedIn, BigDecimal unmatchedOut,
                          List<Entry> notOnStatement) {
    }

    // ------------------------------------------------------------------ import

    @Transactional
    public ImportResult importLines(List<LineInput> input) {
        if (input == null || input.isEmpty()) {
            throw new BusinessRuleException("The statement has no lines.");
        }
        if (input.size() > 5000) {
            throw new BusinessRuleException("Import at most 5000 lines at a time.");
        }
        // Two identical lines on one statement (the same amount twice in a day) are both real, so the key carries a
        // running count; importing the same file again then produces the same keys and adds nothing.
        Map<String, Integer> seen = new java.util.HashMap<>();
        List<BankLine> built = new ArrayList<>();
        for (LineInput in : input) {
            BigDecimal debit = money(in.debit());
            BigDecimal credit = money(in.credit());
            if (in.date() == null || (debit.signum() > 0) == (credit.signum() > 0)) {
                throw new BusinessRuleException("Every line needs a date and either a debit or a credit amount.");
            }
            String base = in.date() + "|" + clean(in.description()) + "|" + clean(in.reference()) + "|" + debit + "|" + credit;
            int n = seen.merge(base, 1, Integer::sum);
            built.add(BankLine.builder().lineDate(in.date()).description(trim(clean(in.description()), 255))
                    .reference(blankToNull(trim(clean(in.reference()), 80))).debit(debit).credit(credit)
                    .importKey(trim(base + "|" + n, 300)).build());
        }
        Set<String> existing = lines.findByImportKeyIn(built.stream().map(BankLine::getImportKey).toList()).stream()
                .map(BankLine::getImportKey).collect(Collectors.toSet());
        List<BankLine> fresh = built.stream().filter(l -> !existing.contains(l.getImportKey())).toList();
        lines.saveAll(fresh);
        int matched = autoMatch();
        events.audit("finance-service", "BANK_STATEMENT_IMPORTED", "BankStatement", null, null,
                Map.of("lines", fresh.size(), "duplicates", built.size() - fresh.size(), "matched", matched));
        return new ImportResult(fresh.size(), built.size() - fresh.size(), matched);
    }

    /** Matches every unmatched line that has one clear answer. Returns how many it matched. */
    @Transactional
    public int autoMatch() {
        List<BankLine> open = lines.findByMatchedKindIsNull();
        if (open.isEmpty()) {
            return 0;
        }
        LocalDate from = open.stream().map(BankLine::getLineDate).min(LocalDate::compareTo).orElseThrow()
                .minusDays(BankMatcher.WINDOW_DAYS + 30L);
        LocalDate to = open.stream().map(BankLine::getLineDate).max(LocalDate::compareTo).orElseThrow()
                .plusDays(BankMatcher.WINDOW_DAYS);
        Set<String> taken = lines.findByMatchedKindIsNotNull().stream()
                .map(l -> l.getMatchedKind() + ":" + l.getMatchedId()).collect(Collectors.toCollection(HashSet::new));
        Long me = currentUser();
        int done = 0;
        for (BankLine line : open) {
            String kind = line.isCredit() ? "PAYMENT" : null;
            List<Entry> pool = new ArrayList<>();
            if (line.isCredit()) {
                pool.addAll(paymentEntries(from, to));
            } else {
                pool.addAll(expenseEntries(from, to));
                pool.addAll(refundEntries(from, to));
            }
            List<BankMatcher.Candidate> candidates = new ArrayList<>();
            Map<Long, String> kinds = new java.util.HashMap<>();
            for (Entry e : pool) {
                if (!taken.contains(e.kind() + ":" + e.id())) {
                    // Ids from different tables can coincide, so the candidate id is made unique per kind.
                    long key = key(e.kind(), e.id());
                    candidates.add(new BankMatcher.Candidate(key, e.amount(), e.date(), e.reference()));
                    kinds.put(key, e.kind());
                }
            }
            Long found = BankMatcher.match(line.amount(), line.getLineDate(), line.searchText(), candidates);
            if (found != null) {
                String matchedKind = kinds.get(found);
                Long id = found / 10;
                line.setMatchedKind(matchedKind);
                line.setMatchedId(id);
                line.setMatchedAt(Instant.now());
                line.setMatchedBy(me);
                taken.add(matchedKind + ":" + id);
                lines.save(line);
                done++;
            }
        }
        return done;
    }

    // ------------------------------------------------------------------ by hand

    @Transactional(readOnly = true)
    public List<Entry> candidates(Long lineId) {
        BankLine line = require(lineId);
        Set<String> taken = lines.findByMatchedKindIsNotNull().stream()
                .map(l -> l.getMatchedKind() + ":" + l.getMatchedId()).collect(Collectors.toSet());
        LocalDate from = line.getLineDate().minusDays(120);
        LocalDate to = line.getLineDate().plusDays(30);
        List<Entry> pool = new ArrayList<>();
        if (line.isCredit()) {
            pool.addAll(paymentEntries(from, to));
        } else {
            pool.addAll(expenseEntries(from, to));
            pool.addAll(refundEntries(from, to));
        }
        return pool.stream().filter(e -> !taken.contains(e.kind() + ":" + e.id()))
                .filter(e -> e.amount().compareTo(line.amount()) == 0).limit(20).toList();
    }

    @Transactional
    public LineView match(Long lineId, String kind, Long id) {
        BankLine line = require(lineId);
        if (line.isMatched()) {
            throw new BusinessRuleException("This line is already matched. Unmatch it first.");
        }
        String k = kind == null ? "" : kind.toUpperCase(java.util.Locale.ROOT);
        BigDecimal amount = switch (k) {
            case "PAYMENT" -> payments.findById(id).orElseThrow(() -> new ResourceNotFoundException("Payment", id)).getAmount();
            case "EXPENSE" -> expenses.findById(id).orElseThrow(() -> new ResourceNotFoundException("Expense", id)).getAmount();
            case "REFUND" -> refunds.findById(id).orElseThrow(() -> new ResourceNotFoundException("Refund", id)).getAmount();
            default -> throw new BusinessRuleException("Match to a payment, an expense or a refund.");
        };
        if (amount.compareTo(line.amount()) != 0) {
            throw new BusinessRuleException("The amounts differ: the bank shows %s and the books %s."
                    .formatted(line.amount().toPlainString(), amount.toPlainString()));
        }
        if (line.isCredit() != "PAYMENT".equals(k)) {
            throw new BusinessRuleException("Money in matches a payment; money out matches an expense or a refund.");
        }
        boolean taken = lines.findByMatchedKindIsNotNull().stream()
                .anyMatch(l -> k.equals(l.getMatchedKind()) && id.equals(l.getMatchedId()));
        if (taken) {
            throw new BusinessRuleException("That entry is already matched to another bank line.");
        }
        line.setMatchedKind(k);
        line.setMatchedId(id);
        line.setMatchedAt(Instant.now());
        line.setMatchedBy(currentUser());
        events.audit("finance-service", "BANK_LINE_MATCHED", "BankLine", lineId, null, Map.of("kind", k, "id", id));
        return LineView.from(lines.save(line));
    }

    @Transactional
    public LineView unmatch(Long lineId) {
        BankLine line = require(lineId);
        line.setMatchedKind(null);
        line.setMatchedId(null);
        line.setMatchedAt(null);
        line.setMatchedBy(null);
        events.audit("finance-service", "BANK_LINE_UNMATCHED", "BankLine", lineId, null, Map.of());
        return LineView.from(lines.save(line));
    }

    // ------------------------------------------------------------------ views

    @Transactional(readOnly = true)
    public List<LineView> list(boolean onlyUnmatched) {
        return lines.findAllByOrderByLineDateDescIdDesc().stream().filter(l -> !onlyUnmatched || !l.isMatched())
                .limit(500).map(LineView::from).toList();
    }

    /** How the statement and the books stand: lines matched and not, and recorded bank payments the statement lacks. */
    @Transactional(readOnly = true)
    public Summary summary() {
        List<BankLine> all = lines.findAllByOrderByLineDateDescIdDesc();
        List<BankLine> open = all.stream().filter(l -> !l.isMatched()).toList();
        BigDecimal in = open.stream().map(BankLine::getCredit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal out = open.stream().map(BankLine::getDebit).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Entry> missing = new ArrayList<>();
        if (!all.isEmpty()) {
            LocalDate from = all.stream().map(BankLine::getLineDate).min(LocalDate::compareTo).orElseThrow();
            LocalDate to = all.stream().map(BankLine::getLineDate).max(LocalDate::compareTo).orElseThrow();
            Set<String> taken = all.stream().filter(BankLine::isMatched)
                    .map(l -> l.getMatchedKind() + ":" + l.getMatchedId()).collect(Collectors.toSet());
            // Recorded as received by bank inside the statement's period, but no bank line was ever matched to it.
            paymentEntries(from, to).stream().filter(e -> !taken.contains(e.kind() + ":" + e.id())).forEach(missing::add);
            expenseEntries(from, to).stream().filter(e -> !taken.contains(e.kind() + ":" + e.id())).forEach(missing::add);
        }
        return new Summary(all.size(), all.size() - open.size(), open.size(), in, out, missing.stream().limit(100).toList());
    }

    // ------------------------------------------------------------------ helpers

    private List<Entry> paymentEntries(LocalDate from, LocalDate to) {
        return payments.findByPaymentDateBetweenAndStatus(from, to, PaymentStatus.SUCCESS).stream()
                .filter(p -> BANK_METHODS.contains(p.getMethod()))
                .map(p -> new Entry("PAYMENT", p.getId(), p.getPaymentDate(), "Receipt " + p.getReceiptNo(),
                        p.getReferenceNo(), p.getAmount())).toList();
    }

    private List<Entry> expenseEntries(LocalDate from, LocalDate to) {
        return expenses.findByExpenseDateBetweenOrderByExpenseDateDescIdDesc(from, to).stream().filter(Expense::counts)
                .map(e -> new Entry("EXPENSE", e.getId(), e.getExpenseDate(), e.getCategory() + ": " + e.getDescription(),
                        e.getReferenceNo(), e.getAmount())).toList();
    }

    private List<Entry> refundEntries(LocalDate from, LocalDate to) {
        return refunds.findByStatusOrderByIdDesc(Refund.Status.PAID).stream()
                .filter(r -> r.getPaidOn() != null && !r.getPaidOn().isBefore(from) && !r.getPaidOn().isAfter(to))
                .filter(r -> !"CASH".equals(r.getPayMethod()))
                .map(r -> new Entry("REFUND", r.getId(), r.getPaidOn(), "Refund " + r.getId(), r.getPayReference(),
                        r.getAmount())).toList();
    }

    /** One number per (kind, id), ending in a digit that says which table it came from. */
    private static long key(String kind, Long id) {
        return id * 10 + switch (kind) {
            case "PAYMENT" -> 1;
            case "EXPENSE" -> 2;
            default -> 3;
        };
    }

    private BankLine require(Long id) {
        return lines.findById(id).orElseThrow(() -> new ResourceNotFoundException("Bank line", id));
    }

    private static Long currentUser() {
        try {
            return SecurityUtils.requirePrincipal().userId();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static BigDecimal money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ");
    }

    private static String trim(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}

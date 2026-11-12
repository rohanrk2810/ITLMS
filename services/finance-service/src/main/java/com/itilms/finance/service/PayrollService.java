package com.itilms.finance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.common.event.EventPublisher;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.common.security.AppPrincipal;
import com.itilms.common.security.SecurityUtils;
import com.itilms.finance.client.StaffDirectoryClient;
import com.itilms.finance.config.FinanceProperties;
import com.itilms.finance.entity.Expense;
import com.itilms.finance.entity.Payslip;
import com.itilms.finance.entity.StaffPay;
import com.itilms.finance.ledger.PayHeads;
import com.itilms.finance.ledger.PayslipMath;
import com.itilms.finance.repository.PayslipRepository;
import com.itilms.finance.repository.StaffPayRepository;

import lombok.RequiredArgsConstructor;

/**
 * Staff payroll: the salary structure of each person, a salary slip per person per month, approval by the
 * administrator, and payment by the finance desk. Paying a slip records a SALARY expense, so the profit and loss
 * already counts it.
 */
@Service
@RequiredArgsConstructor
public class PayrollService {

    private static final String SERVICE_NAME = "finance-service";
    private static final Set<String> PAY_METHODS = Set.of("CASH", "UPI", "BANK_TRANSFER", "CHEQUE");

    private final StaffPayRepository structures;
    private final com.itilms.finance.repository.StaffPayHeadRepository heads;
    private final com.itilms.finance.repository.PayslipItemRepository items;
    private final com.itilms.finance.repository.StaffPayHistoryRepository history;
    private final PayslipRepository slips;
    private final StaffDirectoryClient directory;
    private final ExpenseService expenses;
    private final FinanceProperties props;
    private final EventPublisher events;
    private final BooksLockService booksLock;

    // ------------------------------------------------------------------ views

    /** A head as the desk enters it: an amount, or a percent of the basic. */
    public record HeadInput(String kind, String name, BigDecimal amount, BigDecimal percentOfBasic, boolean basic) {
    }

    /** What is on file about the person: the details a payslip shows. */
    public record Profile(String employeeCode, String pan, String bankName, String bankAccount, String pfNumber,
                          String uan, String esiNumber, LocalDate hiredOn, String department, String location) {
    }

    public record HeadRow(String kind, String name, BigDecimal amount, BigDecimal percentOfBasic, boolean basic) {
    }

    public record SlipItemView(String kind, String name, BigDecimal amount) {
    }

    public record StaffRow(Long userId, String name, String email, String role, BigDecimal basic, BigDecimal allowances,
                           BigDecimal fixedDeduction, boolean active, boolean configured, List<HeadRow> heads,
                           Profile profile) {
    }

    public record HistoryEntry(BigDecimal basic, BigDecimal allowances, BigDecimal fixedDeduction, boolean active,
                               Long changedBy, java.time.Instant changedAt) {
    }

    public record StructureRequest(BigDecimal basic, BigDecimal allowances, BigDecimal fixedDeduction, Boolean active,
                                   List<HeadInput> heads, Profile profile) {

        public StructureRequest(BigDecimal basic, BigDecimal allowances, BigDecimal fixedDeduction, Boolean active) {
            this(basic, allowances, fixedDeduction, active, null, null);
        }
    }

    public record SlipRequest(BigDecimal lwpDays, BigDecimal otherDeduction, BigDecimal bonus, String note) {
    }

    public record PayRequest(String method, String reference, LocalDate paidOn) {
    }

    public record SlipView(Long id, String slipNo, String month, Long userId, String staffName, String designation,
                           int daysInMonth, BigDecimal basic, BigDecimal allowances, BigDecimal fixedDeduction,
                           BigDecimal lwpDays, BigDecimal lwpDeduction, BigDecimal otherDeduction, BigDecimal bonus,
                           BigDecimal gross, BigDecimal net, String note, String status, LocalDate paidOn,
                           String payMethod, String payReference, List<SlipItemView> items, Profile profile) {
        static SlipView from(Payslip p, List<SlipItemView> items) {
            return new SlipView(p.getId(), p.getSlipNo(), p.getMonthKey(), p.getUserId(), p.getStaffName(),
                    p.getDesignation(), p.getDaysInMonth(), p.getBasic(), p.getAllowances(), p.getFixedDeduction(),
                    p.getLwpDays(), p.getLwpDeduction(), p.getOtherDeduction(), p.getBonus(), p.getGross(), p.getNet(),
                    p.getNote(), p.getStatus().name(), p.getPaidOn(), p.getPayMethod(), p.getPayReference(), items,
                    new Profile(p.getEmployeeCode(), p.getPan(), p.getBankName(), p.getBankAccount(), p.getPfNumber(),
                            p.getUan(), p.getEsiNumber(), p.getHiredOn(), p.getDepartment(), p.getLocation()));
        }
    }

    /** What a month costs: how many slips, the total net pay, how much of it is paid and how much is still owed. */
    public record MonthSummary(String month, int slips, BigDecimal totalNet, BigDecimal paid, BigDecimal toPay,
                               int pending, int approved, int paidCount) {
    }

    public record MonthView(MonthSummary summary, List<SlipView> slips) {
    }

    public record YearView(int year, BigDecimal totalNet, BigDecimal paid, BigDecimal toPay, List<MonthSummary> months) {
    }

    // ------------------------------------------------------------------ structures

    @Transactional(readOnly = true)
    public List<StaffRow> staff() {
        Map<Long, StaffPay> saved = new LinkedHashMap<>();
        structures.findAll().forEach(s -> saved.put(s.getUserId(), s));
        Map<Long, List<com.itilms.finance.entity.StaffPayHead>> headsByUser = heads.findAll().stream()
                .sorted(Comparator.comparingInt(com.itilms.finance.entity.StaffPayHead::getSortOrder)
                        .thenComparing(com.itilms.finance.entity.StaffPayHead::getId))
                .collect(java.util.stream.Collectors.groupingBy(com.itilms.finance.entity.StaffPayHead::getUserId));
        List<StaffRow> rows = new ArrayList<>();
        List<StaffDirectoryClient.Staff> people = listStaff();
        if (people != null) {
            for (StaffDirectoryClient.Staff p : people) {
                StaffPay s = saved.remove(p.id());
                rows.add(s == null
                        ? new StaffRow(p.id(), p.fullName(), p.email(), p.roleDisplayName(), BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, true, false, List.of(), new Profile(null, null, null, null, null,
                        null, null, null, null, null))
                        : row(s, p.email(), headsByUser.getOrDefault(p.id(), List.of())));
            }
        }
        // Someone on payroll who is no longer in the directory (left, or the directory is unreachable) stays visible.
        saved.values().forEach(s -> rows.add(row(s, null, headsByUser.getOrDefault(s.getUserId(), List.of()))));
        rows.sort(Comparator.comparing(StaffRow::name, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    @Transactional
    public StaffRow saveStructure(Long userId, StructureRequest request) {
        // The heads, or (for an older caller) the three plain numbers turned into heads.
        List<PayHeads.Head> input = new ArrayList<>();
        if (request.heads() != null && !request.heads().isEmpty()) {
            request.heads().forEach(h -> input.add(new PayHeads.Head(h.kind() == null ? "" : h.kind().trim().toUpperCase(java.util.Locale.ROOT),
                    h.name(), h.amount(), h.percentOfBasic(), h.basic())));
        } else {
            input.add(new PayHeads.Head("EARNING", "Basic", money(request.basic()), null, true));
            if (money(request.allowances()).signum() > 0) {
                input.add(new PayHeads.Head("EARNING", "Allowances", money(request.allowances()), null, false));
            }
            if (money(request.fixedDeduction()).signum() > 0) {
                input.add(new PayHeads.Head("DEDUCTION", "Fixed deduction", money(request.fixedDeduction()), null, false));
            }
        }
        PayHeads.Resolved resolved = PayHeads.resolve(input);

        StaffPay pay = structures.findById(userId).orElse(null);
        String email = null;
        if (pay == null) {
            List<StaffDirectoryClient.Staff> people = listStaff();
            StaffDirectoryClient.Staff person = people == null ? null
                    : people.stream().filter(p -> p.id().equals(userId)).findFirst().orElse(null);
            if (person == null) {
                throw new ResourceNotFoundException("Staff member", userId);
            }
            pay = StaffPay.builder().userId(userId).staffName(person.fullName()).roleName(person.roleDisplayName()).build();
            email = person.email();
        }
        pay.setBasic(resolved.basic());
        pay.setAllowances(resolved.allowances());
        pay.setFixedDeduction(resolved.deductions());
        if (request.active() != null) {
            pay.setActive(request.active());
        }
        if (request.profile() != null) {
            applyProfile(pay, request.profile());
        }
        pay = structures.save(pay);

        heads.deleteByUserId(userId);
        heads.flush();
        int order = 0;
        for (PayHeads.Head h : input) {
            heads.save(com.itilms.finance.entity.StaffPayHead.builder().userId(userId).kind(h.kind()).name(h.name().trim())
                    .amount(h.percentOfBasic() != null ? BigDecimal.ZERO : money(h.amount())).percentOfBasic(h.percentOfBasic())
                    .basic(h.basic()).sortOrder(order++).build());
        }
        history.save(com.itilms.finance.entity.StaffPayHistory.builder().userId(userId).basic(pay.getBasic())
                .allowances(pay.getAllowances()).fixedDeduction(pay.getFixedDeduction()).active(pay.isActive()).build());
        events.audit(SERVICE_NAME, "STAFF_PAY_SET", "StaffPay", userId, null,
                Map.of("basic", pay.getBasic().toPlainString(), "allowances", pay.getAllowances().toPlainString(),
                        "deductions", pay.getFixedDeduction().toPlainString(), "heads", input.size()));
        return row(pay, email, heads.findByUserIdOrderBySortOrderAscIdAsc(userId));
    }

    private static void applyProfile(StaffPay pay, Profile p) {
        pay.setEmployeeCode(clean(p.employeeCode(), 40));
        String pan = clean(p.pan(), 10);
        if (pan != null) {
            pan = pan.toUpperCase(java.util.Locale.ROOT);
            if (!pan.matches("^[A-Z]{5}[0-9]{4}[A-Z]$")) {
                throw new BusinessRuleException("The PAN must be 10 characters, like ABCDE1234F.");
            }
        }
        pay.setPan(pan);
        pay.setBankName(clean(p.bankName(), 80));
        pay.setBankAccount(clean(p.bankAccount(), 30));
        pay.setPfNumber(clean(p.pfNumber(), 40));
        pay.setUan(clean(p.uan(), 20));
        pay.setEsiNumber(clean(p.esiNumber(), 30));
        pay.setHiredOn(p.hiredOn());
        pay.setDepartment(clean(p.department(), 80));
        pay.setLocation(clean(p.location(), 120));
    }

    private static String clean(String v, int max) {
        if (v == null || v.isBlank()) {
            return null;
        }
        String t = v.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    /** Every structure this person has had, newest first, with who set it and when. */
    @Transactional(readOnly = true)
    public List<HistoryEntry> history(Long userId) {
        return history.findByUserIdOrderByIdDesc(userId).stream()
                .map(h -> new HistoryEntry(h.getBasic(), h.getAllowances(), h.getFixedDeduction(), h.isActive(),
                        h.getCreatedBy(), h.getCreatedAt()))
                .toList();
    }

    // ------------------------------------------------------------------ the month

    /** Makes a slip for everyone on payroll who does not have one for this month yet. Returns how many were made. */
    @Transactional
    public int generate(String month) {
        YearMonth ym = parse(month);
        if (ym.isAfter(YearMonth.now(props.getZone()))) {
            throw new BusinessRuleException("A month that has not started yet cannot be paid.");
        }
        int made = 0;
        for (StaffPay pay : structures.findByActiveTrue()) {
            if (pay.getBasic().add(pay.getAllowances()).signum() <= 0
                    || slips.findByUserIdAndMonthKey(pay.getUserId(), month).isPresent()) {
                continue;
            }
            // The heads as they are today; a person set up before heads existed has the three plain numbers.
            List<com.itilms.finance.entity.StaffPayHead> saved = heads.findByUserIdOrderBySortOrderAscIdAsc(pay.getUserId());
            List<PayHeads.Head> input = saved.isEmpty()
                    ? List.of(new PayHeads.Head("EARNING", "Basic", pay.getBasic(), null, true),
                    new PayHeads.Head("EARNING", "Allowances", pay.getAllowances(), null, false),
                    new PayHeads.Head("DEDUCTION", "Fixed deduction", pay.getFixedDeduction(), null, false))
                    : saved.stream().map(h -> new PayHeads.Head(h.getKind(), h.getName(), h.getAmount(), h.getPercentOfBasic(),
                    h.isBasic())).toList();
            PayHeads.Resolved resolved = PayHeads.resolve(input.stream()
                    .filter(h -> h.percentOfBasic() != null || h.amount().signum() > 0 || h.basic()).toList());
            PayslipMath.Result r = PayslipMath.compute(resolved.basic(), resolved.allowances(), resolved.deductions(),
                    BigDecimal.ZERO, ym.lengthOfMonth(), BigDecimal.ZERO, BigDecimal.ZERO);
            Payslip slip = slips.save(Payslip.builder().slipNo(nextSlipNo(ym)).monthKey(month).userId(pay.getUserId())
                    .staffName(pay.getStaffName()).designation(pay.getRoleName()).daysInMonth(ym.lengthOfMonth())
                    .basic(resolved.basic()).allowances(resolved.allowances()).fixedDeduction(resolved.deductions())
                    .lwpDeduction(r.lwpDeduction()).gross(r.gross()).net(r.net())
                    .employeeCode(pay.getEmployeeCode()).pan(pay.getPan()).bankName(pay.getBankName())
                    .bankAccount(pay.getBankAccount()).pfNumber(pay.getPfNumber()).uan(pay.getUan())
                    .esiNumber(pay.getEsiNumber()).hiredOn(pay.getHiredOn()).department(pay.getDepartment())
                    .location(pay.getLocation()).build());
            int order = 0;
            for (PayHeads.Line line : resolved.lines()) {
                items.save(com.itilms.finance.entity.PayslipItem.builder().slipId(slip.getId()).kind(line.kind())
                        .name(line.name()).amount(line.amount()).sortOrder(order++).build());
            }
            made++;
        }
        if (made > 0) {
            events.audit(SERVICE_NAME, "PAYROLL_GENERATED", "Payroll", null, null, Map.of("month", month, "slips", made));
        }
        return made;
    }

    @Transactional(readOnly = true)
    public MonthView month(String month) {
        parse(month);
        List<Payslip> rows = slips.findByMonthKeyOrderByStaffNameAsc(month);
        return new MonthView(summarise(month, rows), views(rows));
    }

    @Transactional(readOnly = true)
    public YearView year(int year) {
        List<Payslip> rows = slips.findByMonthKeyStartingWithOrderByMonthKeyAsc(year + "-");
        Map<String, List<Payslip>> byMonth = new LinkedHashMap<>();
        rows.forEach(p -> byMonth.computeIfAbsent(p.getMonthKey(), k -> new ArrayList<>()).add(p));
        List<MonthSummary> months = byMonth.entrySet().stream().map(e -> summarise(e.getKey(), e.getValue())).toList();
        BigDecimal net = months.stream().map(MonthSummary::totalNet).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paid = months.stream().map(MonthSummary::paid).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new YearView(year, net, paid, net.subtract(paid), months);
    }

    static MonthSummary summarise(String month, List<Payslip> rows) {
        BigDecimal net = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        int pending = 0;
        int approved = 0;
        int paidCount = 0;
        for (Payslip p : rows) {
            net = net.add(p.getNet());
            switch (p.getStatus()) {
                case PENDING -> pending++;
                case APPROVED -> approved++;
                case PAID -> {
                    paidCount++;
                    paid = paid.add(p.getNet());
                }
            }
        }
        return new MonthSummary(month, rows.size(), net, paid, net.subtract(paid), pending, approved, paidCount);
    }

    // ------------------------------------------------------------------ one slip

    @Transactional
    public SlipView update(Long id, SlipRequest request) {
        Payslip slip = require(id);
        if (slip.getStatus() != Payslip.Status.PENDING) {
            throw new BusinessRuleException("Only a slip that is not yet approved can be changed.");
        }
        BigDecimal lwp = request.lwpDays() == null ? BigDecimal.ZERO : request.lwpDays().setScale(1, RoundingMode.HALF_UP);
        BigDecimal other = money(request.otherDeduction());
        BigDecimal bonus = money(request.bonus());
        PayslipMath.Result r = PayslipMath.compute(slip.getBasic(), slip.getAllowances(), slip.getFixedDeduction(), lwp,
                slip.getDaysInMonth(), other, bonus);
        slip.setLwpDays(lwp);
        slip.setLwpDeduction(r.lwpDeduction());
        slip.setOtherDeduction(other);
        slip.setBonus(bonus);
        slip.setGross(r.gross());
        slip.setNet(r.net());
        slip.setNote(request.note() == null || request.note().isBlank() ? null : request.note().trim());
        return views(List.of(slips.save(slip))).get(0);
    }

    @Transactional
    public int approve(String month, Long onlySlip) {
        AppPrincipal admin = SecurityUtils.requirePrincipal();
        int done = 0;
        for (Payslip slip : target(month, onlySlip)) {
            if (slip.getStatus() == Payslip.Status.PENDING) {
                slip.setStatus(Payslip.Status.APPROVED);
                slip.setApprovedBy(admin.userId());
                slip.setApprovedAt(java.time.Instant.now());
                slips.save(slip);
                done++;
            }
        }
        if (done > 0) {
            events.audit(SERVICE_NAME, "PAYROLL_APPROVED", "Payroll", onlySlip, null,
                    Map.of("month", month == null ? "" : month, "slips", done));
        }
        return done;
    }

    @Transactional
    public int pay(String month, Long onlySlip, PayRequest request) {
        String method = request.method() == null ? "" : request.method().trim().toUpperCase(java.util.Locale.ROOT);
        if (!PAY_METHODS.contains(method)) {
            throw new BusinessRuleException("Choose how it was paid: cash, UPI, bank transfer or cheque.");
        }
        LocalDate today = LocalDate.now(props.getZone());
        LocalDate paidOn = request.paidOn() != null ? request.paidOn() : today;
        booksLock.requireOpen(paidOn, "a salary payment");
        int done = 0;
        for (Payslip slip : target(month, onlySlip)) {
            if (slip.getStatus() != Payslip.Status.APPROVED) {
                continue;
            }
            Expense.Category category = Expense.Category.SALARY;
            var expense = expenses.add(new ExpenseService.ExpenseRequest(paidOn, category, slip.getNet(),
                    "Salary " + slip.getMonthKey() + " - " + slip.getStaffName(), slip.getStaffName(),
                    request.reference() == null || request.reference().isBlank() ? slip.getSlipNo() : request.reference().trim()));
            slip.setStatus(Payslip.Status.PAID);
            slip.setPaidOn(paidOn);
            slip.setPayMethod(method);
            slip.setPayReference(request.reference() == null || request.reference().isBlank() ? null : request.reference().trim());
            slip.setExpenseId(expense.id());
            slips.save(slip);
            done++;
            events.notifyUsers(List.of(slip.getUserId()), "SALARY", "Salary slip ready",
                    "Your salary for " + slip.getMonthKey() + " has been paid. You can download the slip.", "/app/my-salary");
        }
        if (done > 0) {
            events.audit(SERVICE_NAME, "PAYROLL_PAID", "Payroll", onlySlip, null,
                    Map.of("month", month == null ? "" : month, "slips", done, "method", method));
        }
        return done;
    }

    /** The signed-in person's own paid slips, newest first. */
    @Transactional(readOnly = true)
    public List<SlipView> mine() {
        Long me = SecurityUtils.requirePrincipal().userId();
        return slips.findByUserIdAndStatusOrderByMonthKeyDesc(me, Payslip.Status.PAID).stream().collect(java.util.stream.Collectors.collectingAndThen(
                java.util.stream.Collectors.toList(), this::views));
    }

    /** The slips as the screens see them, each with its heads. */
    private List<SlipView> views(List<Payslip> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, List<SlipItemView>> byslip = items.findBySlipIdInOrderBySortOrderAscIdAsc(
                        rows.stream().map(Payslip::getId).toList()).stream()
                .collect(java.util.stream.Collectors.groupingBy(com.itilms.finance.entity.PayslipItem::getSlipId,
                        java.util.stream.Collectors.mapping(i -> new SlipItemView(i.getKind(), i.getName(), i.getAmount()),
                                java.util.stream.Collectors.toList())));
        return rows.stream().map(p -> SlipView.from(p, byslip.getOrDefault(p.getId(), List.of()))).toList();
    }

    /** A slip the caller may read: the finance desk any, a member of staff only their own paid slip. */
    @Transactional(readOnly = true)
    public Payslip readable(Long id) {
        Payslip slip = require(id);
        AppPrincipal caller = SecurityUtils.requirePrincipal();
        boolean desk = caller.hasRole("ADMIN") || caller.hasRole("FINANCE");
        if (!desk && !(slip.getUserId().equals(caller.userId()) && slip.getStatus() == Payslip.Status.PAID)) {
            throw new ResourceNotFoundException("Salary slip", id);
        }
        return slip;
    }

    // ------------------------------------------------------------------ helpers

    private List<Payslip> target(String month, Long onlySlip) {
        if (onlySlip != null) {
            return List.of(require(onlySlip));
        }
        parse(month);
        return slips.findByMonthKeyOrderByStaffNameAsc(month);
    }

    private Payslip require(Long id) {
        return slips.findById(id).orElseThrow(() -> new ResourceNotFoundException("Salary slip", id));
    }

    private List<StaffDirectoryClient.Staff> listStaff() {
        try {
            return directory.staff();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String nextSlipNo(YearMonth ym) {
        return "SAL-%d%02d-%04d".formatted(ym.getYear(), ym.getMonthValue(), slips.nextSequence());
    }

    static YearMonth parse(String month) {
        try {
            return YearMonth.parse(month);
        } catch (RuntimeException e) {
            throw new BusinessRuleException("The month must look like 2026-11.");
        }
    }

    private static BigDecimal money(BigDecimal v) {
        BigDecimal value = v == null ? BigDecimal.ZERO : v.setScale(2, RoundingMode.HALF_UP);
        if (value.signum() < 0) {
            throw new BusinessRuleException("An amount cannot be negative.");
        }
        return value;
    }

    private static StaffRow row(StaffPay s, String email, List<com.itilms.finance.entity.StaffPayHead> heads) {
        return new StaffRow(s.getUserId(), s.getStaffName(), email, s.getRoleName(), s.getBasic(), s.getAllowances(),
                s.getFixedDeduction(), s.isActive(), s.getBasic().add(s.getAllowances()).signum() > 0,
                heads.stream().map(h -> new HeadRow(h.getKind(), h.getName(), h.getAmount(), h.getPercentOfBasic(), h.isBasic())).toList(),
                new Profile(s.getEmployeeCode(), s.getPan(), s.getBankName(), s.getBankAccount(), s.getPfNumber(), s.getUan(),
                        s.getEsiNumber(), s.getHiredOn(), s.getDepartment(), s.getLocation()));
    }
}

package com.itilms.finance.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.finance.config.FinanceProperties;
import com.itilms.finance.entity.Payment;
import com.itilms.finance.ledger.GstMath;
import com.itilms.finance.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;

/**
 * The tax collected on fees, month by month, for the GST return: the taxable value and the CGST and SGST in the
 * payments received (net of refunds), taking the fee as tax-inclusive. Off (and empty) while the GST rate is zero.
 */
@Service
@RequiredArgsConstructor
public class GstReportService {

    private final PaymentRepository payments;
    private final FinanceProperties props;

    public record Month(String month, int payments, BigDecimal received, BigDecimal taxable, BigDecimal cgst,
                        BigDecimal sgst) {
    }

    public record Report(boolean enabled, BigDecimal ratePercent, String gstin, LocalDate from, LocalDate to,
                         BigDecimal received, BigDecimal taxable, BigDecimal cgst, BigDecimal sgst, List<Month> months) {
    }

    @Transactional(readOnly = true)
    public Report report(LocalDate from, LocalDate to) {
        boolean on = props.getGstPercent() != null && props.getGstPercent().signum() > 0;
        if (!on) {
            return new Report(false, BigDecimal.ZERO, props.getGstin(), from, to, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, List.of());
        }
        return summarise(props.getGstPercent(), props.getGstin(), from, to,
                payments.findByPaymentDateBetweenAndStatus(from, to, com.itilms.finance.entity.PaymentStatus.SUCCESS));
    }

    /** Pure, so the figures can be tested. */
    static Report summarise(BigDecimal rate, String gstin, LocalDate from, LocalDate to, List<Payment> rows) {
        Map<YearMonth, List<Payment>> byMonth = new TreeMap<>();
        rows.forEach(p -> byMonth.computeIfAbsent(YearMonth.from(p.getPaymentDate()), k -> new ArrayList<>()).add(p));
        List<Month> months = new ArrayList<>();
        BigDecimal received = BigDecimal.ZERO;
        BigDecimal taxable = BigDecimal.ZERO;
        BigDecimal cgst = BigDecimal.ZERO;
        BigDecimal sgst = BigDecimal.ZERO;
        for (Map.Entry<YearMonth, List<Payment>> e : byMonth.entrySet()) {
            BigDecimal mReceived = BigDecimal.ZERO;
            BigDecimal mTaxable = BigDecimal.ZERO;
            BigDecimal mCgst = BigDecimal.ZERO;
            BigDecimal mSgst = BigDecimal.ZERO;
            for (Payment p : e.getValue()) {
                GstMath.Split split = GstMath.split(p.netAmount(), rate);
                mReceived = mReceived.add(p.netAmount());
                mTaxable = mTaxable.add(split.taxable());
                mCgst = mCgst.add(split.cgst());
                mSgst = mSgst.add(split.sgst());
            }
            months.add(new Month(e.getKey().toString(), e.getValue().size(), mReceived, mTaxable, mCgst, mSgst));
            received = received.add(mReceived);
            taxable = taxable.add(mTaxable);
            cgst = cgst.add(mCgst);
            sgst = sgst.add(mSgst);
        }
        return new Report(true, rate, gstin, from, to, received, taxable, cgst, sgst, months);
    }
}

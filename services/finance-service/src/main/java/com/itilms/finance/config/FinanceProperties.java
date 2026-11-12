package com.itilms.finance.config;

import java.time.ZoneId;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/** The institute's fee settings (config-repo/finance-service.yml). */
@Getter
@Setter
@ConfigurationProperties(prefix = "itilms.finance")
public class FinanceProperties {

    private String currency = "INR";

    private String currencySymbol = "Rs.";

    /** Days before a due date on which the student is reminded. */
    private List<Integer> dueReminderDays = List.of(7, 3, 1);

    private String receiptPrefix = "RCPT";

    /** GST on fees, as a percent. The fee is taken to include it. Zero (the default) means no GST is shown or reported. */
    private java.math.BigDecimal gstPercent = java.math.BigDecimal.ZERO;

    /** The institute's GST number and the service accounting code, printed on the receipt when GST is on. */
    private String gstin = "";

    private String sacCode = "";

    /** The institute's UPI id students can pay to, such as "institute@okbank". Empty switches UPI payment off. */
    private String upiId = "";

    /** The name shown in the student's UPI app when they scan the code. */
    private String upiName = "";

    /** The late fee added to an installment that stays unpaid after its grace period. Zero switches late fees off. */
    private java.math.BigDecimal lateFee = java.math.BigDecimal.ZERO;

    /** Days after the due date before the late fee is added. */
    private int lateFeeAfterDays = 7;

    /** A concession above this share of the course fee can only be given by the administrator. */
    private int discountApprovalPercent = 10;

    /** The bank line printed on a receipt, such as "CANARA BANK, JALGAON - 1647201001212". Empty prints nothing. */
    private String bank = "";

    /**
     * The timezone "today" is judged in.
     *
     * <p>Whether an installment is overdue is a question about the date, and a
     * container running in UTC would otherwise call a fee due on the 1st
     * overdue at 5:30 in the morning on the 1st in India.
     */
    private ZoneId zone = ZoneId.of("Asia/Kolkata");
}

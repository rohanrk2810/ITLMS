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

    /**
     * The timezone "today" is judged in.
     *
     * <p>Whether an installment is overdue is a question about the date, and a
     * container running in UTC would otherwise call a fee due on the 1st
     * overdue at 5:30 in the morning on the 1st in India.
     */
    private ZoneId zone = ZoneId.of("Asia/Kolkata");
}

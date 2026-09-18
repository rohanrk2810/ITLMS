package com.itilms.finance.service;

import java.util.List;

import com.itilms.finance.dto.response.FinanceDashboardResponse;
import com.itilms.finance.dto.response.OverdueItemResponse;

/** The finance desk's view of the whole institute (Doc S15). */
public interface FinanceReportService {

    FinanceDashboardResponse dashboard();

    /** Every overdue installment, longest overdue first - the chase list. */
    List<OverdueItemResponse> overdue();
}

package com.java.erp.modules.fees.dto.response;

import com.java.erp.modules.fees.entity.StudentFeeAccount;

import java.math.BigDecimal;
import java.util.Map;

/** Department-wide totals. outstanding = unpaid balances of UNPAID and PARTIALLY_PAID charges. */
public record FeeSummaryResponse(
        long accounts,
        BigDecimal totalCharged,
        BigDecimal totalPaid,
        BigDecimal totalWaived,
        BigDecimal totalOutstanding,
        BigDecimal overdueOutstanding,
        Map<StudentFeeAccount.Status, Long> accountsByStatus
) {
}

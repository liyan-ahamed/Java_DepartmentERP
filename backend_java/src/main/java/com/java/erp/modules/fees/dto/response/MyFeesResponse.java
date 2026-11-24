package com.java.erp.modules.fees.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * The logged-in student's fee charges and totals (waived balances are not outstanding).
 * totalCharged = sum of actual amounts. years = the same charges grouped by fee year with ready-made totals
 * and the item breakdown, so the frontend never recalculates.
 */
public record MyFeesResponse(
        BigDecimal totalCharged,
        BigDecimal totalPaid,
        BigDecimal totalOutstanding,
        List<FeeAccountResponse> accounts,
        BigDecimal totalConcession,
        BigDecimal totalPayable,
        List<FeeYearSummary> years
) {
}

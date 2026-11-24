package com.java.erp.modules.fees.dto.response;

import com.java.erp.modules.fees.entity.FeeStructure;
import com.java.erp.modules.fees.entity.StudentFeeAccount;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * One student's fee charges of one fee year with totals. year = 1-4 for the "Year N" template; null groups
 * term charges that do not belong to a year template (label "Other fees").
 *   payable = actual - concession; balance = payable - paid (0 for a WAIVED charge).
 */
public record FeeYearSummary(
        Short year,
        String label,
        BigDecimal totalActualAmount,
        BigDecimal totalConcessionAmount,
        BigDecimal totalPayableAmount,
        BigDecimal totalPaidAmount,
        BigDecimal totalBalanceAmount,
        List<Item> itemBreakdown
) {
    public record Item(
            Long feeAccountId,
            Long feeStructureId,
            String particular,
            FeeStructure.FeeType feeType,
            BigDecimal actualAmount,
            BigDecimal concessionAmount,
            BigDecimal payableAmount,
            BigDecimal paidAmount,
            BigDecimal balanceAmount,
            StudentFeeAccount.Status status,
            LocalDate dueDate
    ) {
        public static Item from(StudentFeeAccount a) {
            FeeStructure f = a.getFeeStructure();
            return new Item(a.getId(), f.getId(), f.getName(), f.getFeeType(), a.getAmount(), a.getConcessionAmount(),
                    a.payable(), a.getPaidAmount(), a.balance(), a.getStatus(), a.getDueDate());
        }
    }
}

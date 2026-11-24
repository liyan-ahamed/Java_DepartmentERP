package com.java.erp.modules.fees.dto.response;

import com.java.erp.modules.fees.entity.FeeStructure;

import java.math.BigDecimal;
import java.util.List;

/**
 * The fee template of one student year (1-4): the active charges every student of that year receives.
 * configured = false means no fee structure is configured for that year (items empty, totals null).
 * Default concession is always 0.00, so default payable = total actual.
 */
public record FeeTemplateResponse(
        short year,
        String label,
        boolean configured,
        BigDecimal totalActualAmount,
        BigDecimal defaultConcessionAmount,
        BigDecimal defaultPayableAmount,
        List<Item> items
) {
    public record Item(Long feeStructureId, String particular, FeeStructure.FeeType feeType, BigDecimal actualAmount,
                       long assignedCount) {
    }
}

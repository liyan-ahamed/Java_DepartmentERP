package com.java.erp.modules.fees.dto.response;

public record AssignFeeResponse(
        Long feeStructureId,
        int targetedStudents,
        int assigned,
        int alreadyAssigned
) {
}

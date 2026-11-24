package com.java.erp.modules.fees.dto.response;

import com.java.erp.modules.fees.entity.FeeStructure;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record FeeStructureResponse(
        Long id,
        String name,
        FeeStructure.FeeType feeType,
        String academicYear,
        Integer semester,
        Long batchId,
        String batchName,
        BigDecimal amount,
        LocalDate dueDate,
        boolean active,
        long assignedCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        /** 1-4 for a charge of the "Year N" template, else null. */
        Short studentYear
) {
    public static FeeStructureResponse from(FeeStructure f, long assignedCount) {
        return new FeeStructureResponse(f.getId(), f.getName(), f.getFeeType(), f.getAcademicYear(), f.getSemester(),
                f.getBatch() == null ? null : f.getBatch().getId(), f.getBatch() == null ? null : f.getBatch().getBatchName(),
                f.getAmount(), f.getDueDate(), f.isActive(), assignedCount, f.getCreatedAt(), f.getUpdatedAt(),
                f.getStudentYear());
    }
}

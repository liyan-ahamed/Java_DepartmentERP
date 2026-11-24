package com.java.erp.modules.fees.dto.request;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Who gets the charge: the listed students, else every active student of sectionId, else every
 * active student of the fee structure's batch. dueDate overrides the structure's due date.
 */
public record AssignFeeRequest(
        @Size(max = 1000) List<Long> studentIds,
        Long sectionId,
        LocalDate dueDate
) {
}

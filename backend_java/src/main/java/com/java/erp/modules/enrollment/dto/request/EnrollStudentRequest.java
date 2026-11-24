package com.java.erp.modules.enrollment.dto.request;

import jakarta.validation.constraints.NotNull;

public record EnrollStudentRequest(
        @NotNull Long studentId,
        @NotNull Long subjectOfferingId
) {
}

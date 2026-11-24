package com.java.erp.modules.enrollment.dto.request;

import com.java.erp.modules.enrollment.entity.CourseEnrollment;
import jakarta.validation.constraints.NotNull;

/** Allowed changes: ENROLLED -> DROPPED, DROPPED -> ENROLLED (reactivate), ENROLLED -> COMPLETED. */
public record UpdateEnrollmentStatusRequest(
        @NotNull CourseEnrollment.Status status
) {
}

package com.java.erp.modules.academic.service;

import com.java.erp.common.util.SecurityUtils;
import com.java.erp.exception.BadRequestException;
import com.java.erp.exception.ForbiddenException;
import com.java.erp.modules.academic.entity.SubjectOffering;

/**
 * Resource-level rules for subject offerings and everything that hangs off them
 * (assessments, grades, analytics, internal marks):
 *  - HOD may view every offering but does not modify grading data.
 *  - STAFF may view and manage only offerings assigned to them.
 * Role checks on the controllers keep STUDENT users out of these endpoints entirely.
 */
public final class OfferingAccess {

    private OfferingAccess() {
    }

    public static boolean isHod() {
        return SecurityUtils.hasAuthority("ROLE_HOD");
    }

    public static boolean isAssignedStaff(SubjectOffering offering) {
        Long userId = SecurityUtils.getCurrentUserId();
        return userId != null && SecurityUtils.hasAuthority("ROLE_STAFF")
                && offering.getStaff().getId().equals(userId);
    }

    public static void requireView(SubjectOffering offering) {
        if (!isHod() && !isAssignedStaff(offering)) {
            throw new ForbiddenException("This subject offering is not assigned to you");
        }
    }

    /** Assessment and grade changes: only the assigned staff member, and only while the offering is active. */
    public static void requireManage(SubjectOffering offering) {
        if (!isAssignedStaff(offering)) {
            throw new ForbiddenException("Only the staff member assigned to this subject offering can change its assessments");
        }
        if (!offering.isActive()) {
            throw new BadRequestException("This subject offering is inactive");
        }
    }
}

package com.java.erp.modules.portfolio.service;

import com.java.erp.common.util.SecurityUtils;
import com.java.erp.exception.ForbiddenException;
import com.java.erp.modules.academic.repository.SubjectOfferingRepository;
import com.java.erp.modules.system.entity.Student;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Which students' portfolio entries the current reviewer may see and decide on:
 *  - HOD: every student in the department.
 *  - STAFF: students of the sections they currently teach (active subject offerings).
 * Role checks on the controller keep STUDENT users out of the review endpoints.
 */
@Component
public class PortfolioReviewScope {

    private final SubjectOfferingRepository offerings;

    public PortfolioReviewScope(SubjectOfferingRepository offerings) {
        this.offerings = offerings;
    }

    public record Scope(boolean allSections, Set<Long> sectionIds) {

        public boolean includes(Student student) {
            return allSections || sectionIds.contains(student.getSection().getId());
        }

        /** Section ids for "IN" queries, which must not be empty (no section matches id -1). */
        public List<Long> queryIds() {
            return sectionIds.isEmpty() ? List.of(-1L) : List.copyOf(sectionIds);
        }
    }

    public Scope current() {
        if (SecurityUtils.hasAuthority("ROLE_HOD")) {
            return new Scope(true, Set.of());
        }
        if (SecurityUtils.hasAuthority("ROLE_STAFF")) {
            return new Scope(false, Set.copyOf(offerings.findActiveSectionIdsByStaffId(SecurityUtils.getCurrentUserId())));
        }
        return new Scope(false, Set.of());
    }

    public void require(Scope scope, Student student) {
        if (!scope.includes(student)) {
            throw new ForbiddenException("You can only review portfolio entries of students in sections you teach");
        }
    }
}

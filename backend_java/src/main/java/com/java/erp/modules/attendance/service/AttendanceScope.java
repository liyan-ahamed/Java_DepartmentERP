package com.java.erp.modules.attendance.service;

import com.java.erp.common.util.SecurityUtils;
import com.java.erp.exception.BadRequestException;
import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.academic.repository.SubjectOfferingRepository;
import com.java.erp.modules.academic.service.OfferingAccess;
import com.java.erp.modules.enrollment.service.EnrollmentRoster;
import com.java.erp.modules.gradebook.service.CurrentStudentResolver;
import com.java.erp.modules.system.entity.Section;
import com.java.erp.modules.system.entity.Student;
import com.java.erp.modules.system.repository.SectionRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Which active subject offerings the current user may see in the attendance day / class views:
 *   HOD     - one class: sectionId is required (batchId and semester narrow it further)
 *   STAFF   - only offerings assigned to them (filters optional; there is no staffId parameter)
 *   STUDENT - only offerings they are currently ENROLLED in (filters ignored; never another student)
 */
@Component
public class AttendanceScope {

    private final SubjectOfferingRepository offerings;
    private final SectionRepository sections;
    private final EnrollmentRoster roster;
    private final CurrentStudentResolver currentStudent;

    public AttendanceScope(SubjectOfferingRepository offerings, SectionRepository sections, EnrollmentRoster roster,
                           CurrentStudentResolver currentStudent) {
        this.offerings = offerings;
        this.sections = sections;
        this.roster = roster;
        this.currentStudent = currentStudent;
    }

    public static boolean isStudent() {
        return SecurityUtils.hasAuthority("ROLE_STUDENT");
    }

    /** The logged-in student (STUDENT role only). */
    public Student student() {
        return currentStudent.require();
    }

    public List<SubjectOffering> visibleOfferings(Long batchId, Integer semester, Long sectionId) {
        if (isStudent()) {
            List<Long> ids = roster.offeringIds(currentStudent.require().getId(), EnrollmentRoster.CURRENT);
            if (ids.isEmpty()) return List.of();
            return sorted(offerings.findByIdIn(ids).stream().filter(SubjectOffering::isActive).toList());
        }
        Long staffId = null;
        if (OfferingAccess.isHod()) {
            if (sectionId == null) {
                throw new BadRequestException("sectionId is required (choose cohort, semester and section)");
            }
            Section section = sections.findById(sectionId)
                    .orElseThrow(() -> new BadRequestException("Section " + sectionId + " does not exist"));
            if (batchId != null && !section.getBatch().getId().equals(batchId)) {
                throw new BadRequestException("Section " + sectionId + " does not belong to batch " + batchId);
            }
        } else {
            staffId = SecurityUtils.getCurrentUserId();
        }
        Long ownStaffId = staffId;
        Specification<SubjectOffering> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.isTrue(root.get("active")));
            if (ownStaffId != null) p.add(cb.equal(root.get("staff").get("id"), ownStaffId));
            if (batchId != null) p.add(cb.equal(root.get("batch").get("id"), batchId));
            if (semester != null) p.add(cb.equal(root.get("semester"), semester));
            if (sectionId != null) p.add(cb.equal(root.get("section").get("id"), sectionId));
            return cb.and(p.toArray(Predicate[]::new));
        };
        return sorted(offerings.findAll(spec));
    }

    private static List<SubjectOffering> sorted(List<SubjectOffering> list) {
        return list.stream().sorted(Comparator.comparing((SubjectOffering o) -> o.getSection().getId())
                .thenComparing(o -> o.getSubject().getCode())).toList();
    }
}

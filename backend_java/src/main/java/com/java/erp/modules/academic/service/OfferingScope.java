package com.java.erp.modules.academic.service;

import com.java.erp.common.util.SecurityUtils;
import com.java.erp.exception.BadRequestException;
import com.java.erp.exception.ResourceNotFoundException;
import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.academic.repository.SubjectOfferingRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The subject offerings ("classes") of one cohort + semester that the current user may see: HOD all, STAFF only
 * those assigned to them. Active and inactive offerings are both included, so earlier semesters stay readable.
 * Used by the read-only results view and the assessment overview.
 */
@Component
public class OfferingScope {

    private static final Comparator<SubjectOffering> ORDER = Comparator
            .comparing((SubjectOffering o) -> o.getSubject().getCode())
            .thenComparing(o -> o.getSection().getSectionName())
            .thenComparing(SubjectOffering::getId);

    private final SubjectOfferingRepository offerings;

    public OfferingScope(SubjectOfferingRepository offerings) {
        this.offerings = offerings;
    }

    /**
     * Offerings of batchId + semester, optionally one section or one offering, by subject code then section.
     * An explicit offering must exist (404), be visible to the user (403) and belong to the cohort + semester (400).
     */
    public List<SubjectOffering> forTerm(Long batchId, int semester, Long sectionId, Long offeringId) {
        if (offeringId != null) {
            SubjectOffering offering = offerings.findWithDetailsById(offeringId)
                    .orElseThrow(() -> new ResourceNotFoundException("SubjectOffering", "id", offeringId));
            OfferingAccess.requireView(offering);
            if (!offering.getBatch().getId().equals(batchId) || offering.getSemester() != semester) {
                throw new BadRequestException("Subject offering " + offeringId + " is not part of this cohort and semester");
            }
            return sectionId == null || offering.getSection().getId().equals(sectionId) ? List.of(offering) : List.of();
        }
        Long staffId = OfferingAccess.isHod() ? null : SecurityUtils.getCurrentUserId();
        Specification<SubjectOffering> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("batch").get("id"), batchId));
            p.add(cb.equal(root.get("semester"), semester));
            if (sectionId != null) p.add(cb.equal(root.get("section").get("id"), sectionId));
            if (staffId != null) p.add(cb.equal(root.get("staff").get("id"), staffId));
            return cb.and(p.toArray(Predicate[]::new));
        };
        return offerings.findAll(spec).stream().sorted(ORDER).toList();
    }
}

package com.java.erp.modules.academic.service;

import com.java.erp.common.audit.Auditable;
import com.java.erp.common.response.PagedResponse;
import com.java.erp.common.util.SecurityUtils;
import com.java.erp.exception.BadRequestException;
import com.java.erp.exception.ConflictException;
import com.java.erp.exception.ResourceNotFoundException;
import com.java.erp.modules.academic.dto.request.SubjectOfferingRequest;
import com.java.erp.modules.academic.dto.response.SubjectOfferingResponse;
import com.java.erp.modules.academic.entity.Subject;
import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.academic.repository.SubjectOfferingRepository;
import com.java.erp.modules.academic.repository.SubjectRepository;
import com.java.erp.modules.auth.entity.User;
import com.java.erp.modules.auth.repository.UserRepository;
import com.java.erp.modules.gradebook.repository.AssessmentRepository;
import com.java.erp.modules.gradebook.service.InternalMarksService;
import com.java.erp.modules.system.entity.Batch;
import com.java.erp.modules.system.entity.Section;
import com.java.erp.modules.system.repository.BatchRepository;
import com.java.erp.modules.system.repository.SectionRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class SubjectOfferingService {

    private final SubjectOfferingRepository offerings;
    private final SubjectRepository subjects;
    private final BatchRepository batches;
    private final SectionRepository sections;
    private final UserRepository users;
    private final AssessmentRepository assessments;
    private final InternalMarksService internalMarks;

    public SubjectOfferingService(SubjectOfferingRepository offerings, SubjectRepository subjects,
                                  BatchRepository batches, SectionRepository sections, UserRepository users,
                                  AssessmentRepository assessments, InternalMarksService internalMarks) {
        this.offerings = offerings;
        this.subjects = subjects;
        this.batches = batches;
        this.sections = sections;
        this.users = users;
        this.assessments = assessments;
        this.internalMarks = internalMarks;
    }

    @Transactional
    @Auditable(action = "CREATE", module = "ACADEMIC", entityType = "SubjectOffering")
    public SubjectOfferingResponse create(SubjectOfferingRequest request) {
        SubjectOffering offering = new SubjectOffering();
        apply(offering, request, true);
        offering = offerings.saveAndFlush(offering);
        internalMarks.ensureRecords(offering);
        return SubjectOfferingResponse.from(offering);
    }

    /**
     * Staff reassignment and activation can always change. Subject, batch, section, academic
     * year and semester are fixed once the offering has assessments, because grades belong
     * to that context.
     */
    @Transactional
    @Auditable(action = "UPDATE", module = "ACADEMIC", entityType = "SubjectOffering")
    public SubjectOfferingResponse update(Long id, SubjectOfferingRequest request) {
        SubjectOffering offering = offerings.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SubjectOffering", "id", id));
        boolean contextChanged = !offering.getSubject().getId().equals(request.subjectId())
                || !offering.getBatch().getId().equals(request.batchId())
                || !offering.getSection().getId().equals(request.sectionId())
                || !offering.getAcademicYear().equals(request.academicYear().trim())
                || !offering.getSemester().equals(request.semester());
        if (contextChanged && assessments.existsBySubjectOfferingId(id)) {
            throw new ConflictException("Subject, batch, section, academic year and semester cannot change once the offering has assessments");
        }
        boolean sectionChanged = !offering.getSection().getId().equals(request.sectionId());
        // An already-active offering keeps working (e.g. staff reassignment) even if its batch was since retired;
        // re-activating it or moving it to another subject/batch needs active references again.
        apply(offering, request, !offering.isActive() || contextChanged);
        if (sectionChanged) {
            // Only pending, empty rows can exist here (no assessments, no calculation); rebuild them for the new class.
            internalMarks.deleteRecords(id);
        }
        offering = offerings.saveAndFlush(offering);
        internalMarks.ensureRecords(offering);
        return SubjectOfferingResponse.from(offering);
    }

    @Transactional(readOnly = true)
    public SubjectOfferingResponse get(Long id) {
        SubjectOffering offering = offerings.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SubjectOffering", "id", id));
        OfferingAccess.requireView(offering);
        return SubjectOfferingResponse.from(offering);
    }

    /** HOD sees every offering; STAFF only their own (a staffId filter is ignored for STAFF). */
    @Transactional(readOnly = true)
    public PagedResponse<SubjectOfferingResponse> list(Long subjectId, Long batchId, Long sectionId, Long staffId,
                                                       String academicYear, Integer semester, Boolean active,
                                                       int page, int size) {
        Long scopedStaffId = OfferingAccess.isHod() ? staffId : SecurityUtils.getCurrentUserId();
        Specification<SubjectOffering> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (subjectId != null) p.add(cb.equal(root.get("subject").get("id"), subjectId));
            if (batchId != null) p.add(cb.equal(root.get("batch").get("id"), batchId));
            if (sectionId != null) p.add(cb.equal(root.get("section").get("id"), sectionId));
            if (scopedStaffId != null) p.add(cb.equal(root.get("staff").get("id"), scopedStaffId));
            if (academicYear != null) p.add(cb.equal(root.get("academicYear"), academicYear.trim()));
            if (semester != null) p.add(cb.equal(root.get("semester"), semester));
            if (active != null) p.add(cb.equal(root.get("active"), active));
            return cb.and(p.toArray(Predicate[]::new));
        };
        Sort sort = Sort.by(Sort.Order.desc("academicYear"), Sort.Order.desc("semester"), Sort.Order.asc("subject.code"));
        Page<SubjectOffering> result = offerings.findAll(spec, PageRequest.of(page, size, sort));
        return PagedResponse.of(result.getContent().stream().map(SubjectOfferingResponse::from).toList(), result);
    }

    /**
     * Validates the request and copies it onto the offering.
     * checkActiveReferences: the offering will be active, so its subject and batch must be active too.
     */
    private void apply(SubjectOffering offering, SubjectOfferingRequest request, boolean checkActiveReferences) {
        Subject subject = subjects.findById(request.subjectId())
                .orElseThrow(() -> new BadRequestException("Subject " + request.subjectId() + " does not exist"));
        Batch batch = batches.findById(request.batchId())
                .orElseThrow(() -> new BadRequestException("Batch " + request.batchId() + " does not exist"));
        Section section = sections.findById(request.sectionId())
                .orElseThrow(() -> new BadRequestException("Section " + request.sectionId() + " does not exist"));
        User staff = users.findById(request.staffId())
                .orElseThrow(() -> new BadRequestException("Staff member " + request.staffId() + " does not exist"));

        if (!section.getBatch().getId().equals(batch.getId())) {
            throw new BadRequestException("Section " + section.getId() + " does not belong to batch " + batch.getId());
        }
        if (!isActiveStaff(staff)) {
            throw new BadRequestException("The assigned staff member must be an active STAFF user");
        }
        String academicYear = request.academicYear().trim();
        validateAcademicYear(academicYear);

        boolean active = request.active() != null ? request.active() : offering.getId() == null || offering.isActive();
        if (active && checkActiveReferences) {
            if (!subject.isActive()) throw new BadRequestException("Subject " + subject.getCode() + " is inactive");
            if (!batch.isActive()) throw new BadRequestException("Batch " + batch.getBatchName() + " is inactive");
        }
        if (active && offerings.existsBySubjectIdAndSectionIdAndAcademicYearAndSemesterAndActiveTrueAndIdNot(
                subject.getId(), section.getId(), academicYear, request.semester(), Objects.requireNonNullElse(offering.getId(), 0L))) {
            throw new ConflictException("An active offering of this subject already exists for this section, academic year and semester");
        }

        offering.setSubject(subject);
        offering.setBatch(batch);
        offering.setSection(section);
        offering.setStaff(staff);
        offering.setAcademicYear(academicYear);
        offering.setSemester(request.semester());
        offering.setActive(active);
    }

    private static boolean isActiveStaff(User user) {
        boolean staffRole = user.getRoles().stream().anyMatch(r -> "ROLE_STAFF".equals(r.getName()));
        boolean studentRole = user.getRoles().stream().anyMatch(r -> "ROLE_STUDENT".equals(r.getName()));
        return user.isActive() && staffRole && !studentRole;
    }

    /** "2026-2027": second year must directly follow the first. */
    private static void validateAcademicYear(String academicYear) {
        int start = Integer.parseInt(academicYear.substring(0, 4));
        int end = Integer.parseInt(academicYear.substring(5, 9));
        if (end != start + 1 || start < 2000 || start > 2100) {
            throw new BadRequestException("Academic year must be two consecutive years, e.g. 2026-2027");
        }
    }
}

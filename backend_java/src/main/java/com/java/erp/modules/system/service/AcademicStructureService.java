package com.java.erp.modules.system.service;

import com.java.erp.common.response.PagedResponse;
import com.java.erp.common.util.SecurityUtils;
import com.java.erp.exception.ForbiddenException;
import com.java.erp.exception.ResourceNotFoundException;
import com.java.erp.modules.academic.repository.SubjectOfferingRepository;
import com.java.erp.modules.system.dto.response.BatchResponse;
import com.java.erp.modules.system.dto.response.SectionResponse;
import com.java.erp.modules.system.dto.response.SectionStudentResponse;
import com.java.erp.modules.system.entity.Student;
import com.java.erp.modules.system.repository.BatchRepository;
import com.java.erp.modules.system.repository.SectionRepository;
import com.java.erp.modules.system.repository.StudentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * Read-only lookups of the existing batches and sections (used e.g. when creating subject offerings).
 */
@Service
public class AcademicStructureService {

    private final BatchRepository batches;
    private final SectionRepository sections;
    private final StudentRepository students;
    private final SubjectOfferingRepository offerings;

    public AcademicStructureService(BatchRepository batches, SectionRepository sections, StudentRepository students,
                                    SubjectOfferingRepository offerings) {
        this.batches = batches;
        this.sections = sections;
        this.students = students;
        this.offerings = offerings;
    }

    @Transactional(readOnly = true)
    public List<BatchResponse> listActiveBatches() {
        return batches.findByActiveTrueOrderByCurrentYearAscBatchNameAsc().stream().map(BatchResponse::from).toList();
    }

    /** Sections of active batches, optionally limited to one batch. */
    @Transactional(readOnly = true)
    public List<SectionResponse> listActiveSections(Long batchId) {
        return sections.findAllForActiveBatches().stream()
                .filter(s -> batchId == null || batchId.equals(s.getBatch().getId()))
                .map(SectionResponse::from)
                .toList();
    }

    /**
     * One page of a section's students (with or without a login or enrollments), by register number.
     * HOD: any section. STAFF: only sections where they teach an active subject offering.
     */
    @Transactional(readOnly = true)
    public PagedResponse<SectionStudentResponse> sectionStudents(Long sectionId, boolean active, String search, int page, int size) {
        if (!sections.existsById(sectionId)) {
            throw new ResourceNotFoundException("Section", "id", sectionId);
        }
        if (!SecurityUtils.hasAuthority("ROLE_HOD")
                && !offerings.findActiveSectionIdsByStaffId(SecurityUtils.getCurrentUserId()).contains(sectionId)) {
            throw new ForbiddenException("You do not teach this section");
        }
        String pattern = StringUtils.hasText(search)
                ? "%" + search.trim().toLowerCase(Locale.ROOT) + "%"
                : "%";
        Page<Student> result = students.findSectionRoster(sectionId, active, pattern,
                PageRequest.of(page, size, Sort.by("registerNumber", "id")));
        return PagedResponse.of(result.getContent().stream().map(SectionStudentResponse::from).toList(), result);
    }
}

package com.java.erp.modules.academic.service;

import com.java.erp.common.util.SecurityUtils;
import com.java.erp.modules.academic.dto.response.TeachingTermsResponse;
import com.java.erp.modules.academic.dto.response.TeachingTermsResponse.SemesterStatus;
import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.academic.repository.SubjectOfferingRepository;
import com.java.erp.modules.system.entity.Batch;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Batch and semester level of the academic selector: the cohorts and semesters in which the user has subject
 * offerings (STAFF: assigned to them; HOD: all), active or not. The class level is the existing
 * GET /subject-offerings?batchId=&semester= (already limited to the staff member's own offerings).
 */
@Service
public class TeachingTermsService {

    private final SubjectOfferingRepository offerings;

    public TeachingTermsService(SubjectOfferingRepository offerings) {
        this.offerings = offerings;
    }

    @Transactional(readOnly = true)
    public List<TeachingTermsResponse> teachingTerms(LocalDate today) {
        Long staffId = OfferingAccess.isHod() ? null : SecurityUtils.getCurrentUserId();
        Specification<SubjectOffering> spec = (root, query, cb) ->
                staffId == null ? cb.conjunction() : cb.equal(root.get("staff").get("id"), staffId);

        Map<Long, List<SubjectOffering>> byBatch = offerings.findAll(spec).stream()
                .collect(Collectors.groupingBy(o -> o.getBatch().getId()));
        List<TeachingTermsResponse> result = new ArrayList<>();
        for (List<SubjectOffering> list : byBatch.values()) {
            Batch batch = list.get(0).getBatch();
            AcademicCalendar.Position position = AcademicCalendar.position(batch.getAdmissionYear(), today);
            List<TeachingTermsResponse.Semester> semesters = list.stream()
                    .collect(Collectors.groupingBy(SubjectOffering::getSemester, TreeMap::new, Collectors.toList()))
                    .entrySet().stream()
                    .map(e -> semester(batch, e.getKey(), e.getValue(), position))
                    .toList();
            result.add(new TeachingTermsResponse(batch.getId(), batch.getBatchName(), batch.isActive(),
                    batch.getAdmissionYear(), position.yearOfStudy(), position.currentSemester(), position.status(),
                    position.currentTerm() == null ? null : position.currentTerm().startDate(),
                    position.currentTerm() == null ? null : position.currentTerm().endDate(), semesters));
        }
        result.sort(Comparator.comparing(TeachingTermsResponse::batchActive).reversed()
                .thenComparing(TeachingTermsResponse::admissionYear, Comparator.reverseOrder())
                .thenComparing(TeachingTermsResponse::batchName));
        return result;
    }

    private static TeachingTermsResponse.Semester semester(Batch batch, int semester, List<SubjectOffering> list,
                                                           AcademicCalendar.Position position) {
        AcademicCalendar.Term term = semester >= 1 && semester <= AcademicCalendar.MAX_SEMESTER
                ? AcademicCalendar.term(batch.getAdmissionYear(), semester) : null;
        List<String> years = list.stream().map(SubjectOffering::getAcademicYear).distinct().sorted().toList();
        int active = (int) list.stream().filter(SubjectOffering::isActive).count();
        return new TeachingTermsResponse.Semester(semester, status(semester, position),
                term == null ? null : term.startDate(), term == null ? null : term.endDate(), years, list.size(), active);
    }

    static SemesterStatus status(int semester, AcademicCalendar.Position position) {
        return switch (position.status()) {
            case COMPLETED -> SemesterStatus.PREVIOUS;
            case NOT_STARTED -> SemesterStatus.UPCOMING;
            default -> semester == position.currentSemester() ? SemesterStatus.CURRENT
                    : semester < position.currentSemester() ? SemesterStatus.PREVIOUS : SemesterStatus.UPCOMING;
        };
    }
}

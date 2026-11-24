package com.java.erp.modules.gradebook.service;

import com.java.erp.modules.academic.entity.Subject;
import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.academic.service.OfferingScope;
import com.java.erp.modules.gradebook.dto.response.AssessmentOverviewResponse;
import com.java.erp.modules.gradebook.entity.Assessment;
import com.java.erp.modules.gradebook.entity.StudentGrade;
import com.java.erp.modules.gradebook.repository.AssessmentRepository;
import com.java.erp.modules.gradebook.repository.StudentGradeRepository;
import com.java.erp.modules.system.entity.Student;
import com.java.erp.modules.system.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Assessment overview of one cohort + semester, grouped by subject offering (HOD all, STAFF their own; see
 * {@link OfferingScope}). Four queries per request: offerings, assessments, grades, class lists.
 */
@Service
public class AssessmentOverviewService {

    private final OfferingScope scope;
    private final AssessmentRepository assessments;
    private final StudentGradeRepository grades;
    private final StudentRepository students;

    public AssessmentOverviewService(OfferingScope scope, AssessmentRepository assessments,
                                     StudentGradeRepository grades, StudentRepository students) {
        this.scope = scope;
        this.assessments = assessments;
        this.grades = grades;
        this.students = students;
    }

    @Transactional(readOnly = true)
    public List<AssessmentOverviewResponse> overview(Long batchId, int semester, Long sectionId, Long offeringId) {
        List<SubjectOffering> classes = scope.forTerm(batchId, semester, sectionId, offeringId);
        if (classes.isEmpty()) {
            return List.of();
        }
        List<Long> offeringIds = classes.stream().map(SubjectOffering::getId).toList();
        Map<Long, List<Assessment>> byOffering = assessments
                .findBySubjectOfferingIdInOrderByAssessmentDateAscIdAsc(offeringIds).stream()
                .collect(Collectors.groupingBy(a -> a.getSubjectOffering().getId()));
        List<Long> assessmentIds = byOffering.values().stream().flatMap(List::stream).map(Assessment::getId).toList();
        Map<Long, Map<Long, StudentGrade>> gradesByAssessment = new HashMap<>();
        if (!assessmentIds.isEmpty()) {
            grades.findByAssessmentIdIn(assessmentIds).forEach(g -> gradesByAssessment
                    .computeIfAbsent(g.getAssessment().getId(), k -> new HashMap<>()).put(g.getStudent().getId(), g));
        }
        Set<Long> sectionIds = classes.stream().map(o -> o.getSection().getId()).collect(Collectors.toSet());
        Map<Long, List<Long>> classLists = students.findDistinctBySectionIdInAndActiveTrue(sectionIds).stream()
                .collect(Collectors.groupingBy(s -> s.getSection().getId(),
                        Collectors.mapping(Student::getId, Collectors.toList())));

        List<AssessmentOverviewResponse> result = new ArrayList<>();
        for (SubjectOffering o : classes) {
            List<Long> classList = classLists.getOrDefault(o.getSection().getId(), List.of());
            List<AssessmentOverviewResponse.Item> items = byOffering.getOrDefault(o.getId(), List.of()).stream()
                    .map(a -> item(a, AssessmentStatistics.calculate(classList,
                            gradesByAssessment.getOrDefault(a.getId(), Map.of()), a.getPassMarks())))
                    .toList();
            int drafts = (int) items.stream().filter(i -> i.status() == Assessment.Status.DRAFT).count();
            Subject s = o.getSubject();
            result.add(new AssessmentOverviewResponse(o.getId(), s.getId(), s.getCode(), s.getName(), s.getSubjectType(),
                    o.getBatch().getId(), o.getBatch().getBatchName(), o.getSection().getId(),
                    o.getSection().getSectionName(), o.getAcademicYear(), o.getSemester(), o.isActive(),
                    o.getStaff().getId(), o.getStaff().getName(), classList.size(), drafts, items.size() - drafts, items));
        }
        return result;
    }

    private static AssessmentOverviewResponse.Item item(Assessment a, AssessmentStatistics statistics) {
        return new AssessmentOverviewResponse.Item(a.getId(), a.getTitle(), a.getAssessmentType(), a.getMaxMarks(),
                a.getPassMarks(), a.getWeightage(), a.getAssessmentDate(), a.getStatus(), a.getFinalizedAt(),
                a.getMarksEntryDueAt(), a.isDraft() && statistics.complete(), statistics);
    }
}

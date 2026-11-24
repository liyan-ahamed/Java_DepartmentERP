package com.java.erp.modules.results.service;

import com.java.erp.common.response.PagedResponse;
import com.java.erp.exception.BadRequestException;
import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.academic.service.OfferingAccess;
import com.java.erp.modules.academic.service.OfferingScope;
import com.java.erp.modules.exams.entity.ExamPeriod;
import com.java.erp.modules.gradebook.dto.response.GradeStatusLabel;
import com.java.erp.modules.gradebook.entity.Assessment;
import com.java.erp.modules.gradebook.entity.StudentGrade;
import com.java.erp.modules.gradebook.repository.AssessmentRepository;
import com.java.erp.modules.gradebook.repository.StudentGradeRepository;
import com.java.erp.modules.results.dto.response.ClassResultRow;
import com.java.erp.modules.results.entity.ExamResult;
import com.java.erp.modules.results.repository.ExamResultRepository;
import com.java.erp.modules.system.entity.Student;
import com.java.erp.modules.system.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Read-only results of one cohort + semester for one exam (Results page: batch -> semester -> exam), over the
 * offerings the user may see (HOD all, STAFF their own; see {@link OfferingScope}). Nothing here writes.
 *  - END_SEMESTER: exam_results of END_SEMESTER exam periods. STAFF only see published rows.
 *  - CAT_1 / CAT_2: FINALIZED gradebook assessments of that type (drafts are never returned), one row per student of
 *    the gradebook class list (active students of the offering's section), NOT_GRADED when the student has no row.
 * A fixed number of queries per request; rows are sorted by subject code, section, assessment / period, register
 * number and paged in memory (classes are small).
 */
@Service
public class ClassResultViewService {

    static final String SOURCE_EXAM_RESULT = "EXAM_RESULT";
    static final String SOURCE_ASSESSMENT = "ASSESSMENT";

    private final OfferingScope scope;
    private final ExamResultRepository results;
    private final AssessmentRepository assessments;
    private final StudentGradeRepository grades;
    private final StudentRepository students;
    private final boolean gradebookEnabled;

    public ClassResultViewService(OfferingScope scope, ExamResultRepository results, AssessmentRepository assessments,
                                  StudentGradeRepository grades, StudentRepository students,
                                  @Value("${features.gradebook.enabled:false}") boolean gradebookEnabled) {
        this.scope = scope;
        this.results = results;
        this.assessments = assessments;
        this.grades = grades;
        this.students = students;
        this.gradebookEnabled = gradebookEnabled;
    }

    @Transactional(readOnly = true)
    public PagedResponse<ClassResultRow> byExam(Long batchId, int semester, ResultExam exam, Long offeringId,
                                                Long sectionId, Long examPeriodId, int page, int size) {
        if (exam != ResultExam.END_SEMESTER && examPeriodId != null) {
            throw new BadRequestException("examPeriodId applies to END_SEMESTER only");
        }
        if (exam != ResultExam.END_SEMESTER && !gradebookEnabled) {
            throw new BadRequestException("CAT marks are not available: the gradebook is switched off");
        }
        List<SubjectOffering> classes = scope.forTerm(batchId, semester, sectionId, offeringId);
        List<ClassResultRow> rows = classes.isEmpty() ? List.of()
                : exam == ResultExam.END_SEMESTER ? endSemesterRows(classes, examPeriodId) : assessmentRows(classes, exam);

        int totalPages = (int) Math.ceil(rows.size() / (double) size);
        int from = (int) Math.min((long) page * size, rows.size());
        int to = Math.min(from + size, rows.size());
        return new PagedResponse<>(rows.subList(from, to), page, size, rows.size(), totalPages, page >= totalPages - 1);
    }

    private List<ClassResultRow> endSemesterRows(List<SubjectOffering> classes, Long examPeriodId) {
        boolean publishedOnly = !OfferingAccess.isHod();
        Map<Long, Integer> order = positions(classes);
        return results.findBySubjectOfferingIdInAndExamPeriodExamType(order.keySet(), ExamPeriod.ExamType.END_SEMESTER)
                .stream()
                .filter(r -> !publishedOnly || r.isPublished())
                .filter(r -> examPeriodId == null || r.getExamPeriod().getId().equals(examPeriodId))
                .sorted(Comparator.comparing((ExamResult r) -> order.get(r.getSubjectOffering().getId()))
                        .thenComparing(r -> r.getExamPeriod().getId())
                        .thenComparing(r -> r.getStudent().getRegisterNumber()))
                .map(ClassResultViewService::row)
                .toList();
    }

    private List<ClassResultRow> assessmentRows(List<SubjectOffering> classes, ResultExam exam) {
        Map<Long, SubjectOffering> byId = classes.stream()
                .collect(Collectors.toMap(SubjectOffering::getId, o -> o, (a, b) -> a, LinkedHashMap::new));
        List<Assessment> finalized = assessments
                .findBySubjectOfferingIdInAndStatusOrderByAssessmentDateAscIdAsc(byId.keySet(), Assessment.Status.FINALIZED)
                .stream().filter(a -> a.getAssessmentType() == exam.assessmentType()).toList();
        if (finalized.isEmpty()) {
            return List.of();
        }
        Map<Long, Map<Long, StudentGrade>> gradesByAssessment = new HashMap<>();
        grades.findByAssessmentIdIn(finalized.stream().map(Assessment::getId).toList()).forEach(g ->
                gradesByAssessment.computeIfAbsent(g.getAssessment().getId(), k -> new HashMap<>()).put(g.getStudent().getId(), g));
        Map<Long, List<Student>> classLists = classLists(classes);

        Map<Long, List<Assessment>> perOffering = new LinkedHashMap<>();
        byId.keySet().forEach(id -> perOffering.put(id, new ArrayList<>()));
        finalized.forEach(a -> perOffering.get(a.getSubjectOffering().getId()).add(a));

        List<ClassResultRow> rows = new ArrayList<>();
        perOffering.forEach((offeringId, list) -> {
            SubjectOffering o = byId.get(offeringId);
            for (Assessment a : list) {
                Map<Long, StudentGrade> marks = gradesByAssessment.getOrDefault(a.getId(), Map.of());
                for (Student s : classLists.getOrDefault(o.getSection().getId(), List.of())) {
                    rows.add(row(o, a, s, marks.get(s.getId())));
                }
            }
        });
        return rows;
    }

    /** Active students per section, by register number (the gradebook class list). */
    private Map<Long, List<Student>> classLists(List<SubjectOffering> classes) {
        Set<Long> sectionIds = classes.stream().map(o -> o.getSection().getId()).collect(Collectors.toSet());
        return students.findDistinctBySectionIdInAndActiveTrue(sectionIds).stream()
                .sorted(Comparator.comparing(Student::getRegisterNumber).thenComparing(Student::getId))
                .collect(Collectors.groupingBy(s -> s.getSection().getId(), LinkedHashMap::new, Collectors.toList()));
    }

    private static Map<Long, Integer> positions(List<SubjectOffering> classes) {
        Map<Long, Integer> order = new LinkedHashMap<>();
        for (int i = 0; i < classes.size(); i++) order.put(classes.get(i).getId(), i);
        return order;
    }

    private static ClassResultRow row(ExamResult r) {
        SubjectOffering o = r.getSubjectOffering();
        Student s = r.getStudent();
        ExamPeriod p = r.getExamPeriod();
        return new ClassResultRow(ResultExam.END_SEMESTER, SOURCE_EXAM_RESULT, o.getId(), o.getSubject().getId(),
                o.getSubject().getCode(), o.getSubject().getName(), o.getBatch().getId(), o.getBatch().getBatchName(),
                o.getSemester(), o.getAcademicYear(), o.getSection().getId(), o.getSection().getSectionName(),
                s.getId(), s.getRegisterNumber(), s.getName(), null, p.getId(), p.getName(), r.getMarksObtained(),
                r.getMaxMarks(), null, r.getGrade(), r.getResultStatus().name(), r.getRemarks(), r.isPublished(),
                r.getPublishedAt());
    }

    private static ClassResultRow row(SubjectOffering o, Assessment a, Student s, StudentGrade g) {
        ResultExam exam = a.getAssessmentType() == Assessment.AssessmentType.CAT_1 ? ResultExam.CAT_1 : ResultExam.CAT_2;
        return new ClassResultRow(exam, SOURCE_ASSESSMENT, o.getId(), o.getSubject().getId(), o.getSubject().getCode(),
                o.getSubject().getName(), o.getBatch().getId(), o.getBatch().getBatchName(), o.getSemester(),
                o.getAcademicYear(), o.getSection().getId(), o.getSection().getSectionName(), s.getId(),
                s.getRegisterNumber(), s.getName(), a.getId(), null, a.getTitle(), g == null ? null : g.getMarks(),
                a.getMaxMarks(), a.getPassMarks(), null, GradeStatusLabel.of(g), g == null ? null : g.getRemarks(),
                true, a.getFinalizedAt());
    }
}

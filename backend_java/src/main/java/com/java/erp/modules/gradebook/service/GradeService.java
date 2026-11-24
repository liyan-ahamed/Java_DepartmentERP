package com.java.erp.modules.gradebook.service;

import com.java.erp.common.audit.Auditable;
import com.java.erp.exception.BadRequestException;
import com.java.erp.modules.academic.entity.Subject;
import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.academic.service.OfferingAccess;
import com.java.erp.modules.gradebook.dto.request.BulkGradeRequest;
import com.java.erp.modules.gradebook.dto.response.*;
import com.java.erp.modules.gradebook.entity.Assessment;
import com.java.erp.modules.gradebook.entity.StudentGrade;
import com.java.erp.modules.gradebook.repository.AssessmentRepository;
import com.java.erp.modules.gradebook.repository.StudentGradeRepository;
import com.java.erp.modules.system.entity.Student;
import com.java.erp.modules.system.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Grade entry, class gradebook, assessment analytics and the student's own grades.
 * The expected class list of an assessment is the active students of its offering's section.
 */
@Service
public class GradeService {

    private final AssessmentService assessmentService;
    private final AssessmentRepository assessments;
    private final StudentGradeRepository grades;
    private final StudentRepository students;
    private final CurrentStudentResolver currentStudent;

    public GradeService(AssessmentService assessmentService, AssessmentRepository assessments,
                        StudentGradeRepository grades, StudentRepository students, CurrentStudentResolver currentStudent) {
        this.assessmentService = assessmentService;
        this.assessments = assessments;
        this.grades = grades;
        this.students = students;
        this.currentStudent = currentStudent;
    }

    /**
     * Creates, updates or clears grades for many students of a DRAFT assessment. Every row is validated
     * first; if any row is invalid nothing is saved and all problems are returned together.
     * Returns (and audits) counts only, never the marks themselves.
     */
    @Transactional
    @Auditable(action = "GRADE_BULK_UPDATE", module = "GRADEBOOK", entityType = "Assessment")
    public GradeBulkUpdateResponse saveGrades(Long assessmentId, BulkGradeRequest request) {
        Assessment assessment = assessmentService.requireAssessment(assessmentId);
        SubjectOffering offering = assessment.getSubjectOffering();
        OfferingAccess.requireManage(offering);
        AssessmentService.requireDraft(assessment);

        Map<Long, Student> classList = classList(offering);
        List<String> errors = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < request.grades().size(); i++) {
            BulkGradeRequest.Entry entry = request.grades().get(i);
            String row = "grades[" + i + "] (studentId " + entry.studentId() + "): ";
            if (!seen.add(entry.studentId())) {
                errors.add(row + "student appears more than once");
            } else if (!classList.containsKey(entry.studentId())) {
                errors.add(row + "student is not in this subject offering's section");
            }
            validateMarks(entry, assessment.getMaxMarks()).ifPresent(problem -> errors.add(row + problem));
        }
        if (!errors.isEmpty()) {
            throw new BadRequestException("Grade validation failed", errors);
        }
        return applyGrades(assessment, classList, request.grades());
    }

    /**
     * Writes already validated entries (same transaction as the caller): new rows are created, changed rows
     * updated, identical rows skipped, and entries with no status and no marks delete the stored row.
     */
    GradeBulkUpdateResponse applyGrades(Assessment assessment, Map<Long, Student> classList,
                                        List<BulkGradeRequest.Entry> entries) {
        Map<Long, StudentGrade> existing = gradesByStudent(assessment.getId());
        int created = 0;
        int updated = 0;
        int cleared = 0;
        List<StudentGrade> toSave = new ArrayList<>();
        List<StudentGrade> toDelete = new ArrayList<>();
        for (BulkGradeRequest.Entry entry : entries) {
            String remarks = entry.remarks() == null || entry.remarks().isBlank() ? null : entry.remarks().trim();
            StudentGrade grade = existing.get(entry.studentId());
            if (entry.status() == null) {
                if (grade != null) {
                    toDelete.add(grade);
                    cleared++;
                }
                continue;
            }
            if (grade == null) {
                grade = new StudentGrade(assessment, classList.get(entry.studentId()));
                created++;
            } else if (sameGrade(grade, entry, remarks)) {
                continue;
            } else {
                updated++;
            }
            grade.setGradeStatus(entry.status());
            grade.setMarks(entry.marks());
            grade.setRemarks(remarks);
            toSave.add(grade);
        }
        grades.deleteAll(toDelete);
        grades.saveAll(toSave);
        grades.flush();
        return new GradeBulkUpdateResponse(assessment.getId(), assessment.getSubjectOffering().getId(), entries.size(),
                created, updated, created + updated + cleared, cleared);
    }

    /** Every expected student of the class with their grade, if any (NOT_GRADED otherwise). */
    @Transactional(readOnly = true)
    public GradebookResponse gradebook(Long assessmentId) {
        Assessment assessment = assessmentService.requireAssessment(assessmentId);
        OfferingAccess.requireView(assessment.getSubjectOffering());
        Map<Long, StudentGrade> byStudent = gradesByStudent(assessmentId);
        List<GradebookResponse.Row> rows = classList(assessment.getSubjectOffering()).values().stream().map(s -> {
            StudentGrade g = byStudent.get(s.getId());
            return new GradebookResponse.Row(s.getId(), s.getRegisterNumber(), s.getName(),
                    g == null ? null : g.getId(), g == null ? null : g.getMarks(), GradeStatusLabel.of(g),
                    g == null ? null : g.getRemarks(), g == null ? null : g.getUpdatedAt());
        }).toList();
        return new GradebookResponse(AssessmentResponse.from(assessment), rows.size(), rows);
    }

    /** Basic statistics over the expected class list; numeric statistics use GRADED marks only. */
    @Transactional(readOnly = true)
    public AssessmentSummaryResponse summary(Long assessmentId) {
        Assessment assessment = assessmentService.requireAssessment(assessmentId);
        OfferingAccess.requireView(assessment.getSubjectOffering());
        AssessmentStatistics s = AssessmentStatistics.calculate(classList(assessment.getSubjectOffering()).keySet(),
                gradesByStudent(assessmentId), assessment.getPassMarks());
        return new AssessmentSummaryResponse(assessment.getId(), assessment.getTitle(), assessment.getStatus(),
                assessment.getMaxMarks(), assessment.getPassMarks(), s.expectedStudentCount(), s.gradedCount(),
                s.absentCount(), s.exemptedCount(), s.notGradedCount(), s.average(), s.highest(), s.lowest(),
                s.passCount(), s.failCount(), s.passPercentage());
    }

    /**
     * The logged-in student's results for FINALIZED assessments only (DRAFT grades are never
     * returned). Assessments of their class without a grade row appear as NOT_GRADED.
     */
    @Transactional(readOnly = true)
    public List<StudentGradeResponse> myGrades(Long subjectId, String academicYear, Integer semester) {
        Student student = currentStudent.require();
        List<Assessment> visible = assessments.findVisibleToStudent(Assessment.Status.FINALIZED,
                        student.getSection().getId(), student.getId()).stream()
                .filter(a -> subjectId == null || a.getSubjectOffering().getSubject().getId().equals(subjectId))
                .filter(a -> academicYear == null || a.getSubjectOffering().getAcademicYear().equals(academicYear.trim()))
                .filter(a -> semester == null || a.getSubjectOffering().getSemester().equals(semester))
                .toList();
        if (visible.isEmpty()) {
            return List.of();
        }
        Map<Long, StudentGrade> byAssessment = grades.findByStudentIdAndAssessmentIdIn(student.getId(),
                        visible.stream().map(Assessment::getId).toList()).stream()
                .collect(Collectors.toMap(g -> g.getAssessment().getId(), Function.identity()));
        return visible.stream().map(a -> {
            SubjectOffering o = a.getSubjectOffering();
            Subject s = o.getSubject();
            StudentGrade g = byAssessment.get(a.getId());
            return new StudentGradeResponse(a.getId(), a.getTitle(), a.getAssessmentType(), a.getAssessmentDate(),
                    a.getMaxMarks(), a.getPassMarks(), a.getFinalizedAt(), o.getId(), s.getId(), s.getCode(), s.getName(),
                    s.getSubjectType(), o.getAcademicYear(), o.getSemester(),
                    g == null ? null : g.getMarks(), GradeStatusLabel.of(g), g == null ? null : g.getRemarks());
        }).toList();
    }

    /** Active students of the offering's section by register number (the gradebook class list). */
    Map<Long, Student> classList(SubjectOffering offering) {
        Map<Long, Student> result = new LinkedHashMap<>();
        students.findBySectionIdAndActiveTrueOrderByRegisterNumberAsc(offering.getSection().getId())
                .forEach(s -> result.put(s.getId(), s));
        return result;
    }

    Map<Long, StudentGrade> gradesByStudent(Long assessmentId) {
        return grades.findByAssessmentId(assessmentId).stream()
                .collect(Collectors.toMap(g -> g.getStudent().getId(), Function.identity()));
    }

    private static Optional<String> validateMarks(BulkGradeRequest.Entry entry, BigDecimal maxMarks) {
        if (entry.status() == null) {
            return entry.marks() == null ? Optional.empty()
                    : Optional.of("status is required when marks are given (send status and marks as null to clear)");
        }
        return GradeRules.problem(entry.status(), entry.marks(), maxMarks);
    }

    private static boolean sameGrade(StudentGrade grade, BulkGradeRequest.Entry entry, String remarks) {
        boolean sameMarks = grade.getMarks() == null ? entry.marks() == null
                : entry.marks() != null && grade.getMarks().compareTo(entry.marks()) == 0;
        return grade.getGradeStatus() == entry.status() && sameMarks && Objects.equals(grade.getRemarks(), remarks);
    }
}

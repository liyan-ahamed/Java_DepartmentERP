package com.java.erp.modules.gradebook.service;

import com.java.erp.common.audit.Auditable;
import com.java.erp.exception.BadRequestException;
import com.java.erp.exception.ConflictException;
import com.java.erp.modules.academic.service.OfferingAccess;
import com.java.erp.modules.gradebook.dto.request.BulkGradeRequest;
import com.java.erp.modules.gradebook.dto.request.GradeImportRequest;
import com.java.erp.modules.gradebook.dto.response.GradeBulkUpdateResponse;
import com.java.erp.modules.gradebook.dto.response.GradeImportPreviewResponse;
import com.java.erp.modules.gradebook.dto.response.GradeImportPreviewResponse.Outcome;
import com.java.erp.modules.gradebook.dto.response.GradeStatusLabel;
import com.java.erp.modules.gradebook.entity.Assessment;
import com.java.erp.modules.gradebook.entity.StudentGrade;
import com.java.erp.modules.gradebook.entity.StudentGrade.GradeStatus;
import com.java.erp.modules.system.entity.Student;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Spreadsheet import of one DRAFT assessment's marks, matched by register number (case-insensitive) against the
 * gradebook class list. Preview first (read-only), then apply: all or nothing, refused while any row is unmatched,
 * duplicated or invalid, and refused (409) unless confirmOverwrite=true when existing grades would change.
 * Same owner / draft rules and the same storage (student_grades) as the bulk save. Blank rows never clear a grade.
 */
@Service
public class GradeImportService {

    private final AssessmentService assessmentService;
    private final GradeService gradeService;

    public GradeImportService(AssessmentService assessmentService, GradeService gradeService) {
        this.assessmentService = assessmentService;
        this.gradeService = gradeService;
    }

    @Transactional(readOnly = true)
    public GradeImportPreviewResponse preview(Long assessmentId, GradeImportRequest request) {
        return analyze(requireEditable(assessmentId), request).response();
    }

    @Transactional
    @Auditable(action = "GRADE_IMPORT", module = "GRADEBOOK", entityType = "Assessment")
    public GradeBulkUpdateResponse apply(Long assessmentId, GradeImportRequest request) {
        Assessment assessment = requireEditable(assessmentId);
        Analysis analysis = analyze(assessment, request);
        GradeImportPreviewResponse preview = analysis.response();
        if (preview.errorCount() > 0) {
            List<String> errors = preview.rows().stream().filter(r -> r.error() != null)
                    .map(r -> "row " + r.rowNumber() + " (" + r.registerNumber() + "): " + r.error()).toList();
            throw new BadRequestException("Import has " + preview.errorCount() + " problem row(s); nothing was saved", errors);
        }
        if (preview.overwriteCount() > 0 && !Boolean.TRUE.equals(request.confirmOverwrite())) {
            throw new ConflictException(preview.overwriteCount() + " existing grade(s) would be overwritten; "
                    + "review the preview and send confirmOverwrite=true to apply");
        }
        List<BulkGradeRequest.Entry> entries = preview.rows().stream()
                .filter(r -> r.outcome() == Outcome.NEW || r.outcome() == Outcome.OVERWRITE)
                .map(r -> {
                    StudentGrade current = analysis.existing().get(r.studentId());
                    // Remarks are not part of the file: keep what was stored.
                    return new BulkGradeRequest.Entry(r.studentId(), r.marks(), r.status(),
                            current == null ? null : current.getRemarks());
                })
                .toList();
        return gradeService.applyGrades(assessment, analysis.classList(), entries);
    }

    private Assessment requireEditable(Long assessmentId) {
        Assessment assessment = assessmentService.requireAssessment(assessmentId);
        OfferingAccess.requireManage(assessment.getSubjectOffering());
        AssessmentService.requireDraft(assessment);
        return assessment;
    }

    private record Analysis(Map<Long, Student> classList, Map<Long, StudentGrade> existing,
                            GradeImportPreviewResponse response) {
    }

    private Analysis analyze(Assessment assessment, GradeImportRequest request) {
        Map<Long, Student> classList = gradeService.classList(assessment.getSubjectOffering());
        Map<Long, StudentGrade> existing = gradeService.gradesByStudent(assessment.getId());
        Map<String, Student> byRegister = new HashMap<>();
        classList.values().forEach(s -> byRegister.put(normalize(s.getRegisterNumber()), s));

        Map<String, List<Integer>> rowsByRegister = new HashMap<>();
        List<GradeImportRequest.Row> input = request.rows();
        for (int i = 0; i < input.size(); i++) {
            rowsByRegister.computeIfAbsent(normalize(input.get(i).registerNumber()), k -> new ArrayList<>())
                    .add(rowNumber(input.get(i), i));
        }

        List<GradeImportPreviewResponse.Row> rows = new ArrayList<>();
        Set<Long> matched = new HashSet<>();
        EnumMap<Outcome, Integer> counts = new EnumMap<>(Outcome.class);
        for (int i = 0; i < input.size(); i++) {
            GradeImportRequest.Row in = input.get(i);
            String key = normalize(in.registerNumber());
            Student student = byRegister.get(key);
            if (student != null) matched.add(student.getId());
            StudentGrade current = student == null ? null : existing.get(student.getId());
            GradeStatus status = in.status() != null ? in.status() : in.marks() != null ? GradeStatus.GRADED : null;

            Outcome outcome;
            String error = null;
            List<Integer> sameRegister = rowsByRegister.get(key);
            if (sameRegister.size() > 1) {
                outcome = Outcome.DUPLICATE;
                error = "register number appears in rows " + sameRegister.stream().map(String::valueOf)
                        .collect(Collectors.joining(", "));
            } else if (student == null) {
                outcome = Outcome.UNMATCHED;
                error = "register number is not a student of this class";
            } else if (status == null) {
                outcome = Outcome.SKIPPED;
            } else {
                Optional<String> problem = GradeRules.problem(status, in.marks(), assessment.getMaxMarks());
                if (problem.isPresent()) {
                    outcome = Outcome.INVALID;
                    error = problem.get();
                } else if (current == null) {
                    outcome = Outcome.NEW;
                } else if (current.getGradeStatus() == status && sameMarks(current, in)) {
                    outcome = Outcome.UNCHANGED;
                } else {
                    outcome = Outcome.OVERWRITE;
                }
            }
            counts.merge(outcome, 1, Integer::sum);
            rows.add(new GradeImportPreviewResponse.Row(rowNumber(in, i), in.registerNumber().trim(),
                    student == null ? null : student.getId(), student == null ? null : student.getName(),
                    in.marks(), status, current == null ? null : current.getMarks(),
                    student == null ? null : GradeStatusLabel.of(current), outcome, error));
        }

        List<GradeImportPreviewResponse.MissingStudent> notInFile = classList.values().stream()
                .filter(s -> !matched.contains(s.getId()))
                .map(s -> new GradeImportPreviewResponse.MissingStudent(s.getId(), s.getRegisterNumber(), s.getName(),
                        GradeStatusLabel.of(existing.get(s.getId()))))
                .toList();
        int errorCount = counts.getOrDefault(Outcome.UNMATCHED, 0) + counts.getOrDefault(Outcome.DUPLICATE, 0)
                + counts.getOrDefault(Outcome.INVALID, 0);
        GradeImportPreviewResponse response = new GradeImportPreviewResponse(assessment.getId(), assessment.getMaxMarks(),
                input.size(), counts.getOrDefault(Outcome.NEW, 0), counts.getOrDefault(Outcome.OVERWRITE, 0),
                counts.getOrDefault(Outcome.UNCHANGED, 0), counts.getOrDefault(Outcome.SKIPPED, 0), errorCount,
                errorCount == 0, rows, notInFile);
        return new Analysis(classList, existing, response);
    }

    private static int rowNumber(GradeImportRequest.Row row, int index) {
        return row.rowNumber() != null ? row.rowNumber() : index + 1;
    }

    private static String normalize(String registerNumber) {
        return registerNumber == null ? "" : registerNumber.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean sameMarks(StudentGrade current, GradeImportRequest.Row in) {
        return current.getMarks() == null ? in.marks() == null
                : in.marks() != null && current.getMarks().compareTo(in.marks()) == 0;
    }
}

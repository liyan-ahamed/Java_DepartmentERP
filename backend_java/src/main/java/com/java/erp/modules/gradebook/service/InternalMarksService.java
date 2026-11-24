package com.java.erp.modules.gradebook.service;

import com.java.erp.exception.ResourceNotFoundException;
import com.java.erp.modules.academic.dto.response.SubjectOfferingResponse;
import com.java.erp.modules.academic.entity.Subject;
import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.academic.repository.SubjectOfferingRepository;
import com.java.erp.modules.academic.service.OfferingAccess;
import com.java.erp.modules.gradebook.dto.response.GradeStatusLabel;
import com.java.erp.modules.gradebook.dto.response.InternalMarksResponse;
import com.java.erp.modules.gradebook.entity.Assessment;
import com.java.erp.modules.gradebook.entity.StudentGrade;
import com.java.erp.modules.gradebook.entity.StudentInternalMark;
import com.java.erp.modules.gradebook.repository.AssessmentRepository;
import com.java.erp.modules.gradebook.repository.StudentGradeRepository;
import com.java.erp.modules.gradebook.repository.StudentInternalMarkRepository;
import com.java.erp.modules.system.entity.Student;
import com.java.erp.modules.system.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Storage and read access for internal-assessment summaries.
 *
 * Intentionally contains NO calculation: the formulas and weightages are not defined yet.
 * Records are created empty (PENDING_CALCULATION_CONFIGURATION, all totals null). A future
 * InternalMarksCalculator can load an offering, check its subject type, take the FINALIZED
 * assessments grouped by type (as returned here), apply the agreed formula and store the totals
 * in student_internal_marks with status CALCULATED.
 */
@Service
public class InternalMarksService {

    private final StudentInternalMarkRepository internalMarks;
    private final SubjectOfferingRepository offerings;
    private final AssessmentRepository assessments;
    private final StudentGradeRepository grades;
    private final StudentRepository students;
    private final CurrentStudentResolver currentStudent;

    public InternalMarksService(StudentInternalMarkRepository internalMarks, SubjectOfferingRepository offerings,
                                AssessmentRepository assessments, StudentGradeRepository grades,
                                StudentRepository students, CurrentStudentResolver currentStudent) {
        this.internalMarks = internalMarks;
        this.offerings = offerings;
        this.assessments = assessments;
        this.grades = grades;
        this.students = students;
        this.currentStudent = currentStudent;
    }

    /** Creates an empty pending record for every active student of the offering's section that has none yet. */
    @Transactional
    public void ensureRecords(SubjectOffering offering) {
        Set<Long> existing = internalMarks.findBySubjectOfferingId(offering.getId()).stream()
                .map(m -> m.getStudent().getId()).collect(Collectors.toSet());
        List<StudentInternalMark> missing = students.findBySectionIdAndActiveTrueOrderByRegisterNumberAsc(offering.getSection().getId())
                .stream()
                .filter(s -> !existing.contains(s.getId()))
                .map(s -> new StudentInternalMark(offering, s))
                .toList();
        internalMarks.saveAll(missing);
    }

    @Transactional
    public void deleteRecords(Long offeringId) {
        internalMarks.deleteBySubjectOfferingId(offeringId);
    }

    /** Faculty/HOD view of one offering: every student of the class plus anyone who already has a record. */
    @Transactional(readOnly = true)
    public InternalMarksResponse forOffering(Long offeringId) {
        SubjectOffering offering = offerings.findWithDetailsById(offeringId)
                .orElseThrow(() -> new ResourceNotFoundException("SubjectOffering", "id", offeringId));
        OfferingAccess.requireView(offering);

        Map<Long, StudentInternalMark> records = internalMarks.findBySubjectOfferingId(offeringId).stream()
                .collect(Collectors.toMap(m -> m.getStudent().getId(), Function.identity()));
        Map<Long, Student> classList = new LinkedHashMap<>();
        students.findBySectionIdAndActiveTrueOrderByRegisterNumberAsc(offering.getSection().getId())
                .forEach(s -> classList.put(s.getId(), s));
        records.values().forEach(m -> classList.putIfAbsent(m.getStudent().getId(), m.getStudent()));

        List<Student> roster = classList.values().stream()
                .sorted(Comparator.comparing(Student::getRegisterNumber)).toList();
        return build(offering, roster, records);
    }

    /** The logged-in student's internal-mark view for each offering of their section (optionally filtered). */
    @Transactional(readOnly = true)
    public List<InternalMarksResponse> mine(Long subjectId, String academicYear, Integer semester) {
        Student student = currentStudent.require();
        Map<Long, StudentInternalMark> recordsByOffering = internalMarks.findByStudentId(student.getId()).stream()
                .collect(Collectors.toMap(m -> m.getSubjectOffering().getId(), Function.identity()));

        Map<Long, SubjectOffering> visible = new LinkedHashMap<>();
        offerings.findBySectionIdOrderByAcademicYearDescSemesterDesc(student.getSection().getId())
                .forEach(o -> visible.put(o.getId(), o));
        offerings.findByIdIn(recordsByOffering.keySet()).forEach(o -> visible.putIfAbsent(o.getId(), o));

        return visible.values().stream()
                .filter(o -> subjectId == null || o.getSubject().getId().equals(subjectId))
                .filter(o -> academicYear == null || o.getAcademicYear().equals(academicYear.trim()))
                .filter(o -> semester == null || o.getSemester().equals(semester))
                .sorted(Comparator.comparing(SubjectOffering::getAcademicYear).reversed()
                        .thenComparing(SubjectOffering::getSemester, Comparator.reverseOrder())
                        .thenComparing(o -> o.getSubject().getCode()))
                .map(o -> {
                    StudentInternalMark record = recordsByOffering.get(o.getId());
                    return build(o, List.of(student), record == null ? Map.of() : Map.of(student.getId(), record));
                })
                .toList();
    }

    private InternalMarksResponse build(SubjectOffering offering, List<Student> roster, Map<Long, StudentInternalMark> records) {
        List<Assessment> finalized = assessments.findBySubjectOfferingIdInAndStatusOrderByAssessmentDateAscIdAsc(
                List.of(offering.getId()), Assessment.Status.FINALIZED);
        Map<Long, Map<Long, StudentGrade>> gradesByAssessment = new HashMap<>();
        if (!finalized.isEmpty()) {
            List<Long> ids = finalized.stream().map(Assessment::getId).toList();
            List<StudentGrade> rows = roster.size() == 1
                    ? grades.findByStudentIdAndAssessmentIdIn(roster.get(0).getId(), ids)
                    : grades.findByAssessmentIdIn(ids);
            rows.forEach(g -> gradesByAssessment.computeIfAbsent(g.getAssessment().getId(), k -> new HashMap<>())
                    .put(g.getStudent().getId(), g));
        }

        List<InternalMarksResponse.StudentEntry> entries = roster.stream().map(student -> {
            Map<Assessment.AssessmentType, List<InternalMarksResponse.AssessmentResult>> byType =
                    new EnumMap<>(Assessment.AssessmentType.class);
            for (Assessment a : finalized) {
                StudentGrade g = gradesByAssessment.getOrDefault(a.getId(), Map.of()).get(student.getId());
                byType.computeIfAbsent(a.getAssessmentType(), k -> new ArrayList<>())
                        .add(new InternalMarksResponse.AssessmentResult(a.getId(), a.getTitle(), a.getAssessmentDate(),
                                a.getMaxMarks(), g == null ? null : g.getMarks(), GradeStatusLabel.of(g)));
            }
            StudentInternalMark m = records.get(student.getId());
            return new InternalMarksResponse.StudentEntry(student.getId(), student.getRegisterNumber(), student.getName(),
                    m == null ? null : m.getTheoryComponentTotal(),
                    m == null ? null : m.getPracticalComponentTotal(),
                    m == null ? null : m.getTotalInternalMarks(),
                    m == null ? StudentInternalMark.CalculationStatus.PENDING_CALCULATION_CONFIGURATION : m.getCalculationStatus(),
                    m == null ? null : m.getCalculationVersion(),
                    m == null ? null : m.getCalculatedAt(),
                    byType);
        }).toList();

        return new InternalMarksResponse(SubjectOfferingResponse.from(offering),
                offering.getSubject().getSubjectType() == Subject.SubjectType.THEORY_CUM_PRACTICAL, entries);
    }
}

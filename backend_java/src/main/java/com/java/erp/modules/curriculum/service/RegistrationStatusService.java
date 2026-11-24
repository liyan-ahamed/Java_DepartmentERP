package com.java.erp.modules.curriculum.service;

import com.java.erp.common.response.PagedResponse;
import com.java.erp.exception.BadRequestException;
import com.java.erp.exception.ResourceNotFoundException;
import com.java.erp.modules.academic.entity.Subject;
import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.academic.repository.SubjectOfferingRepository;
import com.java.erp.modules.curriculum.dto.response.RegistrationStatusResponses.*;
import com.java.erp.modules.curriculum.entity.CurriculumElectiveGroup;
import com.java.erp.modules.curriculum.entity.CurriculumSubject;
import com.java.erp.modules.curriculum.service.RegistrationEvaluator.Evaluation;
import com.java.erp.modules.curriculum.service.RegistrationEvaluator.Status;
import com.java.erp.modules.enrollment.entity.CourseEnrollment;
import com.java.erp.modules.enrollment.repository.CourseEnrollmentRepository;
import com.java.erp.modules.system.entity.Batch;
import com.java.erp.modules.system.entity.Section;
import com.java.erp.modules.system.entity.Student;
import com.java.erp.modules.system.repository.BatchRepository;
import com.java.erp.modules.system.repository.SectionRepository;
import com.java.erp.modules.system.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Authoritative registration status per student for a cohort + semester ({@link RegistrationEvaluator} is the rule).
 *
 * The list works on the whole class at once with a fixed number of queries (students, counted registrations,
 * curriculum), so students without enrollments are included, each student appears once, and the status / search
 * filters run before paging. A class is a few hundred students at most, so paging in memory is cheap.
 */
@Service
public class RegistrationStatusService {

    private final StudentRepository students;
    private final BatchRepository batches;
    private final SectionRepository sections;
    private final CourseEnrollmentRepository enrollments;
    private final SubjectOfferingRepository offerings;
    private final CurriculumService curricula;

    public RegistrationStatusService(StudentRepository students, BatchRepository batches, SectionRepository sections,
                                     CourseEnrollmentRepository enrollments, SubjectOfferingRepository offerings,
                                     CurriculumService curricula) {
        this.students = students;
        this.batches = batches;
        this.sections = sections;
        this.enrollments = enrollments;
        this.offerings = offerings;
        this.curricula = curricula;
    }

    @Transactional(readOnly = true)
    public PagedResponse<StudentRow> list(Long batchId, Integer semester, Long sectionId, Status status, String search,
                                          int page, int size) {
        List<StudentRow> rows = evaluateClass(batchId, semester, sectionId);
        String needle = StringUtils.hasText(search) ? search.trim().toLowerCase(Locale.ROOT) : null;
        List<StudentRow> filtered = rows.stream()
                .filter(r -> status == null || r.status() == status)
                .filter(r -> needle == null || r.studentName().toLowerCase(Locale.ROOT).contains(needle)
                        || r.registerNumber().toLowerCase(Locale.ROOT).contains(needle))
                .toList();
        int totalPages = (int) Math.ceil(filtered.size() / (double) size);
        int from = (int) Math.min((long) page * size, filtered.size());
        int to = Math.min(from + size, filtered.size());
        return new PagedResponse<>(filtered.subList(from, to), page, size, filtered.size(), totalPages,
                page >= totalPages - 1);
    }

    @Transactional(readOnly = true)
    public Summary summary(Long batchId, Integer semester, Long sectionId) {
        List<StudentRow> rows = evaluateClass(batchId, semester, sectionId);
        Map<Status, Long> counts = new EnumMap<>(Status.class);
        for (Status s : Status.values()) counts.put(s, 0L);
        rows.forEach(r -> counts.merge(r.status(), 1L, Long::sum));
        long offered = sectionId == null ? offerings.countOfferedSubjects(batchId, semester)
                : offerings.countOfferedSubjectsForSection(sectionId, semester);
        return new Summary(rows.size(), curricula.load(batchId, semester).isPresent(), offered, counts);
    }

    @Transactional(readOnly = true)
    public Detail detail(Long studentId, Integer semester, Long batchId) {
        CurriculumService.requireSemester(semester);
        Student student = students.findWithClassById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student", "id", studentId));
        Long cohort = student.getBatch().getId();
        if (batchId != null && !batchId.equals(cohort)) {
            throw new BadRequestException("Student " + student.getRegisterNumber() + " belongs to cohort "
                    + student.getBatch().getBatchName() + ", not cohort " + batchId);
        }
        Optional<CurriculumService.Loaded> curriculum = curricula.load(cohort, semester);

        // Enrollment rows of the term, per subject: a counted one first, else the latest other row.
        Map<Long, CourseEnrollment> bySubject = new HashMap<>();
        for (CourseEnrollment e : enrollments.findByStudentAndTerm(studentId, cohort, semester)) {
            Long subjectId = e.getSubjectOffering().getSubject().getId();
            CourseEnrollment current = bySubject.get(subjectId);
            if (current == null || counts(e) || !counts(current)) bySubject.put(subjectId, e);
        }
        Set<Long> registered = bySubject.values().stream().filter(RegistrationStatusService::counts)
                .map(e -> e.getSubjectOffering().getSubject().getId()).collect(Collectors.toSet());
        Map<Long, SubjectOffering> sectionOfferings = offerings.findBySectionIdAndSemesterAndActiveTrue(
                        student.getSection().getId(), semester).stream()
                .collect(Collectors.toMap(o -> o.getSubject().getId(), Function.identity(), (a, b) -> a));
        Evaluation evaluation = RegistrationEvaluator.evaluate(registered,
                curriculum.map(CurriculumService.Loaded::requirements).orElse(null));

        List<SubjectItem> compulsory = new ArrayList<>();
        List<ElectiveGroupItem> groups = new ArrayList<>();
        Set<Long> inCurriculum = new HashSet<>();
        if (curriculum.isPresent()) {
            List<CurriculumSubject> rows = curriculum.get().subjects();
            rows.stream().filter(r -> r.getElectiveGroup() == null).forEach(r -> {
                inCurriculum.add(r.getSubject().getId());
                compulsory.add(item(r.getSubject(), bySubject, sectionOfferings));
            });
            for (CurriculumElectiveGroup g : curriculum.get().groups()) {
                List<SubjectItem> options = rows.stream()
                        .filter(r -> r.getElectiveGroup() != null && r.getElectiveGroup().getId().equals(g.getId()))
                        .peek(r -> inCurriculum.add(r.getSubject().getId()))
                        .map(r -> item(r.getSubject(), bySubject, sectionOfferings)).toList();
                int selected = (int) options.stream().filter(SubjectItem::registered).count();
                groups.add(new ElectiveGroupItem(g.getId(), g.getName(), g.getRequiredSelections(), selected,
                        selected >= g.getRequiredSelections(), options));
            }
        }
        List<SubjectItem> others = bySubject.values().stream()
                .filter(RegistrationStatusService::counts)
                .filter(e -> !inCurriculum.contains(e.getSubjectOffering().getSubject().getId()))
                .sorted(Comparator.comparing(e -> e.getSubjectOffering().getSubject().getCode()))
                .map(e -> item(e.getSubjectOffering().getSubject(), bySubject, sectionOfferings))
                .toList();
        StudentInfo info = new StudentInfo(student.getId(), student.getRegisterNumber(), student.getName(), cohort,
                student.getBatch().getBatchName(), semester, student.getSection().getId(), student.getSection().getSectionName());
        return new Detail(info, evaluation.status(), curriculum.isPresent(), evaluation.registeredCount(), compulsory, groups,
                others);
    }

    /** Every active student of the class, evaluated, in register-number order. */
    private List<StudentRow> evaluateClass(Long batchId, Integer semester, Long sectionId) {
        CurriculumService.requireSemester(semester);
        Batch batch = batches.findById(batchId).orElseThrow(() -> new ResourceNotFoundException("Batch", "id", batchId));
        List<Student> classList;
        if (sectionId == null) {
            classList = students.findByBatchIdAndActiveTrueOrderByRegisterNumberAscIdAsc(batchId);
        } else {
            Section section = sections.findById(sectionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Section", "id", sectionId));
            if (!section.getBatch().getId().equals(batchId)) {
                throw new BadRequestException("Section " + sectionId + " does not belong to cohort " + batch.getBatchName());
            }
            classList = students.findByBatchIdAndSectionIdAndActiveTrueOrderByRegisterNumberAscIdAsc(batchId, sectionId);
        }
        RegistrationEvaluator.Requirements requirements = curricula.load(batchId, semester)
                .map(CurriculumService.Loaded::requirements).orElse(null);
        Map<Long, Set<Long>> registeredByStudent = new HashMap<>();
        enrollments.findRegistrations(batchId, semester, RegistrationEvaluator.COUNTED)
                .forEach(r -> registeredByStudent.computeIfAbsent(r.getStudentId(), k -> new HashSet<>()).add(r.getSubjectId()));

        List<StudentRow> rows = new ArrayList<>(classList.size());
        for (Student s : classList) {
            Evaluation e = RegistrationEvaluator.evaluate(registeredByStudent.getOrDefault(s.getId(), Set.of()), requirements);
            rows.add(new StudentRow(s.getId(), s.getRegisterNumber(), s.getName(), batch.getId(), batch.getBatchName(), semester,
                    s.getSection().getId(), s.getSection().getSectionName(), e.registeredCount(), e.compulsoryRegistered(),
                    e.compulsoryRequired(), e.electiveGroupsSatisfied(), e.electiveGroupsRequired(), e.status()));
        }
        return rows;
    }

    private static boolean counts(CourseEnrollment e) {
        return RegistrationEvaluator.COUNTED.contains(e.getStatus()) && e.getSubjectOffering().isActive();
    }

    private static SubjectItem item(Subject subject, Map<Long, CourseEnrollment> bySubject,
                                    Map<Long, SubjectOffering> sectionOfferings) {
        CourseEnrollment e = bySubject.get(subject.getId());
        if (e != null) {
            SubjectOffering o = e.getSubjectOffering();
            return new SubjectItem(subject.getId(), subject.getCode(), subject.getName(), o.getId(), o.getStaff().getName(),
                    counts(e), e.getId(), e.getStatus());
        }
        SubjectOffering o = sectionOfferings.get(subject.getId());
        return new SubjectItem(subject.getId(), subject.getCode(), subject.getName(), o == null ? null : o.getId(),
                o == null ? null : o.getStaff().getName(), false, null, null);
    }
}

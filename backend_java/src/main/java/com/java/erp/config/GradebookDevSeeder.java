package com.java.erp.config;

import com.java.erp.modules.academic.entity.Subject;
import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.academic.repository.SubjectOfferingRepository;
import com.java.erp.modules.academic.repository.SubjectRepository;
import com.java.erp.modules.auth.entity.User;
import com.java.erp.modules.auth.repository.UserRepository;
import com.java.erp.modules.gradebook.config.ConditionalOnGradebookEnabled;
import com.java.erp.modules.gradebook.entity.Assessment;
import com.java.erp.modules.gradebook.entity.StudentGrade;
import com.java.erp.modules.gradebook.repository.AssessmentRepository;
import com.java.erp.modules.gradebook.repository.StudentGradeRepository;
import com.java.erp.modules.gradebook.service.InternalMarksService;
import com.java.erp.modules.system.entity.Section;
import com.java.erp.modules.system.entity.Student;
import com.java.erp.modules.system.repository.SectionRepository;
import com.java.erp.modules.system.repository.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Small, predictable gradebook demo data for local development: dev profile AND
 * features.gradebook.enabled=true only (never in the default/production profile, not in tests).
 * Runs after {@link DataSeeder} so the demo logins exist, and only once (skipped when subject
 * CS301 already exists).
 *
 * Creates, for the demo student's section and the demo staff login (staff@department.com):
 *  - CS301 Computer Networks (THEORY): CAT 1 + Assignment 1 FINALIZED, CAT 2 DRAFT (partly graded)
 *  - CS305 Java Programming (THEORY_CUM_PRACTICAL): CAT 1 FINALIZED, Lab Exercise 1 DRAFT (graded), Record DRAFT (empty)
 *  - CS301 for a neighbouring section assigned to another staff member (for access checks)
 *  - empty, pending internal-mark records (no totals are calculated)
 */
@Component
@Profile("dev")
@ConditionalOnGradebookEnabled
@Order(2)
public class GradebookDevSeeder implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(GradebookDevSeeder.class);

    private final SubjectRepository subjects;
    private final SubjectOfferingRepository offerings;
    private final AssessmentRepository assessments;
    private final StudentGradeRepository grades;
    private final StudentRepository students;
    private final SectionRepository sections;
    private final UserRepository users;
    private final InternalMarksService internalMarks;

    public GradebookDevSeeder(SubjectRepository subjects, SubjectOfferingRepository offerings,
                              AssessmentRepository assessments, StudentGradeRepository grades,
                              StudentRepository students, SectionRepository sections, UserRepository users,
                              InternalMarksService internalMarks) {
        this.subjects = subjects;
        this.offerings = offerings;
        this.assessments = assessments;
        this.grades = grades;
        this.students = students;
        this.sections = sections;
        this.users = users;
        this.internalMarks = internalMarks;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (subjects.existsByCodeIgnoreCase("CS301")) {
            logger.info("Gradebook demo data already present, skipping");
            return;
        }
        Optional<User> staff = users.findByEmail("staff@department.com");
        Optional<Student> demoStudent = users.findByEmail("aarav.sharma1@student.erp.com")
                .flatMap(u -> students.findLinkedStudent(u.getId(), u.getEmail()));
        if (staff.isEmpty() || demoStudent.isEmpty()) {
            logger.warn("Demo staff or demo student not found; gradebook demo data not seeded");
            return;
        }

        Section section = demoStudent.get().getSection();
        int semester = section.getBatch().getCurrentYear() * 2 - 1;
        String academicYear = currentAcademicYear();
        LocalDate today = LocalDate.now();

        Subject networks = subjects.save(new Subject("CS301", "Computer Networks", Subject.SubjectType.THEORY));
        Subject java = subjects.save(new Subject("CS305", "Java Programming", Subject.SubjectType.THEORY_CUM_PRACTICAL));

        SubjectOffering networksOffering = offering(networks, section, staff.get(), academicYear, semester);
        SubjectOffering javaOffering = offering(java, section, staff.get(), academicYear, semester);
        List<Student> classList = students.findBySectionIdAndActiveTrueOrderByRegisterNumberAsc(section.getId());

        grade(assessment(networksOffering, staff.get(), "CAT 1", Assessment.AssessmentType.CAT_1, 50, 25, today.minusDays(30), true), classList, classList.size(), 3);
        grade(assessment(networksOffering, staff.get(), "Assignment 1", Assessment.AssessmentType.ASSIGNMENT, 10, null, today.minusDays(20), true), classList, classList.size(), 11);
        grade(assessment(networksOffering, staff.get(), "CAT 2", Assessment.AssessmentType.CAT_2, 50, 25, today.minusDays(2), false), classList, 10, 17);
        grade(assessment(javaOffering, staff.get(), "CAT 1", Assessment.AssessmentType.CAT_1, 50, 25, today.minusDays(28), true), classList, classList.size(), 5);
        grade(assessment(javaOffering, staff.get(), "Lab Exercise 1", Assessment.AssessmentType.PRACTICAL, 25, null, today.minusDays(7), false), classList, classList.size(), 23);
        assessment(javaOffering, staff.get(), "Record", Assessment.AssessmentType.RECORD, 15, null, today.minusDays(1), false);

        users.findByEmail("staff_ananya@department.com").ifPresent(other ->
                sections.findByBatchIdOrderBySectionNameAsc(section.getBatch().getId()).stream()
                        .filter(s -> !s.getId().equals(section.getId()))
                        .findFirst()
                        .ifPresent(otherSection -> offering(networks, otherSection, other, academicYear, semester)));

        logger.info("Seeded gradebook demo data for section {} ({} students)", section.getId(), classList.size());
    }

    private SubjectOffering offering(Subject subject, Section section, User staff, String academicYear, int semester) {
        SubjectOffering offering = new SubjectOffering();
        offering.setSubject(subject);
        offering.setBatch(section.getBatch());
        offering.setSection(section);
        offering.setStaff(staff);
        offering.setAcademicYear(academicYear);
        offering.setSemester(semester);
        offering = offerings.save(offering);
        internalMarks.ensureRecords(offering);
        return offering;
    }

    private Assessment assessment(SubjectOffering offering, User staff, String title, Assessment.AssessmentType type,
                                  int maxMarks, Integer passMarks, LocalDate date, boolean finalized) {
        Assessment a = new Assessment();
        a.setSubjectOffering(offering);
        a.setCreatedBy(staff);
        a.setTitle(title);
        a.setAssessmentType(type);
        a.setMaxMarks(BigDecimal.valueOf(maxMarks));
        a.setPassMarks(passMarks == null ? null : BigDecimal.valueOf(passMarks));
        a.setAssessmentDate(date);
        if (finalized) {
            a.setStatus(Assessment.Status.FINALIZED);
            a.setFinalizedAt(date.plusDays(3).atTime(10, 0));
        }
        return assessments.save(a);
    }

    /** Deterministic marks between 45% and 94% of max; a few ABSENT and EXEMPTED students. */
    private void grade(Assessment assessment, List<Student> classList, int count, int seed) {
        List<StudentGrade> rows = new ArrayList<>();
        for (int i = 0; i < Math.min(count, classList.size()); i++) {
            StudentGrade g = new StudentGrade(assessment, classList.get(i));
            if (i % 13 == 7) {
                g.setGradeStatus(StudentGrade.GradeStatus.ABSENT);
            } else if (i % 29 == 11) {
                g.setGradeStatus(StudentGrade.GradeStatus.EXEMPTED);
                g.setRemarks("Medical exemption");
            } else {
                double fraction = 0.45 + ((i * 37 + seed) % 50) / 100.0;
                g.setGradeStatus(StudentGrade.GradeStatus.GRADED);
                g.setMarks(assessment.getMaxMarks().multiply(BigDecimal.valueOf(fraction)).setScale(0, RoundingMode.HALF_UP));
            }
            rows.add(g);
        }
        grades.saveAll(rows);
    }

    /** June starts a new academic year: October 2026 -> "2026-2027", March 2027 -> "2026-2027". */
    private static String currentAcademicYear() {
        LocalDate today = LocalDate.now();
        int start = today.getMonthValue() >= 6 ? today.getYear() : today.getYear() - 1;
        return start + "-" + (start + 1);
    }
}

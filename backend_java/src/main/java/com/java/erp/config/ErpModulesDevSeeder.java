package com.java.erp.config;

import com.java.erp.modules.academic.entity.Subject;
import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.academic.repository.SubjectOfferingRepository;
import com.java.erp.modules.academic.repository.SubjectRepository;
import com.java.erp.modules.attendance.entity.AttendanceSession;
import com.java.erp.modules.attendance.entity.StudentAttendance;
import com.java.erp.modules.attendance.repository.AttendanceSessionRepository;
import com.java.erp.modules.attendance.repository.StudentAttendanceRepository;
import com.java.erp.modules.auth.entity.Role;
import com.java.erp.modules.auth.entity.User;
import com.java.erp.modules.auth.repository.RoleRepository;
import com.java.erp.modules.auth.repository.UserRepository;
import com.java.erp.modules.enrollment.entity.CourseEnrollment;
import com.java.erp.modules.enrollment.repository.CourseEnrollmentRepository;
import com.java.erp.modules.exams.entity.ExamPeriod;
import com.java.erp.modules.exams.entity.ExamRegistration;
import com.java.erp.modules.exams.entity.ExamRegistrationSubject;
import com.java.erp.modules.exams.repository.ExamPeriodRepository;
import com.java.erp.modules.exams.repository.ExamRegistrationRepository;
import com.java.erp.modules.feedback.entity.*;
import com.java.erp.modules.feedback.repository.FeedbackFormRepository;
import com.java.erp.modules.feedback.repository.FeedbackResponseRepository;
import com.java.erp.modules.feedback.repository.FeedbackSubmissionRepository;
import com.java.erp.modules.fees.entity.FeePayment;
import com.java.erp.modules.fees.entity.FeeStructure;
import com.java.erp.modules.fees.entity.StudentFeeAccount;
import com.java.erp.modules.fees.repository.FeePaymentRepository;
import com.java.erp.modules.fees.repository.FeeStructureRepository;
import com.java.erp.modules.fees.repository.StudentFeeAccountRepository;
import com.java.erp.modules.fees.service.ReceiptNumberGenerator;
import com.java.erp.modules.gradebook.service.InternalMarksService;
import com.java.erp.modules.lessonplan.config.LessonPlansFeature;
import com.java.erp.modules.lessonplan.entity.LessonPlan;
import com.java.erp.modules.lessonplan.repository.LessonPlanRepository;
import com.java.erp.modules.leetcode.service.LeetCodeRosterService;
import com.java.erp.modules.results.entity.ExamResult;
import com.java.erp.modules.results.repository.ExamResultRepository;
import com.java.erp.modules.system.entity.Batch;
import com.java.erp.modules.system.entity.Section;
import com.java.erp.modules.system.entity.Student;
import com.java.erp.modules.system.repository.BatchRepository;
import com.java.erp.modules.system.repository.SectionRepository;
import com.java.erp.modules.system.repository.StudentRepository;
import com.java.erp.modules.timetable.entity.TimetableSlot;
import com.java.erp.modules.timetable.period.InstitutionPeriodSchedule;
import com.java.erp.modules.timetable.repository.TimetableSlotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

/**
 * Demo data for the student-facing ERP modules: dev profile only (never production, not in tests).
 *
 * Uses ONLY the existing Student records of the LeetCode-linked Second-Year roster (active students of
 * the Second-Year batch, section A). It reads those records and references them by id; it never
 * changes LeetCode fields. The only change to a roster student is linking one demo STUDENT login to
 * the first student of section A (students.user_id), so the student APIs can be tried out.
 *
 * Idempotent: the demo login is created once; the module data is skipped when subject CS203 exists.
 * Skipped entirely (with a warning) until the roster has been imported.
 *
 * Its CS203/CS205 offerings are LEGACY demo data: {@link CurriculumDevSeeder} deactivates them
 * (data kept) because the roster cohort now follows the official Semester 3 curriculum.
 * (CS201 Data Structures was removed from the demo data on 2026-10-12.)
 */
@Component
@Profile("dev")
@Order(5)
public class ErpModulesDevSeeder implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(ErpModulesDevSeeder.class);
    static final String MARKER_SUBJECT = "CS203";
    static final String DEMO_PASSWORD = "password123";

    private final BatchRepository batches;
    private final SectionRepository sections;
    private final StudentRepository students;
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final SubjectRepository subjects;
    private final SubjectOfferingRepository offerings;
    private final InternalMarksService internalMarks;
    private final CourseEnrollmentRepository enrollments;
    private final TimetableSlotRepository slots;
    private final AttendanceSessionRepository sessions;
    private final StudentAttendanceRepository attendance;
    private final LessonPlanRepository lessonPlans;
    private final FeeStructureRepository feeStructures;
    private final StudentFeeAccountRepository feeAccounts;
    private final FeePaymentRepository feePayments;
    private final ReceiptNumberGenerator receiptNumbers;
    private final ExamPeriodRepository examPeriods;
    private final ExamRegistrationRepository examRegistrations;
    private final ExamResultRepository examResults;
    private final FeedbackFormRepository feedbackForms;
    private final FeedbackSubmissionRepository feedbackSubmissions;
    private final FeedbackResponseRepository feedbackResponses;
    /** Lesson-plan demo rows are seeded only while the Lesson Plans feature is switched on. */
    private final boolean lessonPlansEnabled;

    public ErpModulesDevSeeder(BatchRepository batches, SectionRepository sections, StudentRepository students,
                               UserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder,
                               SubjectRepository subjects, SubjectOfferingRepository offerings,
                               InternalMarksService internalMarks, CourseEnrollmentRepository enrollments,
                               TimetableSlotRepository slots, AttendanceSessionRepository sessions,
                               StudentAttendanceRepository attendance, LessonPlanRepository lessonPlans,
                               FeeStructureRepository feeStructures, StudentFeeAccountRepository feeAccounts,
                               FeePaymentRepository feePayments, ReceiptNumberGenerator receiptNumbers,
                               ExamPeriodRepository examPeriods, ExamRegistrationRepository examRegistrations,
                               ExamResultRepository examResults, FeedbackFormRepository feedbackForms,
                               FeedbackSubmissionRepository feedbackSubmissions,
                               FeedbackResponseRepository feedbackResponses,
                               @Value("${" + LessonPlansFeature.PROPERTY + ":false}") boolean lessonPlansEnabled) {
        this.lessonPlansEnabled = lessonPlansEnabled;
        this.batches = batches;
        this.sections = sections;
        this.students = students;
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.subjects = subjects;
        this.offerings = offerings;
        this.internalMarks = internalMarks;
        this.enrollments = enrollments;
        this.slots = slots;
        this.sessions = sessions;
        this.attendance = attendance;
        this.lessonPlans = lessonPlans;
        this.feeStructures = feeStructures;
        this.feeAccounts = feeAccounts;
        this.feePayments = feePayments;
        this.receiptNumbers = receiptNumbers;
        this.examPeriods = examPeriods;
        this.examRegistrations = examRegistrations;
        this.examResults = examResults;
        this.feedbackForms = feedbackForms;
        this.feedbackSubmissions = feedbackSubmissions;
        this.feedbackResponses = feedbackResponses;
    }

    @Override
    @Transactional
    public void run(String... args) {
        List<Batch> secondYear = batches.findByActiveTrueAndCurrentYear((short) 2);
        if (secondYear.size() != 1) {
            logger.warn("ERP module demo data not seeded: expected one active Second-Year batch, found {}", secondYear.size());
            return;
        }
        Batch batch = secondYear.get(0);
        long rosterSize = students.findByBatchId(batch.getId()).stream().filter(Student::isActive).count();
        if (rosterSize != LeetCodeRosterService.SECOND_YEAR_STUDENTS) {
            logger.warn("ERP module demo data not seeded: the Second-Year batch has {} active students, the LeetCode roster has {}. "
                    + "Import the roster first (LEETCODE_SECOND_YEAR_ROSTER).", rosterSize, LeetCodeRosterService.SECOND_YEAR_STUDENTS);
            return;
        }
        Map<String, Section> sectionByName = new HashMap<>();
        sections.findByBatchIdOrderBySectionNameAsc(batch.getId()).forEach(s -> sectionByName.put(s.getSectionName().toUpperCase(Locale.ROOT), s));
        Section a = sectionByName.get("A");
        List<Student> classA = students.findBySectionIdAndActiveTrueOrderByRegisterNumberAsc(a.getId());

        ensureDemoStudentLogin(classA.get(0));

        if (subjects.existsByCodeIgnoreCase(MARKER_SUBJECT)) {
            logger.info("ERP module demo data already present, skipping");
            return;
        }
        Optional<User> staff = users.findByEmail("staff@department.com");
        Optional<User> otherStaff = users.findByEmail("staff_ananya@department.com");
        Optional<User> hod = users.findByEmail("hod@department.com");
        if (staff.isEmpty() || otherStaff.isEmpty() || hod.isEmpty()) {
            logger.warn("ERP module demo data not seeded: demo HOD/staff logins missing");
            return;
        }
        seed(batch, a, classA, staff.get(), otherStaff.get(), hod.get());
    }

    /** One STUDENT login for the first roster student of section A (only user_id is set on the student). */
    private void ensureDemoStudentLogin(Student student) {
        if (student.getUser() != null) {
            return;
        }
        String username = student.getRegisterNumber().toLowerCase(Locale.ROOT);
        String email = username + "@student.erp.com";
        if (users.findByEmail(email).isPresent() || users.existsByUsername(username)) {
            return;
        }
        Role role = roles.findByName("ROLE_STUDENT").orElse(null);
        if (role == null) return;
        User user = new User(username, student.getName(), email, passwordEncoder.encode(DEMO_PASSWORD));
        user.setDesignation("STUDENT");
        user.setActive(true);
        user.addRole(role);
        user = users.save(user);
        student.setUser(user);
        students.save(student);
        logger.info("Linked demo student login '{}' to roster student {} ({})", email, student.getRegisterNumber(), student.getName());
    }

    private void seed(Batch batch, Section a, List<Student> classA, User staff, User otherStaff, User hod) {
        LocalDate today = LocalDate.now();
        String academicYear = currentAcademicYear(today);
        int semester = batch.getCurrentYear() * 2 - 1;

        // ---- Subjects + offerings ----
        Subject dbms = subjects.save(new Subject(MARKER_SUBJECT, "Database Management Systems", Subject.SubjectType.THEORY_CUM_PRACTICAL));
        Subject maths = subjects.save(new Subject("CS205", "Discrete Mathematics", Subject.SubjectType.THEORY));
        SubjectOffering dbmsA = offering(dbms, a, staff, academicYear, semester);
        SubjectOffering mathsA = offering(maths, a, otherStaff, academicYear, semester);

        // ---- Enrollment ----
        enroll(dbmsA, classA, hod);
        List<CourseEnrollment> mathsEnrollments = enroll(mathsA, classA, hod);
        mathsEnrollments.get(mathsEnrollments.size() - 1).setStatus(CourseEnrollment.Status.DROPPED);
        enrollments.flush();

        // ---- Timetable ----
        List<TimetableSlot> dbmsSlots = List.of(
                slot(dbmsA, DayOfWeek.TUESDAY, 1, "CR-201", TimetableSlot.SlotType.LECTURE),
                slot(dbmsA, DayOfWeek.THURSDAY, 4, "LAB-2", TimetableSlot.SlotType.LAB));
        slot(mathsA, DayOfWeek.MONDAY, 2, "CR-201", TimetableSlot.SlotType.LECTURE);
        slot(mathsA, DayOfWeek.THURSDAY, 1, "CR-201", TimetableSlot.SlotType.TUTORIAL);

        // ---- Attendance: 4 finalized + 1 draft (CS203-A) ----
        List<LocalDate> dbmsDates = pastSlotDates(dbmsSlots, today, 5);
        for (int j = 0; j < 4; j++) {
            attendanceSession(dbmsA, dbmsSlots, dbmsDates.get(4 - j), classA, j + 10, true, staff, "Session " + (j + 1));
        }
        attendanceSession(dbmsA, dbmsSlots, dbmsDates.get(0), classA.subList(0, classA.size() / 2), 14, false, staff, "Session 5");

        // ---- Lesson plans (only while the feature is switched on) ----
        if (lessonPlansEnabled) {
            String[] dbmsTopics = {"ER modelling", "Relational model and keys", "SQL basics", "Joins and subqueries",
                    "Normalization", "Transactions"};
            for (int i = 0; i < dbmsTopics.length; i++) {
                lessonPlan(dbmsA, staff, i / 2 + 1, dbmsTopics[i], today.minusWeeks(4).plusWeeks(i), i < 3);
            }
        }

        // ---- Fees: no longer seeded here. The authoritative Year 1 / Year 2 templates come from FeeTemplateDevSeeder
        // (the old 45,000 tuition + 1,500 exam fee demo charges were removed on 2026-10-12). ----

        // ---- Exam periods, registrations, results ----
        ExamPeriod internal = examPeriod("Internal Assessment Test 1", ExamPeriod.ExamType.INTERNAL, academicYear, semester,
                today.minusDays(45), today.minusDays(35), ExamPeriod.Status.CLOSED, hod);
        ExamPeriod endSem = examPeriod("End Semester Examinations " + today.getYear(), ExamPeriod.ExamType.END_SEMESTER,
                academicYear, semester, today.minusDays(3), today.plusDays(14), ExamPeriod.Status.OPEN, hod);
        for (int i = 1; i <= 10; i++) {
            Student s = classA.get(i);
            ExamRegistration r = new ExamRegistration(endSem, s);
            r.getSubjects().add(new ExamRegistrationSubject(r, dbmsA));
            r.getSubjects().add(new ExamRegistrationSubject(r, mathsA));
            r.setStatus(ExamRegistration.Status.SUBMITTED);
            r.setSubmittedAt(today.minusDays(2).atTime(10, i));
            if (i >= 9) {
                r.setStatus(ExamRegistration.Status.APPROVED);
                r.setReviewedBy(hod);
                r.setReviewedAt(today.minusDays(1).atTime(15, 0));
            }
            examRegistrations.save(r);
        }
        results(dbmsA, internal, classA, staff, hod, true, 3);
        // The last student dropped CS205, so only the others get (unpublished) results.
        results(mathsA, internal, classA.subList(0, classA.size() - 1), otherStaff, hod, false, 7);

        // ---- Feedback ----
        FeedbackForm courseFeedback = feedbackForm("Mid-semester feedback: CS203 Database Management Systems",
                FeedbackForm.TargetType.SUBJECT_OFFERING, dbmsA, true, FeedbackForm.Status.OPEN, hod,
                List.of("The pace of the course is appropriate", "Concepts are explained clearly",
                        "Lab and tutorial support is useful"), "What should be improved?");
        for (int i = 1; i <= 10; i++) {
            Student s = classA.get(i);
            feedbackSubmissions.save(new FeedbackSubmission(courseFeedback, s));
            FeedbackResponse response = new FeedbackResponse(courseFeedback, null);
            for (FeedbackQuestion q : courseFeedback.getQuestions()) {
                response.getAnswers().add(q.getQuestionType() == FeedbackQuestion.QuestionType.RATING
                        ? new FeedbackAnswer(response, q, (short) (2 + (i * 3 + q.getPosition()) % 4), null)
                        : new FeedbackAnswer(response, q, null, TEXT_ANSWERS[i % TEXT_ANSWERS.length]));
            }
            feedbackResponses.save(response);
        }
        feedbackForm("Department facilities survey", FeedbackForm.TargetType.DEPARTMENT, null, false,
                FeedbackForm.Status.DRAFT, hod, List.of("Classrooms are well equipped", "Library resources are sufficient"),
                "Any other suggestions?");

        logger.info("Seeded ERP module demo data for roster section {} ({} students)", a.getSectionName(), classA.size());
    }

    private static final String[] TEXT_ANSWERS = {
            "More practice problems before tests would help.", "Please share slides before class.",
            "Lab sessions are very helpful.", "Pace is a bit fast for normalization.",
            "More real-world examples please."};

    // ---- helpers ----

    private SubjectOffering offering(Subject subject, Section section, User staff, String academicYear, int semester) {
        SubjectOffering o = new SubjectOffering();
        o.setSubject(subject);
        o.setBatch(section.getBatch());
        o.setSection(section);
        o.setStaff(staff);
        o.setAcademicYear(academicYear);
        o.setSemester(semester);
        o = offerings.save(o);
        internalMarks.ensureRecords(o);
        return o;
    }

    private List<CourseEnrollment> enroll(SubjectOffering offering, List<Student> classList, User hod) {
        List<CourseEnrollment> list = classList.stream().map(s -> new CourseEnrollment(s, offering, hod)).toList();
        return enrollments.saveAll(list);
    }

    /** One official teaching period (times come from InstitutionPeriodSchedule). */
    private TimetableSlot slot(SubjectOffering offering, DayOfWeek day, int period, String room, TimetableSlot.SlotType type) {
        InstitutionPeriodSchedule.Period p = InstitutionPeriodSchedule.teachingPeriod(period).orElseThrow();
        TimetableSlot t = new TimetableSlot();
        t.setSubjectOffering(offering);
        t.setDayOfWeek(day);
        t.setPeriodNumber(period);
        t.setStartTime(p.startTime());
        t.setEndTime(p.endTime());
        t.setRoom(room);
        t.setSlotType(type);
        return slots.save(t);
    }

    /** The most recent `count` dates (newest first, up to today) that fall on one of the slots' days. */
    private static List<LocalDate> pastSlotDates(List<TimetableSlot> slotList, LocalDate today, int count) {
        Set<DayOfWeek> days = new HashSet<>();
        slotList.forEach(s -> days.add(s.getDayOfWeek()));
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate d = today; dates.size() < count; d = d.minusDays(1)) {
            if (days.contains(d.getDayOfWeek())) dates.add(d);
        }
        return dates;
    }

    /**
     * Deterministic mixed marks: most students PRESENT; every 9th student (from index 4) is often absent
     * so some fall below the 75% minimum; a few ON_DUTY and EXCUSED marks.
     */
    private void attendanceSession(SubjectOffering offering, List<TimetableSlot> slotList, LocalDate date, List<Student> marked,
                                   int seed, boolean finalized, User staff, String topic) {
        TimetableSlot slot = slotList.stream().filter(s -> s.getDayOfWeek() == date.getDayOfWeek()).findFirst().orElseThrow();
        AttendanceSession session = new AttendanceSession();
        session.setSubjectOffering(offering);
        session.setAttendanceDate(date);
        session.setTimetableSlot(slot);
        session.setPeriodNumber(slot.getPeriodNumber());
        session.setTopic(topic);
        session.setCreatedBy(staff);
        if (finalized) {
            session.setStatus(AttendanceSession.Status.FINALIZED);
            session.setFinalizedAt(date.atTime(slot.getEndTime()));
        }
        session = sessions.save(session);
        List<StudentAttendance> rows = new ArrayList<>();
        for (int i = 0; i < marked.size(); i++) {
            StudentAttendance r = new StudentAttendance(session, marked.get(i));
            StudentAttendance.AttendanceStatus status;
            if (i % 9 == 4 && seed % 3 != 0) status = StudentAttendance.AttendanceStatus.ABSENT;
            else if ((i + seed) % 17 == 0) status = StudentAttendance.AttendanceStatus.ABSENT;
            else if ((i * 7 + seed) % 23 == 5) status = StudentAttendance.AttendanceStatus.ON_DUTY;
            else if ((i * 5 + seed) % 29 == 8) status = StudentAttendance.AttendanceStatus.EXCUSED;
            else status = StudentAttendance.AttendanceStatus.PRESENT;
            r.setStatus(status);
            if (status == StudentAttendance.AttendanceStatus.ON_DUTY) r.setRemarks("Inter-college event");
            if (status == StudentAttendance.AttendanceStatus.EXCUSED) r.setRemarks("Medical leave");
            rows.add(r);
        }
        attendance.saveAll(rows);
    }

    private void lessonPlan(SubjectOffering offering, User staff, int unit, String topic, LocalDate planned, boolean completed) {
        LessonPlan p = new LessonPlan();
        p.setSubjectOffering(offering);
        p.setCreatedBy(staff);
        p.setUnitNumber(unit);
        p.setTopic(topic);
        p.setPlannedDate(planned);
        if (completed) {
            p.setStatus(LessonPlan.Status.COMPLETED);
            p.setCompletedDate(planned.isAfter(LocalDate.now()) ? LocalDate.now() : planned);
        }
        lessonPlans.save(p);
    }

    private ExamPeriod examPeriod(String name, ExamPeriod.ExamType type, String academicYear, int semester, LocalDate start,
                                  LocalDate end, ExamPeriod.Status status, User hod) {
        ExamPeriod p = new ExamPeriod();
        p.setName(name);
        p.setExamType(type);
        p.setAcademicYear(academicYear);
        p.setSemester(semester);
        p.setRegistrationStart(start);
        p.setRegistrationEnd(end);
        p.setStatus(status);
        p.setCreatedBy(hod);
        return examPeriods.save(p);
    }

    /** Marks out of 50 between 14 and 49; every 15th student ABSENT; PASS from 20 marks. */
    private void results(SubjectOffering offering, ExamPeriod period, List<Student> classList, User staff, User hod,
                         boolean published, int seed) {
        List<ExamResult> rows = new ArrayList<>();
        for (int i = 0; i < classList.size(); i++) {
            ExamResult r = new ExamResult(classList.get(i), offering, period);
            r.setMaxMarks(BigDecimal.valueOf(50));
            r.setEnteredBy(staff);
            if (i % 15 == 14) {
                r.setResultStatus(ExamResult.ResultStatus.ABSENT);
            } else {
                int marks = 14 + (i * 37 + seed) % 36;
                r.setMarksObtained(BigDecimal.valueOf(marks));
                r.setResultStatus(marks >= 20 ? ExamResult.ResultStatus.PASS : ExamResult.ResultStatus.FAIL);
                r.setGrade(marks >= 45 ? "O" : marks >= 40 ? "A+" : marks >= 35 ? "A" : marks >= 30 ? "B+" : marks >= 25 ? "B" : marks >= 20 ? "C" : "RA");
            }
            if (published) {
                r.setPublished(true);
                r.setPublishedAt(period.getRegistrationEnd().plusDays(10).atTime(16, 0));
                r.setPublishedBy(hod);
            }
            rows.add(r);
        }
        examResults.saveAll(rows);
    }

    private FeedbackForm feedbackForm(String title, FeedbackForm.TargetType target, SubjectOffering offering, boolean anonymous,
                                      FeedbackForm.Status status, User hod, List<String> ratingQuestions, String textQuestion) {
        FeedbackForm f = new FeedbackForm();
        f.setTitle(title);
        f.setTargetType(target);
        f.setTargetSubjectOffering(offering);
        f.setAnonymous(anonymous);
        f.setStatus(status);
        f.setCreatedBy(hod);
        if (status != FeedbackForm.Status.DRAFT) f.setOpenedAt(LocalDate.now().minusDays(4).atTime(9, 0));
        int position = 1;
        for (String q : ratingQuestions) {
            f.getQuestions().add(new FeedbackQuestion(f, position++, q, FeedbackQuestion.QuestionType.RATING, true));
        }
        f.getQuestions().add(new FeedbackQuestion(f, position, textQuestion, FeedbackQuestion.QuestionType.TEXT, false));
        return feedbackForms.save(f);
    }

    /** June starts a new academic year: October 2026 -> "2026-2027" (same rule as GradebookDevSeeder). */
    private static String currentAcademicYear(LocalDate today) {
        int start = today.getMonthValue() >= 6 ? today.getYear() : today.getYear() - 1;
        return start + "-" + (start + 1);
    }
}

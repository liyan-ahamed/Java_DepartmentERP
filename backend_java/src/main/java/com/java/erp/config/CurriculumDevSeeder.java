package com.java.erp.config;

import com.java.erp.modules.academic.entity.Subject;
import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.academic.repository.SubjectOfferingRepository;
import com.java.erp.modules.academic.repository.SubjectRepository;
import com.java.erp.modules.auth.entity.Role;
import com.java.erp.modules.auth.entity.User;
import com.java.erp.modules.auth.repository.RoleRepository;
import com.java.erp.modules.auth.repository.UserRepository;
import com.java.erp.modules.enrollment.entity.CourseEnrollment;
import com.java.erp.modules.enrollment.repository.CourseEnrollmentRepository;
import com.java.erp.modules.gradebook.service.InternalMarksService;
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
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

/**
 * Dev profile only: brings the academic demo data in line with the department's structure.
 * Every step is idempotent (re-running changes nothing) and nothing is deleted.
 *
 * 1. Cohorts: the active B.Tech cohorts are the four admitted in the last four years
 *    (2026-27: 2026-2030 Y1, 2025-2029 Y2, 2024-2028 Y3, 2023-2027 Y4); missing ones are created with
 *    sections A-D, all others are deactivated (not deleted). One-time rename: the V10 batch "2023-2027"
 *    labelled Year 2 holds the LeetCode Second-Year roster, so it becomes "2025-2029" (students untouched).
 * 2. Curriculum: Semester 1-3 subjects from {@link DevCurriculum} (credits; type only where the code
 *    rule gives one and the subject has none yet).
 * 3. Demo students: 10 clearly labelled "Demo Student" rows per section of the new first-year cohort
 *    (which has no real students), plus one STUDENT login. Existing Student rows are never changed.
 * 4. Offerings for every section: Semester 1 + 2 for Year 1, Semester 3 for Year 2, with existing
 *    department STAFF accounts. Legacy demo offerings CS203/CS205 of the roster cohort are
 *    deactivated (their attendance/results/exam/feedback data is kept).
 * 5. Enrollments: the active students of each section in the current term's offerings (odd term
 *    June-November: Semesters 1 and 3; the Semester 2 offerings wait for the even term).
 * 6. Timetable: DEMO weekly allocation on the official periods (Mon-Fri, periods 1-8) for current-term
 *    offerings without slots, with no section or staff double-booking. Credits = periods per week
 *    (zero-credit subjects get 1). This is sample data, not an official timetable.
 */
@Component
@Profile("dev")
@Order(6)
public class CurriculumDevSeeder implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(CurriculumDevSeeder.class);

    static final List<String> SECTION_NAMES = List.of("A", "B", "C", "D");
    static final int DEMO_STUDENTS_PER_SECTION = 10;
    static final String DEMO_PASSWORD = "password123";
    /** Subjects of the earlier demo seeder that the official Semester 3 curriculum replaces for the roster cohort. */
    static final List<String> LEGACY_ROSTER_SUBJECTS = List.of("CS203", "CS205");
    static final String ROSTER_OLD_NAME = "2023-2027";
    private static final List<DayOfWeek> TEACHING_DAYS =
            List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

    /** What one run changed (all zero on a re-run). */
    public record Report(int batchesRenamed, int batchesCreated, int batchesDeactivated, int batchYearsUpdated,
                         int sectionsCreated, int studentsCreated, int subjectsCreated, int subjectsUpdated,
                         int offeringsCreated, int legacyOfferingsDeactivated, int slotsDeactivated,
                         int enrollmentsCreated, int slotsCreated, int periodsNotPlaced) {
    }

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

    public CurriculumDevSeeder(BatchRepository batches, SectionRepository sections, StudentRepository students,
                               UserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder,
                               SubjectRepository subjects, SubjectOfferingRepository offerings,
                               InternalMarksService internalMarks, CourseEnrollmentRepository enrollments,
                               TimetableSlotRepository slots) {
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
    }

    @Override
    @Transactional
    public void run(String... args) {
        Report report = seed(LocalDate.now());
        logger.info("Curriculum dev data: {}", report);
    }

    /** Mutable counters behind {@link Report}. */
    private static final class Counts {
        int renamed, batchesCreated, deactivated, yearsUpdated, sectionsCreated, studentsCreated, subjectsCreated,
                subjectsUpdated, offeringsCreated, legacyDeactivated, slotsDeactivated, enrollmentsCreated, slotsCreated,
                notPlaced;

        Report report() {
            return new Report(renamed, batchesCreated, deactivated, yearsUpdated, sectionsCreated, studentsCreated,
                    subjectsCreated, subjectsUpdated, offeringsCreated, legacyDeactivated, slotsDeactivated,
                    enrollmentsCreated, slotsCreated, notPlaced);
        }
    }

    @Transactional
    public Report seed(LocalDate today) {
        Counts c = new Counts();
        int startYear = academicYearStart(today);
        String academicYear = startYear + "-" + (startYear + 1);

        Map<Integer, Batch> cohortByYear = standardizeCohorts(startYear, c);
        Map<Long, List<Section>> sectionsByBatch = new HashMap<>();
        for (Batch b : cohortByYear.values()) {
            sectionsByBatch.put(b.getId(), ensureSections(b, c));
        }

        List<User> staffPool = staffPool();
        if (staffPool.isEmpty()) {
            logger.warn("Curriculum offerings not seeded: no active department STAFF account");
            return c.report();
        }
        User hod = users.findByEmail("hod@department.com").orElse(null);

        ensureDemoStudents(cohortByYear.get(1), sectionsByBatch.get(cohortByYear.get(1).getId()), c);

        Map<String, Subject> subjectByCode = ensureSubjects(c);
        deactivateLegacyOfferings(cohortByYear.get(2), c);
        deactivateNonConformingSlots(c);

        boolean oddTerm = isOddTerm(today);
        Map<Long, Integer> staffLoad = new HashMap<>();
        List<SubjectOffering> currentTerm = new ArrayList<>();
        for (int semester : new TreeSet<>(DevCurriculum.SEMESTERS.keySet())) {
            Batch cohort = cohortByYear.get(DevCurriculum.yearOfSemester(semester));
            boolean current = (semester % 2 == 1) == oddTerm;
            for (Section section : sectionsByBatch.get(cohort.getId())) {
                for (DevCurriculum.Course course : DevCurriculum.SEMESTERS.get(semester)) {
                    SubjectOffering o = ensureOffering(subjectByCode.get(course.code()), section, academicYear, semester,
                            staffPool, staffLoad, c);
                    if (current) currentTerm.add(o);
                }
            }
        }
        for (SubjectOffering o : currentTerm) {
            enrollSection(o, hod, c);
        }
        allocateTimetable(currentTerm, c);
        return c.report();
    }

    // ---- 1. cohorts ----

    private Map<Integer, Batch> standardizeCohorts(int startYear, Counts c) {
        Optional<Batch> roster = batches.findByBatchName(ROSTER_OLD_NAME)
                .filter(b -> b.getCurrentYear() != null && b.getCurrentYear() == 2);
        String rosterNewName = cohortName(startYear - 1);
        if (roster.isPresent() && batches.findByBatchName(rosterNewName).isEmpty()) {
            Batch b = roster.get();
            b.setBatchName(rosterNewName);
            b.setAdmissionYear(startYear - 1);
            b.setGraduationYear(startYear + 3);
            batches.saveAndFlush(b);
            c.renamed++;
            logger.info("Renamed batch {} (LeetCode Second-Year roster) to {}", ROSTER_OLD_NAME, rosterNewName);
        }

        Map<Integer, Batch> cohortByYear = new HashMap<>();
        for (int year = 1; year <= 4; year++) {
            int admission = startYear - year + 1;
            Batch b = batches.findByBatchName(cohortName(admission)).orElse(null);
            if (b == null) {
                b = batches.save(new Batch(cohortName(admission), admission, admission + 4, (short) year));
                c.batchesCreated++;
            }
            cohortByYear.put(year, b);
        }
        Set<Long> activeIds = new HashSet<>();
        cohortByYear.forEach((year, b) -> {
            activeIds.add(b.getId());
            if (!b.isActive() || b.getCurrentYear() == null || b.getCurrentYear() != year.shortValue()) {
                b.setActive(true);
                b.setCurrentYear(year.shortValue());
                c.yearsUpdated++;
            }
        });
        for (Batch b : batches.findAll()) {
            if (!activeIds.contains(b.getId()) && b.isActive()) {
                b.setActive(false);
                c.deactivated++;
            }
        }
        batches.flush();
        return cohortByYear;
    }

    private List<Section> ensureSections(Batch batch, Counts c) {
        Map<String, Section> byName = new TreeMap<>();
        sections.findByBatchIdOrderBySectionNameAsc(batch.getId()).forEach(s -> byName.put(s.getSectionName().toUpperCase(Locale.ROOT), s));
        for (String name : SECTION_NAMES) {
            if (!byName.containsKey(name)) {
                byName.put(name, sections.save(new Section(batch, name)));
                c.sectionsCreated++;
            }
        }
        return SECTION_NAMES.stream().map(byName::get).toList();
    }

    // ---- 3. demo students (new first-year cohort only) ----

    private void ensureDemoStudents(Batch firstYear, List<Section> sectionList, Counts c) {
        for (Section section : sectionList) {
            for (int i = 1; i <= DEMO_STUDENTS_PER_SECTION; i++) {
                String register = String.format("DEMO%d%s%02d", firstYear.getAdmissionYear(), section.getSectionName(), i);
                if (students.existsByRegisterNumber(register)) continue;
                Student s = new Student(register, "Demo Student " + firstYear.getAdmissionYear() + " "
                        + section.getSectionName() + String.format("%02d", i), null);
                s.setBatch(firstYear);
                s.setSection(section);
                s.setEmail(register.toLowerCase(Locale.ROOT) + "@student.erp.com");
                students.save(s);
                c.studentsCreated++;
            }
        }
        students.flush();
        String first = String.format("DEMO%d%s%02d", firstYear.getAdmissionYear(), sectionList.get(0).getSectionName(), 1);
        students.findFirstByEmailIgnoreCaseAndActiveTrue(first.toLowerCase(Locale.ROOT) + "@student.erp.com")
                .ifPresent(this::ensureLogin);
    }

    private void ensureLogin(Student student) {
        if (student.getUser() != null) return;
        String username = student.getRegisterNumber().toLowerCase(Locale.ROOT);
        if (users.findByEmail(student.getEmail()).isPresent() || users.existsByUsername(username)) return;
        Role role = roles.findByName("ROLE_STUDENT").orElse(null);
        if (role == null) return;
        User user = new User(username, student.getName(), student.getEmail(), passwordEncoder.encode(DEMO_PASSWORD));
        user.setDesignation("STUDENT");
        user.setActive(true);
        user.addRole(role);
        student.setUser(users.save(user));
        students.save(student);
        logger.info("Created demo student login '{}' for {}", student.getEmail(), student.getRegisterNumber());
    }

    // ---- 2. subjects ----

    private Map<String, Subject> ensureSubjects(Counts c) {
        Map<String, DevCurriculum.Course> courses = new LinkedHashMap<>();
        DevCurriculum.SEMESTERS.values().forEach(list -> list.forEach(course -> courses.putIfAbsent(course.code(), course)));
        Map<String, Subject> result = new HashMap<>();
        for (DevCurriculum.Course course : courses.values()) {
            Subject s = subjects.findByCodeIgnoreCase(course.code()).orElse(null);
            if (s == null) {
                s = new Subject(course.code(), course.name(), course.type());
                s.setCredits(course.credits());
                result.put(course.code(), subjects.save(s));
                c.subjectsCreated++;
                continue;
            }
            boolean changed = !course.name().equals(s.getName()) || !Objects.equals(course.credits(), s.getCredits()) || !s.isActive()
                    || (s.getSubjectType() == null && course.type() != null);
            if (changed) {
                s.setName(course.name());
                s.setCredits(course.credits());
                s.setActive(true);
                // An existing type is never overwritten.
                if (s.getSubjectType() == null) s.setSubjectType(course.type());
                c.subjectsUpdated++;
            }
            result.put(course.code(), s);
        }
        subjects.flush();
        return result;
    }

    // ---- 4. offerings ----

    /** Active department STAFF accounts; the demo login staff@department.com first so it teaches in every cohort. */
    private List<User> staffPool() {
        return users.findAll().stream()
                .filter(User::isActive)
                .filter(u -> u.getEmail() != null && u.getEmail().endsWith("@department.com"))
                .filter(u -> u.getRoles().stream().anyMatch(r -> "ROLE_STAFF".equals(r.getName()))
                        && u.getRoles().stream().noneMatch(r -> "ROLE_STUDENT".equals(r.getName())))
                .sorted(Comparator.comparing((User u) -> !"staff@department.com".equals(u.getEmail())).thenComparing(User::getId))
                .toList();
    }

    private SubjectOffering ensureOffering(Subject subject, Section section, String academicYear, int semester,
                                           List<User> staffPool, Map<Long, Integer> staffLoad, Counts c) {
        int hours = periodsPerWeek(subject);
        Optional<SubjectOffering> existing = offerings.findFirstBySubjectIdAndSectionIdAndAcademicYearAndSemesterAndActiveTrue(
                subject.getId(), section.getId(), academicYear, semester);
        if (existing.isPresent()) {
            staffLoad.merge(existing.get().getStaff().getId(), hours, Integer::sum);
            return existing.get();
        }
        // Least-loaded staff member (ties: pool order) keeps the weekly teaching load balanced.
        User staff = staffPool.stream()
                .min(Comparator.comparingInt((User u) -> staffLoad.getOrDefault(u.getId(), 0)))
                .orElseThrow();
        staffLoad.merge(staff.getId(), hours, Integer::sum);
        SubjectOffering o = new SubjectOffering();
        o.setSubject(subject);
        o.setBatch(section.getBatch());
        o.setSection(section);
        o.setStaff(staff);
        o.setAcademicYear(academicYear);
        o.setSemester(semester);
        o = offerings.save(o);
        internalMarks.ensureRecords(o);
        c.offeringsCreated++;
        return o;
    }

    private void deactivateLegacyOfferings(Batch rosterCohort, Counts c) {
        List<SubjectOffering> legacy = offerings.findBySubjectCodeInAndBatchIdAndActiveTrue(LEGACY_ROSTER_SUBJECTS, rosterCohort.getId());
        if (legacy.isEmpty()) return;
        List<Long> ids = legacy.stream().map(SubjectOffering::getId).toList();
        for (TimetableSlot t : slots.findBySubjectOfferingIdInAndActiveTrue(ids)) {
            t.setActive(false);
            c.slotsDeactivated++;
        }
        legacy.forEach(o -> o.setActive(false));
        c.legacyDeactivated += legacy.size();
        offerings.flush();
        slots.flush();
        logger.info("Deactivated {} legacy demo offerings ({}) of cohort {}", legacy.size(), LEGACY_ROSTER_SUBJECTS,
                rosterCohort.getBatchName());
    }

    /** Active slots whose times are not exactly one official teaching period are deactivated (rows kept). */
    private void deactivateNonConformingSlots(Counts c) {
        for (TimetableSlot t : slots.findAllActiveOfActiveOfferings()) {
            boolean conforms = InstitutionPeriodSchedule.teachingPeriod(t.getPeriodNumber())
                    .filter(p -> p.startTime().equals(t.getStartTime()) && p.endTime().equals(t.getEndTime()))
                    .isPresent();
            if (!conforms) {
                t.setActive(false);
                c.slotsDeactivated++;
            }
        }
        slots.flush();
    }

    // ---- 5. enrollments ----

    private void enrollSection(SubjectOffering offering, User hod, Counts c) {
        Set<Long> already = new HashSet<>(enrollments.findStudentIdsBySubjectOfferingId(offering.getId()));
        List<CourseEnrollment> toSave = new ArrayList<>();
        for (Student s : students.findBySectionIdAndActiveTrueOrderByRegisterNumberAsc(offering.getSection().getId())) {
            if (!already.contains(s.getId())) toSave.add(new CourseEnrollment(s, offering, hod));
        }
        enrollments.saveAll(toSave);
        c.enrollmentsCreated += toSave.size();
    }

    // ---- 6. demo timetable ----

    private record Cell(DayOfWeek day, int period) {
    }

    private void allocateTimetable(List<SubjectOffering> currentTerm, Counts c) {
        Map<Long, Set<Cell>> sectionBusy = new HashMap<>();
        Map<Long, Set<Cell>> staffBusy = new HashMap<>();
        Set<Long> scheduled = new HashSet<>();
        for (TimetableSlot t : slots.findAllActiveOfActiveOfferings()) {
            Cell cell = new Cell(t.getDayOfWeek(), t.getPeriodNumber());
            sectionBusy.computeIfAbsent(t.getSubjectOffering().getSection().getId(), k -> new HashSet<>()).add(cell);
            staffBusy.computeIfAbsent(t.getSubjectOffering().getStaff().getId(), k -> new HashSet<>()).add(cell);
            scheduled.add(t.getSubjectOffering().getId());
        }
        // Heaviest subjects first so they still find room; then section and code for a stable result.
        List<SubjectOffering> todo = currentTerm.stream()
                .filter(o -> !scheduled.contains(o.getId()))
                .sorted(Comparator.comparingInt((SubjectOffering o) -> -periodsPerWeek(o.getSubject()))
                        .thenComparing(o -> o.getSection().getId())
                        .thenComparing(o -> o.getSubject().getCode()))
                .toList();
        int rotation = 0;
        for (SubjectOffering o : todo) {
            Set<Cell> section = sectionBusy.computeIfAbsent(o.getSection().getId(), k -> new HashSet<>());
            Set<Cell> staff = staffBusy.computeIfAbsent(o.getStaff().getId(), k -> new HashSet<>());
            Set<DayOfWeek> usedDays = EnumSet.noneOf(DayOfWeek.class);
            for (int h = 0; h < periodsPerWeek(o.getSubject()); h++) {
                Cell cell = pickCell(section, staff, usedDays, rotation++);
                if (cell == null) {
                    c.notPlaced++;
                    logger.warn("Demo timetable: no free period for {} section {}", o.getSubject().getCode(), o.getSection().getSectionName());
                    continue;
                }
                section.add(cell);
                staff.add(cell);
                usedDays.add(cell.day());
                InstitutionPeriodSchedule.Period p = InstitutionPeriodSchedule.teachingPeriod(cell.period()).orElseThrow();
                TimetableSlot t = new TimetableSlot();
                t.setSubjectOffering(o);
                t.setDayOfWeek(cell.day());
                t.setPeriodNumber(cell.period());
                t.setStartTime(p.startTime());
                t.setEndTime(p.endTime());
                t.setSlotType(TimetableSlot.SlotType.LECTURE);
                slots.save(t);
                c.slotsCreated++;
            }
        }
        slots.flush();
    }

    /** A free cell for both section and staff; prefers a day the subject is not yet taught and a lightly used day. */
    private static Cell pickCell(Set<Cell> section, Set<Cell> staff, Set<DayOfWeek> usedDays, int rotation) {
        Cell best = null;
        int bestScore = Integer.MAX_VALUE;
        for (DayOfWeek day : TEACHING_DAYS) {
            long dayLoad = section.stream().filter(x -> x.day() == day).count();
            for (int i = 0; i < InstitutionPeriodSchedule.LAST_PERIOD; i++) {
                int period = (i + rotation) % InstitutionPeriodSchedule.LAST_PERIOD + 1;
                Cell cell = new Cell(day, period);
                if (section.contains(cell) || staff.contains(cell)) continue;
                int score = (usedDays.contains(day) ? 1000 : 0) + (int) dayLoad * 10 + i;
                if (score < bestScore) {
                    bestScore = score;
                    best = cell;
                }
            }
        }
        return best;
    }

    // ---- helpers ----

    static int periodsPerWeek(Subject subject) {
        return subject.getCredits() == null || subject.getCredits() == 0 ? 1 : subject.getCredits();
    }

    static String cohortName(int admissionYear) {
        return admissionYear + "-" + (admissionYear + 4);
    }

    /** June starts a new academic year: October 2026 -> 2026 (same rule as the other dev seeders). */
    static int academicYearStart(LocalDate today) {
        return today.getMonthValue() >= 6 ? today.getYear() : today.getYear() - 1;
    }

    /** Odd semesters run June-November, even semesters December-May. */
    static boolean isOddTerm(LocalDate today) {
        int m = today.getMonthValue();
        return m >= 6 && m <= 11;
    }
}

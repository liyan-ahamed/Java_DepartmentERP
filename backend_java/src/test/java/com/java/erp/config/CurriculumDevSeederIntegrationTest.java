package com.java.erp.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.java.erp.modules.academic.entity.Subject;
import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.academic.repository.SubjectOfferingRepository;
import com.java.erp.modules.academic.repository.SubjectRepository;
import com.java.erp.modules.auth.entity.User;
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
import com.java.erp.support.IntegrationTestBase;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs the dev curriculum seeder against the test database (inside the rolled-back test transaction)
 * for 11 October 2026, i.e. academic year 2026-2027, odd term.
 */
class CurriculumDevSeederIntegrationTest extends IntegrationTestBase {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 11);
    private static final String AY = "2026-2027";

    @Autowired private BatchRepository batchRepository;
    @Autowired private SectionRepository sectionRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private SubjectOfferingRepository offeringRepository;
    @Autowired private InternalMarksService internalMarksService;
    @Autowired private CourseEnrollmentRepository enrollmentRepository;
    @Autowired private TimetableSlotRepository slotRepository;
    @Autowired private EntityManager entityManager;

    private CurriculumDevSeeder seeder;
    private User hod;

    @BeforeEach
    void setUp() {
        seeder = new CurriculumDevSeeder(batchRepository, sectionRepository, studentRepository, userRepository,
                roleRepository, passwordEncoder, subjectRepository, offeringRepository, internalMarksService,
                enrollmentRepository, slotRepository);
        hod = createUser("ROLE_HOD", true);
    }

    private JsonNode data(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("data");
    }

    private JsonNode getAs(User user, String path) throws Exception {
        return data(mvc.perform(get(path).header("Authorization", bearer(user))).andExpect(status().isOk()));
    }

    private Batch cohort(String name) {
        return batchRepository.findByBatchName(name).orElseThrow();
    }

    @Test
    void exactlyFourActiveCohortsWithCurrentYearAndSections() throws Exception {
        Batch v10Roster = cohort("2023-2027");      // V10 demo data labels it Year 2
        Batch graduated = cohort("2022-2026");
        Set<Long> v10RosterSections = sectionRepository.findByBatchIdOrderBySectionNameAsc(v10Roster.getId()).stream()
                .map(Section::getId).collect(Collectors.toSet());

        seeder.seed(TODAY);

        JsonNode active = getAs(hod, "/batches");
        assertThat(active).hasSize(4);
        Map<String, Integer> years = new LinkedHashMap<>();
        active.forEach(b -> years.put(b.get("name").asText(), b.get("currentYear").asInt()));
        assertThat(years).containsExactly(Map.entry("2026-2030", 1), Map.entry("2025-2029", 2),
                Map.entry("2024-2028", 3), Map.entry("2023-2027", 4));

        // The roster batch was renamed in place (same id, same sections); a new 2023-2027 cohort exists.
        assertThat(cohort("2025-2029").getId()).isEqualTo(v10Roster.getId());
        assertThat(cohort("2023-2027").getId()).isNotEqualTo(v10Roster.getId());
        // Historical cohorts are kept but inactive and excluded from the lookup.
        assertThat(batchRepository.findById(graduated.getId())).get().matches(b -> !b.isActive());
        assertThat(active).noneMatch(b -> b.get("name").asText().equals("2022-2026"));

        for (JsonNode b : active) {
            JsonNode sections = getAs(hod, "/sections?batchId=" + b.get("id").asLong());
            assertThat(sections).extracting(s -> s.get("name").asText()).containsExactly("A", "B", "C", "D");
            assertThat(sections).allSatisfy(s -> assertThat(s.get("batchId").asLong()).isEqualTo(b.get("id").asLong()));
        }
        assertThat(sectionRepository.findByBatchIdOrderBySectionNameAsc(v10Roster.getId()))
                .extracting(Section::getId).containsAll(v10RosterSections);
    }

    @Test
    void semesterOneTwoAndThreeCurriculaWithCredits() throws Exception {
        seeder.seed(TODAY);
        Map<Integer, Map<String, Integer>> expected = Map.of(
                1, Map.ofEntries(Map.entry("2321CSS101J", 4), Map.entry("2321CSS103J", 4), Map.entry("2321CYB102T", 2),
                        Map.entry("2321GEH101T", 1), Map.entry("2321LEH101J", 3), Map.entry("2321MAB102T", 4),
                        Map.entry("2321GEM101B", 0), Map.entry("2321SDA101L", 1), Map.entry("ATT", 0), Map.entry("COUN", 0),
                        Map.entry("LC", 0), Map.entry("LIB", 0)),
                2, Map.ofEntries(Map.entry("2321ADC201J", 3), Map.entry("2321CSS204J", 4), Map.entry("2321CSS206J", 4),
                        Map.entry("2321CSS207J", 4), Map.entry("2321GEH201T", 1), Map.entry("2321MAB201T", 4),
                        Map.entry("2321PYB202J", 4), Map.entry("2321SDA201L", 1), Map.entry("AT", 0), Map.entry("COUN", 0),
                        Map.entry("LEET", 0), Map.entry("TT", 0), Map.entry("SS", 0), Map.entry("LIB", 0)),
                3, Map.ofEntries(Map.entry("2321ALC301J", 3), Map.entry("2321CSC301T", 3), Map.entry("2321CSC302J", 4),
                        Map.entry("2321CSC304R", 4), Map.entry("2321CSS301J", 4), Map.entry("2321MAB301T", 4),
                        Map.entry("2321SDA301L", 1), Map.entry("AT", 0), Map.entry("COUN", 0), Map.entry("NPTEL", 0),
                        Map.entry("TT", 0), Map.entry("LC", 0)));
        Map<Integer, String> cohortOfSemester = Map.of(1, "2026-2030", 2, "2026-2030", 3, "2025-2029");

        for (var e : expected.entrySet()) {
            Batch b = cohort(cohortOfSemester.get(e.getKey()));
            for (Section s : sectionRepository.findByBatchIdOrderBySectionNameAsc(b.getId())) {
                JsonNode page = getAs(hod, "/subject-offerings?size=100&active=true&batchId=" + b.getId()
                        + "&semester=" + e.getKey() + "&sectionId=" + s.getId());
                Map<String, Integer> found = new HashMap<>();
                page.get("content").forEach(o -> {
                    found.put(o.get("subjectCode").asText(), o.get("credits").asInt());
                    assertThat(o.get("academicYear").asText()).isEqualTo(AY);
                    assertThat(o.get("staffName").asText()).isNotBlank();
                });
                assertThat(found).as("semester %d section %s", e.getKey(), s.getSectionName()).isEqualTo(e.getValue());
            }
        }
        assertThat(subjectRepository.findByCodeIgnoreCase("2321GEH101T")).get()
                .extracting(Subject::getName).isEqualTo("Heritage of Tamils / தமிழர் மரபு");
    }

    @Test
    void onlyConfiguredCodeSuffixesAreClassified() {
        seeder.seed(TODAY);
        Map<String, Subject.SubjectType> configured = Map.of(
                "2321CSS101J", Subject.SubjectType.THEORY_CUM_PRACTICAL,
                "2321CYB102T", Subject.SubjectType.THEORY,
                "2321CSC304R", Subject.SubjectType.PROJECT_BASED);
        configured.forEach((code, type) ->
                assertThat(subjectRepository.findByCodeIgnoreCase(code)).get().extracting(Subject::getSubjectType).isEqualTo(type));
        for (String unclassified : List.of("2321SDA101L", "2321GEM101B", "COUN", "LIB", "NPTEL")) {
            assertThat(subjectRepository.findByCodeIgnoreCase(unclassified)).get().extracting(Subject::getSubjectType).isNull();
        }
    }

    @Test
    void secondRunChangesNothing() {
        seeder.seed(TODAY);
        long subjects = subjectRepository.count(), offerings = offeringRepository.count(), students = studentRepository.count(),
                enrollments = enrollmentRepository.count(), slots = slotRepository.count(), batches = batchRepository.count();

        CurriculumDevSeeder.Report again = seeder.seed(TODAY);

        assertThat(again).isEqualTo(new CurriculumDevSeeder.Report(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        assertThat(subjectRepository.count()).isEqualTo(subjects);
        assertThat(offeringRepository.count()).isEqualTo(offerings);
        assertThat(studentRepository.count()).isEqualTo(students);
        assertThat(enrollmentRepository.count()).isEqualTo(enrollments);
        assertThat(slotRepository.count()).isEqualTo(slots);
        assertThat(batchRepository.count()).isEqualTo(batches);
    }

    @Test
    void demoTimetableUsesOfficialPeriodsWithoutCollisions() {
        CurriculumDevSeeder.Report report = seeder.seed(TODAY);
        assertThat(report.periodsNotPlaced()).isZero();
        List<TimetableSlot> active = slotRepository.findAllActiveOfActiveOfferings();
        assertThat(active).isNotEmpty();

        Set<String> sectionCells = new HashSet<>();
        Set<String> staffCells = new HashSet<>();
        for (TimetableSlot t : active) {
            var period = InstitutionPeriodSchedule.teachingPeriod(t.getPeriodNumber()).orElseThrow();
            assertThat(t.getStartTime()).isEqualTo(period.startTime());
            assertThat(t.getEndTime()).isEqualTo(period.endTime());
            assertThat(InstitutionPeriodSchedule.overlapsNonTeaching(t.getStartTime(), t.getEndTime())).isFalse();
            SubjectOffering o = t.getSubjectOffering();
            assertThat(sectionCells.add(o.getSection().getId() + "|" + t.getDayOfWeek() + "|" + t.getPeriodNumber()))
                    .as("section collision").isTrue();
            assertThat(staffCells.add(o.getStaff().getId() + "|" + t.getDayOfWeek() + "|" + t.getPeriodNumber()))
                    .as("staff collision").isTrue();
        }
        // Only current-term offerings (semesters 1 and 3) are timetabled; credits = periods per week.
        Batch firstYear = cohort("2026-2030");
        Section a = sectionRepository.findByBatchIdOrderBySectionNameAsc(firstYear.getId()).get(0);
        Map<String, Long> perSubject = active.stream()
                .filter(t -> t.getSubjectOffering().getSection().getId().equals(a.getId()))
                .peek(t -> assertThat(t.getSubjectOffering().getSemester()).isEqualTo(1))
                .collect(Collectors.groupingBy(t -> t.getSubjectOffering().getSubject().getCode(), Collectors.counting()));
        assertThat(perSubject).containsEntry("2321CSS101J", 4L).containsEntry("2321CYB102T", 2L).containsEntry("LIB", 1L).hasSize(12);
    }

    @Test
    void studentsAndStaffResolveToTheSameOfferingsAsEnrollmentAndTimetable() throws Exception {
        seeder.seed(TODAY);
        Batch firstYear = cohort("2026-2030");
        Section a = sectionRepository.findByBatchIdOrderBySectionNameAsc(firstYear.getId()).get(0);
        List<Student> classA = studentRepository.findBySectionIdAndActiveTrueOrderByRegisterNumberAsc(a.getId());
        assertThat(classA).hasSize(10).allSatisfy(s -> assertThat(s.getName()).startsWith("Demo Student 2026 A"));

        Student demo = classA.get(0);
        assertThat(demo.getRegisterNumber()).isEqualTo("DEMO2026A01");
        assertThat(demo.getUser()).isNotNull();
        String studentToken = loginToken(demo.getEmail(), CurriculumDevSeeder.DEMO_PASSWORD);

        JsonNode courses = data(mvc.perform(get("/course-enrollments/me").header("Authorization", studentToken)).andExpect(status().isOk()));
        Set<Long> enrolled = new HashSet<>();
        courses.forEach(c -> {
            enrolled.add(c.get("subjectOfferingId").asLong());
            assertThat(c.get("semester").asInt()).isEqualTo(1);
            assertThat(c.get("status").asText()).isEqualTo("ENROLLED");
            assertThat(c.get("offeringActive").asBoolean()).isTrue();
        });
        assertThat(enrolled).hasSize(12);
        JsonNode week = data(mvc.perform(get("/timetable/me").header("Authorization", studentToken)).andExpect(status().isOk()));
        assertThat(week).isNotEmpty().allSatisfy(slot -> {
            assertThat(enrolled).contains(slot.get("subjectOfferingId").asLong());
            assertThat(slot.get("sectionId").asLong()).isEqualTo(a.getId());
        });

        // A teacher's timetable holds only offerings assigned to that teacher.
        SubjectOffering any = offeringRepository.findById(week.get(0).get("subjectOfferingId").asLong()).orElseThrow();
        User teacher = userRepository.findById(any.getStaff().getId()).orElseThrow();
        teacher.setPassword(passwordEncoder.encode(PASSWORD));
        userRepository.saveAndFlush(teacher);
        String staffToken = loginToken(teacher.getEmail(), PASSWORD);
        JsonNode staffWeek = data(mvc.perform(get("/timetable/me").header("Authorization", staffToken)).andExpect(status().isOk()));
        assertThat(staffWeek).isNotEmpty().allSatisfy(slot -> assertThat(slot.get("staffId").asLong()).isEqualTo(teacher.getId()));
        mvc.perform(get("/subject-offerings/" + any.getId()).header("Authorization", staffToken)).andExpect(status().isOk());

        // Every active curriculum offering is taught by an active STAFF user and its section belongs to its batch.
        offeringRepository.findAll().stream().filter(SubjectOffering::isActive)
                .filter(o -> o.getAcademicYear().equals(AY) && o.getSemester() <= 3)
                .forEach(o -> {
                    assertThat(o.getSection().getBatch().getId()).isEqualTo(o.getBatch().getId());
                    assertThat(o.getStaff().isActive()).isTrue();
                    assertThat(o.getStaff().getRoles()).anyMatch(r -> r.getName().equals("ROLE_STAFF"));
                });
    }

    @Test
    void legacyRosterOfferingsAreDeactivatedAndExistingStudentsAreUntouched() {
        Batch roster = cohort("2023-2027");
        Section rosterA = sectionRepository.findByBatchIdOrderBySectionNameAsc(roster.getId()).get(0);
        Subject legacySubject = subjectRepository.findByCodeIgnoreCase("CS205")
                .orElseGet(() -> subjectRepository.saveAndFlush(new Subject("CS205", "Discrete Mathematics", Subject.SubjectType.THEORY)));
        User teacher = createUser("ROLE_STAFF", true);
        SubjectOffering legacy = new SubjectOffering();
        legacy.setSubject(legacySubject);
        legacy.setBatch(roster);
        legacy.setSection(rosterA);
        legacy.setStaff(teacher);
        legacy.setAcademicYear(AY);
        legacy.setSemester(3);
        legacy = offeringRepository.saveAndFlush(legacy);

        Map<Long, List<Object>> before = snapshotStudents();
        seeder.seed(TODAY);
        entityManager.flush();
        entityManager.clear();

        assertThat(offeringRepository.findById(legacy.getId())).get().matches(o -> !o.isActive());
        Map<Long, List<Object>> after = snapshotStudents();
        before.forEach((id, row) -> assertThat(after.get(id)).as("student %d", id).isEqualTo(row));
    }

    /** Identity, placement and every LeetCode column of every existing student. */
    private Map<Long, List<Object>> snapshotStudents() {
        Map<Long, List<Object>> rows = new HashMap<>();
        for (Student s : studentRepository.findAll()) {
            LocalDateTime synced = s.getLeetcodeLastSyncedAt();
            rows.put(s.getId(), Arrays.asList(s.getRegisterNumber(), s.getName(), s.getBatch().getId(), s.getSection().getId(),
                    s.isActive(), s.getEmail(), s.getLeetcodeUrl(), s.getLeetcodeUsername(), s.getLeetcodeTotalSolved(),
                    s.getLeetcodeEasySolved(), s.getLeetcodeMediumSolved(), s.getLeetcodeHardSolved(), synced));
        }
        return rows;
    }

    private String loginToken(String email, String password) throws Exception {
        String body = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(body).get("data").get("accessToken").asText();
    }
}

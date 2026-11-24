package com.java.erp.modules.curriculum;

import com.fasterxml.jackson.databind.JsonNode;
import com.java.erp.modules.academic.entity.Subject;
import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.enrollment.entity.CourseEnrollment;
import com.java.erp.modules.system.entity.Student;
import com.java.erp.support.ErpModulesTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Curriculum configuration (validation, versioning, roles) and the registration status built on it. */
class CurriculumIntegrationTest extends ErpModulesTestBase {

    private Subject c1, c2, e1, e2, e3, notOffered;
    private final Map<Long, SubjectOffering> sectionA = new HashMap<>();
    private String path;

    @BeforeEach
    void curriculumFixture() {
        c1 = subject(Subject.SubjectType.THEORY);
        c2 = subject(Subject.SubjectType.THEORY);
        e1 = subject(Subject.SubjectType.THEORY);
        e2 = subject(Subject.SubjectType.THEORY);
        e3 = subject(Subject.SubjectType.THEORY);
        notOffered = subject(Subject.SubjectType.THEORY);
        for (Subject s : List.of(c1, c2, e1, e2, e3)) {
            sectionA.put(s.getId(), offering(s, section, staff));   // semester 3
        }
        path = "/curricula/batches/" + batch.getId() + "/semesters/3";
    }

    private Map<String, Object> group(Long id, String name, int required, Long... subjectIds) {
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("id", id);
        g.put("name", name);
        g.put("requiredSelections", required);
        g.put("subjectIds", List.of(subjectIds));
        return g;
    }

    private Map<String, Object> body(Long version, List<Long> compulsory, List<Map<String, Object>> groups) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("version", version);
        b.put("compulsorySubjectIds", compulsory);
        b.put("electiveGroups", groups);
        return b;
    }

    private JsonNode saveStandard() throws Exception {
        return data(putAs(hod, path, body(null, List.of(c1.getId(), c2.getId()),
                List.of(group(null, "Elective Group I", 1, e1.getId(), e2.getId(), e3.getId())))).andExpect(status().isOk()));
    }

    // ---- Configuration ----

    @Test
    void savesReadsUpdatesWithVersionAndDeletes() throws Exception {
        JsonNode empty = data(getAs(hod, path).andExpect(status().isOk()));
        assertThat(empty.get("configured").asBoolean()).isFalse();
        assertThat(empty.get("version").isNull()).isTrue();
        assertThat(empty.get("compulsorySubjects")).isEmpty();

        JsonNode saved = saveStandard();
        assertThat(saved.get("configured").asBoolean()).isTrue();
        assertThat(saved.get("batchName").asText()).isEqualTo(batch.getBatchName());
        assertThat(saved.get("compulsorySubjects")).extracting(n -> n.get("subjectId").asLong())
                .containsExactly(c1.getId(), c2.getId());
        JsonNode g = saved.get("electiveGroups").get(0);
        assertThat(g.get("requiredSelections").asInt()).isEqualTo(1);
        assertThat(g.get("displayOrder").asInt()).isEqualTo(1);
        assertThat(g.get("options")).extracting(n -> n.get("code").asText())
                .containsExactly(e1.getCode(), e2.getCode(), e3.getCode());
        assertThat(saved.get("updatedBy").asText()).isEqualTo(hod.getName());
        long version = saved.get("version").asLong();
        long groupId = g.get("id").asLong();

        // Stale or missing version -> 409, nothing changes.
        putAs(hod, path, body(version - 1, List.of(c1.getId()), List.of())).andExpect(status().isConflict());
        putAs(hod, path, body(null, List.of(c1.getId()), List.of())).andExpect(status().isConflict());

        // Update: c2 moves into the (renamed) group, which keeps its id; a new group is added.
        JsonNode updated = data(putAs(hod, path, body(version, List.of(c1.getId()), List.of(
                group(groupId, "Elective I", 2, e1.getId(), e2.getId(), c2.getId())))).andExpect(status().isOk()));
        assertThat(updated.get("version").asLong()).isGreaterThan(version);
        assertThat(updated.get("compulsorySubjects")).hasSize(1);
        assertThat(updated.get("electiveGroups").get(0).get("id").asLong()).isEqualTo(groupId);
        assertThat(updated.get("electiveGroups").get(0).get("name").asText()).isEqualTo("Elective I");
        assertThat(updated.get("electiveGroups").get(0).get("options")).hasSize(3);

        JsonNode removed = data(deleteAs(hod, path).andExpect(status().isOk()));
        assertThat(removed.get("configured").asBoolean()).isFalse();
        assertThat(data(getAs(hod, path)).get("configured").asBoolean()).isFalse();
        deleteAs(hod, path).andExpect(status().isNotFound());
    }

    @Test
    void invalidCurriculumIsRejectedWithEveryProblemAndNothingIsSaved() throws Exception {
        Subject inactive = subject(Subject.SubjectType.THEORY);
        offering(inactive, section, staff);
        inactive.setActive(false);
        subjectRepository.saveAndFlush(inactive);

        JsonNode errors = objectMapper.readTree(putAs(hod, path, body(null,
                List.of(c1.getId(), c1.getId(), notOffered.getId(), 999_999_999L, inactive.getId()),
                List.of(group(null, "G1", 3, e1.getId(), e2.getId()),          // more required than options
                        group(null, "g1", 1, e3.getId()),                      // duplicate name, single option
                        group(null, "G3", 0, c1.getId(), e1.getId()),          // 0 required, subjects placed twice
                        group(77L, "G4", 1, c2.getId(), c2.getId()))))         // foreign id, duplicate option
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString()).get("errors");
        List<String> messages = new ArrayList<>();
        errors.forEach(e -> messages.add(e.asText()));
        assertThat(messages).anyMatch(m -> m.startsWith("compulsorySubjectIds: subject " + c1.getId() + " is listed more than once"))
                .anyMatch(m -> m.contains(notOffered.getCode() + " has no active offering"))
                .anyMatch(m -> m.contains("subject 999999999 does not exist"))
                .anyMatch(m -> m.contains(inactive.getCode() + " is inactive"))
                .anyMatch(m -> m.startsWith("electiveGroups[0].requiredSelections: cannot exceed the number of options (2)"))
                .anyMatch(m -> m.startsWith("electiveGroups[1].name:"))
                .anyMatch(m -> m.startsWith("electiveGroups[1].subjectIds: an elective group needs at least 2 options"))
                .anyMatch(m -> m.startsWith("electiveGroups[2].requiredSelections: must be at least 1"))
                .anyMatch(m -> m.equals("electiveGroups[2].subjectIds: subject " + c1.getId() + " is already in compulsorySubjectIds (a subject may appear in only one place)"))
                .anyMatch(m -> m.contains("subject " + e1.getId() + " is already in electiveGroups[0]"))
                .anyMatch(m -> m.startsWith("electiveGroups[3].id: group 77 does not belong to this curriculum"))
                .anyMatch(m -> m.startsWith("electiveGroups[3].subjectIds: subject " + c2.getId() + " is listed more than once"));
        assertThat(data(getAs(hod, path)).get("configured").asBoolean()).isFalse();

        putAs(hod, path, body(null, List.of(), List.of())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(org.hamcrest.Matchers.startsWith("A curriculum needs at least one")));
        putAs(hod, "/curricula/batches/" + batch.getId() + "/semesters/9", body(null, List.of(c1.getId()), List.of()))
                .andExpect(status().isBadRequest());
        getAs(hod, "/curricula/batches/999999999/semesters/3").andExpect(status().isNotFound());
        // Unknown fields are refused (global ObjectMapper).
        putAs(hod, path, Map.of("compulsorySubjectIds", List.of(c1.getId()), "studentId", 1)).andExpect(status().isBadRequest());
    }

    @Test
    void onlyHodWritesStaffReadsStudentsAreRefused() throws Exception {
        saveStandard();
        getAs(staff, path).andExpect(status().isOk()).andExpect(jsonPath("$.data.configured").value(true));
        putAs(staff, path, body(null, List.of(c1.getId()), List.of())).andExpect(status().isForbidden());
        deleteAs(staff, path).andExpect(status().isForbidden());
        StudentLogin student = studentLogin(section);
        getAs(student.user(), path).andExpect(status().isForbidden());
        getAs(staff, "/course-enrollments/registration-status?batchId=" + batch.getId() + "&semester=3").andExpect(status().isForbidden());
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
    }

    // ---- Registration status ----

    private CourseEnrollment enrollIn(Subject s, Student student) {
        return enroll(sectionA.get(s.getId()), student);
    }

    @Test
    void registrationStatusIsAuthoritativeOneRowPerStudentFilteredBeforePaging() throws Exception {
        Student all = student(section);             // c1, c2, e2 -> ALL_REGISTERED
        Student noElective = student(section);      // c1, c2 -> INCOMPLETE
        Student none = student(section);            // nothing -> NOT_REGISTERED
        Student dropped = student(section);         // c1 DROPPED + c2 + e1 -> INCOMPLETE (dropped never counts)
        Student retired = student(section);         // only on a deactivated offering -> NOT_REGISTERED
        Student completed = student(otherSection);  // c1 COMPLETED, c2, e3 + extra -> ALL_REGISTERED
        for (Subject s : List.of(c1, c2, e2)) enrollIn(s, all);
        for (Subject s : List.of(c1, c2)) enrollIn(s, noElective);
        CourseEnrollment drop = enrollIn(c1, dropped);
        drop.setStatus(CourseEnrollment.Status.DROPPED);
        enrollmentRepository.saveAndFlush(drop);
        enrollIn(c2, dropped);
        enrollIn(e1, dropped);
        SubjectOffering old = offering(c1, otherSection, staff);
        old.setActive(false);
        offeringRepository.saveAndFlush(old);
        enroll(old, retired);
        CourseEnrollment done = enrollIn(c1, completed);
        done.setStatus(CourseEnrollment.Status.COMPLETED);
        enrollmentRepository.saveAndFlush(done);
        enrollIn(c2, completed);
        enrollIn(e3, completed);
        enroll(offering(notOffered, otherSection, staff), completed);

        String base = "/course-enrollments/registration-status?batchId=" + batch.getId() + "&semester=3";
        // Without a curriculum nobody is complete.
        JsonNode unknown = data(getAs(hod, base + "&size=200").andExpect(status().isOk()));
        assertThat(unknown.get("total_elements").asInt()).isEqualTo(6);
        Map<String, String> before = statuses(unknown);
        assertThat(before.get(all.getRegisterNumber())).isEqualTo("REQUIREMENTS_UNKNOWN");
        assertThat(before.get(none.getRegisterNumber())).isEqualTo("NOT_REGISTERED");
        assertThat(before.get(retired.getRegisterNumber())).isEqualTo("NOT_REGISTERED");
        assertThat(unknown.get("content").get(0).get("compulsoryRequired").isNull()).isTrue();
        assertThat(before).doesNotContainValue("ALL_REGISTERED");

        saveStandard();
        JsonNode whole = data(getAs(hod, base + "&size=200"));
        Map<String, String> after = statuses(whole);
        assertThat(after).containsEntry(all.getRegisterNumber(), "ALL_REGISTERED")
                .containsEntry(noElective.getRegisterNumber(), "INCOMPLETE")
                .containsEntry(none.getRegisterNumber(), "NOT_REGISTERED")
                .containsEntry(dropped.getRegisterNumber(), "INCOMPLETE")
                .containsEntry(retired.getRegisterNumber(), "NOT_REGISTERED")
                .containsEntry(completed.getRegisterNumber(), "ALL_REGISTERED");
        JsonNode droppedRow = row(whole, dropped);
        assertThat(droppedRow.get("compulsoryRegistered").asInt()).isEqualTo(1);
        assertThat(droppedRow.get("compulsoryRequired").asInt()).isEqualTo(2);
        assertThat(droppedRow.get("electiveGroupsSatisfied").asInt()).isEqualTo(1);
        assertThat(row(whole, completed).get("registeredCount").asInt()).isEqualTo(4);
        assertThat(row(whole, completed).get("sectionName").asText()).isEqualTo("B");

        // Register-number order, each student once.
        List<String> order = new ArrayList<>();
        whole.get("content").forEach(r -> order.add(r.get("registerNumber").asText()));
        assertThat(order).isSorted().doesNotHaveDuplicates();

        // Status filter runs before paging: 2 ALL_REGISTERED -> 2 pages of 1.
        JsonNode page0 = data(getAs(hod, base + "&registrationStatus=ALL_REGISTERED&size=1&page=0"));
        assertThat(page0.get("total_elements").asInt()).isEqualTo(2);
        assertThat(page0.get("total_pages").asInt()).isEqualTo(2);
        assertThat(page0.get("last").asBoolean()).isFalse();
        JsonNode page1 = data(getAs(hod, base + "&registrationStatus=ALL_REGISTERED&size=1&page=1"));
        assertThat(page1.get("content")).hasSize(1);
        assertThat(page1.get("last").asBoolean()).isTrue();
        assertThat(page0.get("content").get(0).get("studentId").asLong())
                .isNotEqualTo(page1.get("content").get(0).get("studentId").asLong());

        // Section filter, search.
        assertThat(data(getAs(hod, base + "&sectionId=" + section.getId())).get("total_elements").asInt()).isEqualTo(5);
        assertThat(data(getAs(hod, base + "&search=" + none.getRegisterNumber().toLowerCase())).get("total_elements").asInt())
                .isEqualTo(1);
        getAs(hod, base + "&registrationStatus=DONE").andExpect(status().isBadRequest());

        JsonNode summary = data(getAs(hod, "/course-enrollments/registration-status/summary?batchId=" + batch.getId()
                + "&semester=3").andExpect(status().isOk()));
        assertThat(summary.get("totalStudents").asInt()).isEqualTo(6);
        assertThat(summary.get("curriculumConfigured").asBoolean()).isTrue();
        assertThat(summary.get("counts").get("ALL_REGISTERED").asInt()).isEqualTo(2);
        assertThat(summary.get("counts").get("INCOMPLETE").asInt()).isEqualTo(2);
        assertThat(summary.get("counts").get("NOT_REGISTERED").asInt()).isEqualTo(2);
        assertThat(summary.get("counts").get("REQUIREMENTS_UNKNOWN").asInt()).isZero();
        assertThat(summary.get("offeredCourseCount").asInt()).isEqualTo(6);   // c1, c2, e1-e3 + notOffered (section B)

        JsonNode detail = data(getAs(hod, "/course-enrollments/registration-status/students/" + dropped.getId() + "?semester=3")
                .andExpect(status().isOk()));
        assertThat(detail.get("status").asText()).isEqualTo("INCOMPLETE");
        assertThat(detail.get("student").get("registerNumber").asText()).isEqualTo(dropped.getRegisterNumber());
        JsonNode c1Item = detail.get("compulsory").get(0);
        assertThat(c1Item.get("subjectId").asLong()).isEqualTo(c1.getId());
        assertThat(c1Item.get("registered").asBoolean()).isFalse();
        assertThat(c1Item.get("enrollmentStatus").asText()).isEqualTo("DROPPED");
        assertThat(c1Item.get("staffName").asText()).isEqualTo(staff.getName());
        JsonNode elective = detail.get("electiveGroups").get(0);
        assertThat(elective.get("selectedCount").asInt()).isEqualTo(1);
        assertThat(elective.get("satisfied").asBoolean()).isTrue();
        assertThat(elective.get("options")).filteredOn(o -> o.get("subjectId").asLong() == e2.getId()).singleElement()
                .satisfies(o -> {
                    assertThat(o.get("registered").asBoolean()).isFalse();
                    assertThat(o.get("enrollmentId").isNull()).isTrue();
                    assertThat(o.get("offeringId").asLong()).isEqualTo(sectionA.get(e2.getId()).getId());
                });

        JsonNode extra = data(getAs(hod, "/course-enrollments/registration-status/students/" + completed.getId() + "?semester=3"));
        assertThat(extra.get("otherRegistrations")).singleElement()
                .satisfies(o -> assertThat(o.get("subjectId").asLong()).isEqualTo(notOffered.getId()));
        getAs(hod, "/course-enrollments/registration-status/students/" + completed.getId() + "?semester=3&batchId=999")
                .andExpect(status().isBadRequest());
        getAs(hod, "/course-enrollments/registration-status/students/999999999?semester=3").andExpect(status().isNotFound());
    }

    private static Map<String, String> statuses(JsonNode page) {
        Map<String, String> result = new HashMap<>();
        page.get("content").forEach(r -> result.put(r.get("registerNumber").asText(), r.get("status").asText()));
        return result;
    }

    private static JsonNode row(JsonNode page, Student s) {
        for (JsonNode r : page.get("content")) {
            if (r.get("studentId").asLong() == s.getId()) return r;
        }
        throw new AssertionError("student " + s.getId() + " missing");
    }
}

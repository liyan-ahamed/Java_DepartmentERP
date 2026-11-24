package com.java.erp.modules.academic;

import com.fasterxml.jackson.databind.JsonNode;
import com.java.erp.modules.academic.entity.Subject;
import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.support.ErpModulesTestBase;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cohort -> Semester -> Section -> Subject filtering, credits, subject types and the offering summary. */
class CourseFilteringIntegrationTest extends ErpModulesTestBase {

    private SubjectOffering offering(Subject subject, com.java.erp.modules.system.entity.Section target,
                                     com.java.erp.modules.auth.entity.User owner, int semester) {
        SubjectOffering o = new SubjectOffering();
        o.setSubject(subject);
        o.setBatch(target.getBatch());
        o.setSection(target);
        o.setStaff(owner);
        o.setAcademicYear(ACADEMIC_YEAR);
        o.setSemester(semester);
        return offeringRepository.saveAndFlush(o);
    }

    private static Map<String, Object> subjectBody(String code, Object type, Object credits) {
        Map<String, Object> m = new HashMap<>();
        m.put("code", code);
        m.put("name", "Subject " + code);
        m.put("subjectType", type);
        m.put("credits", credits);
        return m;
    }

    @Test
    void offeringsFilterByBatchSemesterSectionAndSubject() throws Exception {
        Subject networks = subject(Subject.SubjectType.THEORY);
        Subject maths = subject(Subject.SubjectType.THEORY);
        SubjectOffering networksA = offering(networks, section, staff, 3);
        offering(networks, otherSection, otherStaff, 3);
        offering(maths, section, staff, 3);
        offering(maths, section, staff, 4);

        String base = "/subject-offerings?batchId=" + batch.getId();
        getAs(hod, base).andExpect(jsonPath("$.data.content.length()").value(4));
        getAs(hod, base + "&semester=3").andExpect(jsonPath("$.data.content.length()").value(3));
        getAs(hod, base + "&semester=3&sectionId=" + section.getId()).andExpect(jsonPath("$.data.content.length()").value(2));
        JsonNode one = data(getAs(hod, base + "&semester=3&sectionId=" + section.getId() + "&subjectId=" + networks.getId()));
        assertThat(one.get("content")).hasSize(1);
        JsonNode o = one.get("content").get(0);
        assertThat(o.get("id").asLong()).isEqualTo(networksA.getId());
        assertThat(o.get("subjectCode").asText()).isEqualTo(networks.getCode());
        assertThat(o.get("batchName").asText()).isEqualTo(batch.getBatchName());
        assertThat(o.get("sectionName").asText()).isEqualTo("A");
        assertThat(o.get("academicYear").asText()).isEqualTo(ACADEMIC_YEAR);
        assertThat(o.get("staffId").asLong()).isEqualTo(staff.getId());
        assertThat(o.get("active").asBoolean()).isTrue();

        // STAFF only ever see their own offerings, whatever the filters say.
        getAs(staff, base + "&semester=3").andExpect(jsonPath("$.data.content.length()").value(2));
        getAs(staff, base + "&staffId=" + otherStaff.getId()).andExpect(jsonPath("$.data.content.length()").value(3));
        getAs(otherStaff, "/subject-offerings/" + networksA.getId()).andExpect(status().isForbidden());
    }

    @Test
    void subjectsCarryCreditsAndMayStayUnclassifiedUntilTheHodSetsAType() throws Exception {
        String code = "ZX" + unique().toUpperCase();
        JsonNode created = data(postAs(hod, "/subjects", subjectBody(code, null, 0)).andExpect(status().isCreated()));
        assertThat(created.get("credits").asInt()).isZero();
        assertThat(created.get("subjectType").isNull()).isTrue();
        long id = created.get("id").asLong();

        // Offered while unclassified, then classified once; after that the type is fixed.
        Subject subject = subjectRepository.findById(id).orElseThrow();
        offering(subject, section, staff);
        putAs(hod, "/subjects/" + id, subjectBody(code, "PROJECT_BASED", 4))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subjectType").value("PROJECT_BASED"))
                .andExpect(jsonPath("$.data.credits").value(4));
        putAs(hod, "/subjects/" + id, subjectBody(code, "THEORY", 4)).andExpect(status().isConflict());
        putAs(hod, "/subjects/" + id, subjectBody(code, null, 4)).andExpect(status().isConflict());

        postAs(hod, "/subjects", subjectBody("NEG" + unique().toUpperCase(), "THEORY", -1)).andExpect(status().isBadRequest());
        getAs(hod, "/subject-offerings?subjectId=" + id).andExpect(jsonPath("$.data.content[0].credits").value(4));
        getAs(hod, "/subjects?subjectType=PROJECT_BASED&search=" + code).andExpect(jsonPath("$.data.content.length()").value(1));
    }

    @Test
    void offeringSummaryCountsEnrollmentsForHodAndAssignedStaffOnly() throws Exception {
        SubjectOffering offering = theoryOffering();
        enrollAll(offering, students(section, 3));

        JsonNode summary = data(getAs(hod, "/subject-offerings/" + offering.getId() + "/summary").andExpect(status().isOk()));
        assertThat(summary.get("subjectOfferingId").asLong()).isEqualTo(offering.getId());
        assertThat(summary.get("enrollmentCount").asLong()).isEqualTo(3);
        assertThat(summary.get("finalizedAttendanceSessions").asLong()).isZero();
        assertThat(summary.get("attendancePercentage").isNull()).isTrue();
        assertThat(summary.get("finalizedAssessmentCount").asLong()).isZero();
        assertThat(summary.get("averageAssessmentPercentage").isNull()).isTrue();
        assertThat(summary.get("publishedResultCount").asLong()).isZero();

        getAs(staff, "/subject-offerings/" + offering.getId() + "/summary").andExpect(status().isOk());
        getAs(otherStaff, "/subject-offerings/" + offering.getId() + "/summary").andExpect(status().isForbidden());
        StudentLogin student = studentLogin(section);
        getAs(student.user(), "/subject-offerings/" + offering.getId() + "/summary").andExpect(status().isForbidden());
    }

    @Test
    void attendanceOwnershipStillFollowsTheOffering() throws Exception {
        SubjectOffering own = theoryOffering();
        enrollAll(own, students(section, 2));
        postAs(staff, "/attendance/sessions", Map.of("subjectOfferingId", own.getId(), "attendanceDate", "2026-10-05"))
                .andExpect(status().isCreated());
        postAs(otherStaff, "/attendance/sessions", Map.of("subjectOfferingId", own.getId(), "attendanceDate", "2026-10-06"))
                .andExpect(status().isForbidden());
        JsonNode summary = data(getAs(hod, "/subject-offerings/" + own.getId() + "/summary"));
        assertThat(summary.get("finalizedAttendanceSessions").asLong()).isZero();   // draft sessions are not counted
        assertThat(List.of(summary.get("enrollmentCount").asLong())).containsExactly(2L);
    }
}

package com.java.erp.modules.enrollment;

import com.fasterxml.jackson.databind.JsonNode;
import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.enrollment.entity.CourseEnrollment;
import com.java.erp.modules.system.entity.Student;
import com.java.erp.support.ErpModulesTestBase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CourseEnrollmentIntegrationTest extends ErpModulesTestBase {

    @Test
    void hodEnrollsWholeSectionIdempotently() throws Exception {
        SubjectOffering offering = theoryOffering();
        List<Student> classList = students(section, 3);
        students(otherSection, 2);

        JsonNode first = data(postAs(hod, "/course-enrollments/subject-offerings/" + offering.getId() + "/enroll-section", null)
                .andExpect(status().isOk()));
        assertThat(first.get("activeStudentsInSection").asInt()).isEqualTo(3);
        assertThat(first.get("enrolled").asInt()).isEqualTo(3);

        JsonNode second = data(postAs(hod, "/course-enrollments/subject-offerings/" + offering.getId() + "/enroll-section", null)
                .andExpect(status().isOk()));
        assertThat(second.get("enrolled").asInt()).isZero();
        assertThat(second.get("alreadyEnrolled").asInt()).isEqualTo(3);
        assertThat(enrollmentRepository.findEnrolledStudents(offering.getId())).extracting(Student::getId)
                .containsExactlyInAnyOrderElementsOf(classList.stream().map(Student::getId).toList());
    }

    @Test
    void hodEnrollsIndividualStudentAndDuplicatesAreRejected() throws Exception {
        SubjectOffering offering = theoryOffering();
        Student student = student(otherSection); // same batch, other section: allowed (elective)

        postAs(hod, "/course-enrollments", Map.of("studentId", student.getId(), "subjectOfferingId", offering.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("ENROLLED"))
                .andExpect(jsonPath("$.data.studentId").value(student.getId()));
        postAs(hod, "/course-enrollments", Map.of("studentId", student.getId(), "subjectOfferingId", offering.getId()))
                .andExpect(status().isConflict());
    }

    @Test
    void studentFromAnotherBatchCannotBeEnrolled() throws Exception {
        SubjectOffering offering = theoryOffering();
        var otherBatch = batchRepository.save(new com.java.erp.modules.system.entity.Batch("OB-" + unique(), 2024, 2028, (short) 3));
        var foreignSection = sectionRepository.save(new com.java.erp.modules.system.entity.Section(otherBatch, "A"));
        Student outsider = student(foreignSection);
        postAs(hod, "/course-enrollments", Map.of("studentId", outsider.getId(), "subjectOfferingId", offering.getId()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void dropAndReactivateFollowTheStateMachine() throws Exception {
        SubjectOffering offering = theoryOffering();
        CourseEnrollment e = enroll(offering, student(section));

        putAs(hod, "/course-enrollments/" + e.getId() + "/status", Map.of("status", "DROPPED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DROPPED"));
        postAs(hod, "/course-enrollments", Map.of("studentId", e.getStudent().getId(), "subjectOfferingId", offering.getId()))
                .andExpect(status().isConflict());
        putAs(hod, "/course-enrollments/" + e.getId() + "/status", Map.of("status", "COMPLETED"))
                .andExpect(status().isConflict());
        putAs(hod, "/course-enrollments/" + e.getId() + "/status", Map.of("status", "ENROLLED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ENROLLED"));
    }

    @Test
    void onlyHodManagesEnrollments() throws Exception {
        SubjectOffering offering = theoryOffering();
        Student s = student(section);
        StudentLogin studentLogin = studentLogin(section);
        Map<String, Object> body = Map.of("studentId", s.getId(), "subjectOfferingId", offering.getId());

        postAs(staff, "/course-enrollments", body).andExpect(status().isForbidden());
        postAs(studentLogin.user(), "/course-enrollments", body).andExpect(status().isForbidden());
        postAs(staff, "/course-enrollments/subject-offerings/" + offering.getId() + "/enroll-section", null).andExpect(status().isForbidden());
        postAs(studentLogin.user(), "/course-enrollments/subject-offerings/" + offering.getId() + "/enroll-section", null).andExpect(status().isForbidden());
    }

    @Test
    void staffSeeRosterOfOwnOfferingsOnly() throws Exception {
        SubjectOffering own = theoryOffering();
        SubjectOffering foreign = offering(subject(com.java.erp.modules.academic.entity.Subject.SubjectType.THEORY), section, otherStaff);
        enrollAll(own, students(section, 2));
        enrollAll(foreign, students(section, 1));

        getAs(staff, "/course-enrollments/subject-offerings/" + own.getId())
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2));
        getAs(staff, "/course-enrollments/subject-offerings/" + foreign.getId()).andExpect(status().isForbidden());
        JsonNode list = data(getAs(staff, "/course-enrollments?size=200").andExpect(status().isOk()));
        for (JsonNode row : list.get("content")) {
            assertThat(row.get("staffId").asLong()).isEqualTo(staff.getId());
        }
        getAs(hod, "/course-enrollments/subject-offerings/" + foreign.getId()).andExpect(status().isOk());
    }

    @Test
    void studentSeesOwnEnrollmentsOnly() throws Exception {
        SubjectOffering offering = theoryOffering();
        StudentLogin me = studentLogin(section);
        enroll(offering, me.student());
        enroll(offering, student(section));

        getAs(me.user(), "/course-enrollments/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].studentId").value(me.student().getId()));
        getAs(me.user(), "/course-enrollments").andExpect(status().isForbidden());
        getAs(me.user(), "/course-enrollments/subject-offerings/" + offering.getId()).andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mvc.perform(get("/course-enrollments/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/course-enrollments")).andExpect(status().isUnauthorized());
    }
}

package com.java.erp.modules.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.java.erp.modules.academic.entity.Subject;
import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.system.entity.Student;
import com.java.erp.support.ErpModulesTestBase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /sections/{id}/students and the semester / batchId filters of GET /subjects. */
class SectionRosterAndCatalogFilterIntegrationTest extends ErpModulesTestBase {

    @Test
    void rosterListsEveryStudentPagedWithStaffScope() throws Exception {
        SubjectOffering own = theoryOffering();              // staff teaches section A
        List<Student> classList = students(section, 3);       // nobody enrolled, no logins
        StudentLogin withLogin = studentLogin(section);
        enroll(own, withLogin.student());
        Student left = student(section);
        left.setActive(false);
        studentRepository.saveAndFlush(left);

        String path = "/sections/" + section.getId() + "/students";
        JsonNode page0 = data(getAs(hod, path + "?size=2").andExpect(status().isOk()));
        assertThat(page0.get("total_elements").asInt()).isEqualTo(4);
        assertThat(page0.get("total_pages").asInt()).isEqualTo(2);
        JsonNode page1 = data(getAs(hod, path + "?size=2&page=1"));
        List<String> numbers = new ArrayList<>();
        page0.get("content").forEach(s -> numbers.add(s.get("registerNumber").asText()));
        page1.get("content").forEach(s -> numbers.add(s.get("registerNumber").asText()));
        assertThat(numbers).hasSize(4).isSorted().doesNotHaveDuplicates()
                .contains(classList.get(0).getRegisterNumber(), withLogin.student().getRegisterNumber())
                .doesNotContain(left.getRegisterNumber());
        JsonNode first = page0.get("content").get(0);
        assertThat(first.get("sectionName").asText()).isEqualTo("A");
        assertThat(first.get("batchId").asLong()).isEqualTo(batch.getId());

        getAs(hod, path + "?search=" + withLogin.student().getRegisterNumber().toLowerCase())
                .andExpect(jsonPath("$.data.total_elements").value(1))
                .andExpect(jsonPath("$.data.content[0].userId").value(withLogin.user().getId()));
        getAs(hod, path + "?active=false").andExpect(jsonPath("$.data.total_elements").value(1));

        getAs(staff, path).andExpect(status().isOk());
        getAs(otherStaff, path).andExpect(status().isForbidden());
        getAs(withLogin.user(), path).andExpect(status().isForbidden());
        getAs(hod, "/sections/999999999/students").andExpect(status().isNotFound());
        getAs(hod, path + "?size=500").andExpect(status().isBadRequest());
    }

    @Test
    void catalogFiltersByActiveOfferingsWithoutDuplicates() throws Exception {
        Subject twice = subject(Subject.SubjectType.THEORY);
        offering(twice, section, staff);         // semester 3, section A
        offering(twice, otherSection, staff);    // semester 3, section B: same subject must stay one row
        Subject sem5 = subject(Subject.SubjectType.THEORY);
        SubjectOffering five = offering(sem5, section, staff);
        five.setSemester(5);
        offeringRepository.saveAndFlush(five);
        Subject retired = subject(Subject.SubjectType.THEORY);
        SubjectOffering old = offering(retired, section, staff);
        old.setActive(false);
        offeringRepository.saveAndFlush(old);

        String base = "/subjects?size=100&batchId=" + batch.getId();
        JsonNode sem3 = data(getAs(hod, base + "&semester=3").andExpect(status().isOk()));
        assertThat(sem3.get("total_elements").asInt()).isEqualTo(1);
        JsonNode row = sem3.get("content").get(0);
        assertThat(row.get("id").asLong()).isEqualTo(twice.getId());
        assertThat(row.get("offeredIn")).singleElement().satisfies(t -> {
            assertThat(t.get("batchId").asLong()).isEqualTo(batch.getId());
            assertThat(t.get("semester").asInt()).isEqualTo(3);
        });
        JsonNode anySemester = data(getAs(hod, base));
        assertThat(anySemester.get("total_elements").asInt()).isEqualTo(2);   // twice + sem5; retired is not offered
        getAs(hod, base + "&semester=5&search=" + sem5.getCode()).andExpect(jsonPath("$.data.total_elements").value(1));
        getAs(hod, "/subjects?semester=9").andExpect(status().isBadRequest());
        // Without the new filters the endpoint still lists every subject (offeredIn may be empty).
        getAs(hod, "/subjects?search=" + retired.getCode()).andExpect(jsonPath("$.data.total_elements").value(1))
                .andExpect(jsonPath("$.data.content[0].offeredIn.length()").value(0));
    }
}

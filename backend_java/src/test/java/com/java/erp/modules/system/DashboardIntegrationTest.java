package com.java.erp.modules.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.java.erp.modules.auth.entity.User;
import com.java.erp.modules.system.entity.Batch;
import com.java.erp.modules.system.entity.Section;
import com.java.erp.modules.system.entity.Student;
import com.java.erp.modules.system.repository.BatchRepository;
import com.java.erp.modules.system.repository.SectionRepository;
import com.java.erp.modules.system.repository.StudentRepository;
import com.java.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DashboardIntegrationTest extends IntegrationTestBase {

    @Autowired private BatchRepository batchRepository;
    @Autowired private SectionRepository sectionRepository;
    @Autowired private StudentRepository studentRepository;

    private JsonNode summary(String token) throws Exception {
        String body = mvc.perform(get("/dashboard/summary").header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data");
    }

    private void addStudent(Section section, boolean active) {
        Student student = new Student("TR" + UUID.randomUUID().toString().substring(0, 8), "Dash Student", null);
        student.setBatch(section.getBatch());
        student.setSection(section);
        student.setActive(active);
        studentRepository.saveAndFlush(student);
    }

    private Section newSection(boolean activeBatch) {
        Batch batch = new Batch("TB-" + UUID.randomUUID().toString().substring(0, 8), 2025, 2029, (short) 1);
        batch.setActive(activeBatch);
        return sectionRepository.save(new Section(batchRepository.save(batch), "A"));
    }

    @Test
    void staffCountExcludesHodStudentsAndInactiveUsers() throws Exception {
        String token = bearer(createUser("ROLE_HOD", true));
        long before = summary(token).get("total_staff").asLong();

        createUser("ROLE_STAFF", true);
        createUser("ROLE_STAFF", false);
        createUser("ROLE_HOD", true);
        createUser("ROLE_STUDENT", true);

        assertThat(summary(token).get("total_staff").asLong()).isEqualTo(before + 1);
    }

    @Test
    void studentTotalsMatchBreakdownsAndSkipInactiveRecords() throws Exception {
        String token = bearer(createUser("ROLE_STAFF", true));
        long before = summary(token).get("total_students").asLong();

        Section active = newSection(true);
        addStudent(active, true);
        addStudent(active, true);
        addStudent(active, false);
        addStudent(newSection(false), true);

        JsonNode data = summary(token);
        long total = data.get("total_students").asLong();
        assertThat(total).isEqualTo(before + 2);

        long yearSum = 0;
        for (JsonNode year : data.get("year_wise_students")) yearSum += year.get("student_count").asLong();
        long sectionSum = 0;
        for (JsonNode sec : data.get("section_wise_students")) {
            sectionSum += sec.get("student_count").asLong();
            if (sec.get("section_id").asLong() == active.getId()) {
                assertThat(sec.get("student_count").asLong()).isEqualTo(2);
            }
        }
        assertThat(yearSum).isEqualTo(total);
        assertThat(sectionSum).isEqualTo(total);
    }

    @Test
    void dashboardRequiresAuthentication() throws Exception {
        mvc.perform(get("/dashboard/summary")).andExpect(status().isUnauthorized());
    }
}

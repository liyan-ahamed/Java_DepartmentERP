package com.java.erp.modules.schedule;

import com.java.erp.modules.auth.entity.User;
import com.java.erp.modules.schedule.entity.StudentSchedule;
import com.java.erp.modules.schedule.repository.StudentScheduleRepository;
import com.java.erp.support.AcademicTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StaffScheduleHistoryIntegrationTest extends AcademicTestBase {
    @Autowired private StudentScheduleRepository schedules;
    @Autowired private JdbcTemplate jdbc;

    private StudentSchedule schedule(User owner, StudentSchedule.Type type, String title) {
        var value = new StudentSchedule();
        value.setCreatedBy(owner); value.setType(type); value.setTitle(title);
        value.setDueDate(LocalDate.of(2027, 1, 20)); value.setDueTime(LocalTime.of(10, 30));
        value.getSections().add(section);
        if (type == StudentSchedule.Type.POLL) value.getOptions().addAll(List.of("Morning", "Afternoon"));
        return schedules.saveAndFlush(value);
    }

    @Test void pollsAndDeadlinesPageInGroupsOfFiveNewestFirstWithoutOtherStaffRecords() throws Exception {
        for (var type : StudentSchedule.Type.values()) {
            for (int i = 0; i < 12; i++) schedule(staff, type, type + " " + i);
            schedule(otherStaff, type, "Other staff item");
            // Equal timestamps must still give deterministic, non-overlapping pages.
            jdbc.update("UPDATE student_schedules SET created_at = '2026-11-01 10:00:00' WHERE created_by = ?", staff.getId());
            Set<Long> ids = new HashSet<>();
            for (int page = 0; page < 3; page++) {
                var result = data(mvc.perform(get("/student-schedules/history").param("type", type.name()).param("page", "" + page)
                        .header("Authorization", token(staff))).andExpect(status().isOk()));
                assertThat(result.path("size").asInt()).isEqualTo(5);
                assertThat(result.path("total_elements").asInt()).isEqualTo(12);
                assertThat(result.path("content")).hasSize(page == 2 ? 2 : 5);
                assertThat(result.path("last").asBoolean()).isEqualTo(page == 2);
                int index = 11 - page * 5;
                for (var item : result.path("content")) {
                    assertThat(item.path("schedule").path("title").asText()).isEqualTo(type + " " + index--);
                    assertThat(ids.add(item.path("schedule").path("id").asLong())).isTrue();
                    assertThat(item.path("targetClasses").get(0).asText()).contains("Year 2", "Section A", batch.getBatchName());
                    assertThat(item.path("schedule").path("due_time").asText()).isEqualTo("10:30:00");
                }
            }
            assertThat(ids).hasSize(12);
        }
    }

    @Test void deduplicatesClassAndIndividualTargetsAndShowsActualStudentResponses() throws Exception {
        var classStudent = studentLogin(section);
        var classStudent2 = studentLogin(section);
        var selectedStudent = studentLogin(otherSection);
        var inactive = student(section); inactive.setActive(false); studentRepository.saveAndFlush(inactive);
        var poll = schedule(staff, StudentSchedule.Type.POLL, "Choose slot");
        poll.getStudents().add(classStudent.student()); // Already counted by the class.
        poll.getStudents().add(selectedStudent.student());
        schedules.saveAndFlush(poll);
        for (var login : List.of(classStudent, selectedStudent)) {
            mvc.perform(post("/student-schedules/" + poll.getId() + "/response").header("Authorization", token(login.user()))
                    .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("option_index", 1))))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/student-schedules/history?type=POLL").header("Authorization", token(staff)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].assignedCount").value(3))
                .andExpect(jsonPath("$.data.content[0].respondedCount").value(2));
        var responses = data(mvc.perform(get("/student-schedules/" + poll.getId() + "/responses")
                .header("Authorization", token(staff))).andExpect(status().isOk()));
        assertThat(responses.path("content")).hasSize(2);
        for (var row : responses.path("content")) {
            assertThat(row.path("selectedOption").asText()).isEqualTo("Afternoon");
            assertThat(row.path("studentName").asText()).isNotBlank();
            assertThat(row.path("registerNumber").asText()).isNotBlank();
            assertThat(row.path("targetClass").asText()).contains("Year 2", batch.getBatchName());
            assertThat(row.path("respondedAt").asText()).isNotBlank();
        }
        // Historical responses remain accounted for after a student's roster status changes.
        selectedStudent.student().setActive(false); studentRepository.saveAndFlush(selectedStudent.student());
        mvc.perform(get("/student-schedules/history?type=POLL").header("Authorization", token(staff)))
                .andExpect(jsonPath("$.data.content[0].assignedCount").value(3))
                .andExpect(jsonPath("$.data.content[0].respondedCount").value(2));
        assertThat(classStudent2.student().isActive()).isTrue();
    }

    @Test void responseDetailsAreAlsoPaginatedAndOwnerOnly() throws Exception {
        var poll = schedule(staff, StudentSchedule.Type.POLL, "Response paging");
        for (int i = 0; i < 7; i++) {
            var login = studentLogin(section);
            mvc.perform(post("/student-schedules/" + poll.getId() + "/response").header("Authorization", token(login.user()))
                    .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("option_index", 0))))
                    .andExpect(status().isOk());
        }
        for (int page = 0; page < 2; page++) {
            mvc.perform(get("/student-schedules/" + poll.getId() + "/responses?page=" + page).header("Authorization", token(staff)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.content.length()").value(page == 0 ? 5 : 2))
                    .andExpect(jsonPath("$.data.total_elements").value(7));
        }
        mvc.perform(get("/student-schedules/" + poll.getId() + "/responses").header("Authorization", token(otherStaff)))
                .andExpect(status().isForbidden());
        for (var user : List.of(hod, createUser("ROLE_STUDENT", true))) {
            mvc.perform(get("/student-schedules/history?type=POLL").header("Authorization", token(user))).andExpect(status().isForbidden());
            mvc.perform(get("/student-schedules/" + poll.getId() + "/responses").header("Authorization", token(user)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test void invalidOrAnonymousRequestsAreRejected() throws Exception {
        mvc.perform(get("/student-schedules/history?type=POLL")).andExpect(status().isUnauthorized());
        mvc.perform(get("/student-schedules/history?type=POLL&page=-1").header("Authorization", token(staff)))
                .andExpect(status().isBadRequest());
        var deadline = schedule(staff, StudentSchedule.Type.DEADLINE, "Deadline");
        mvc.perform(get("/student-schedules/" + deadline.getId() + "/responses").header("Authorization", token(staff)))
                .andExpect(status().isBadRequest());
    }
}

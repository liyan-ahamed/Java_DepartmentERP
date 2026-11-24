package com.java.erp.modules.calendar;

import com.java.erp.modules.auth.entity.User;
import com.java.erp.modules.calendar.repository.CalendarEntryRepository;
import com.java.erp.modules.holiday.entity.Holiday;
import com.java.erp.modules.holiday.repository.HolidayRepository;
import com.java.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CalendarIntegrationTest extends IntegrationTestBase {
    @Autowired private CalendarEntryRepository entries;
    @Autowired private HolidayRepository holidays;
    @Autowired private JdbcTemplate jdbc;
    private User staff, hod, student;
    private static final String MONTH = "/calendar?dateFrom=2044-02-01&dateTo=2044-02-29";

    @BeforeEach void users() {
        staff = createUser("ROLE_STAFF", true);
        hod = createUser("ROLE_HOD", true);
        student = createUser("ROLE_STUDENT", true);
    }

    private String body(String title, boolean holiday) throws Exception {
        return json(Map.of("date", "2044-02-29", "title", title, "holiday", holiday));
    }

    @Test void onlyStaffCanCreateButAllThreeRolesCanRead() throws Exception {
        for (User user : List.of(hod, student)) {
            mvc.perform(post("/calendar").header("Authorization", bearer(user)).contentType(MediaType.APPLICATION_JSON)
                    .content(body("Presentation", false))).andExpect(status().isForbidden());
        }
        mvc.perform(post("/calendar").header("Authorization", bearer(staff)).contentType(MediaType.APPLICATION_JSON)
                .content(body("  Group Presentation Day  ", false)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.title").value("Group Presentation Day"))
                .andExpect(jsonPath("$.data.holiday").value(false));
        for (User user : List.of(staff, hod, student)) {
            mvc.perform(get(MONTH).header("Authorization", bearer(user))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.entries[0].title").value("Group Presentation Day"))
                    .andExpect(jsonPath("$.data.entries[0].startDate").value("2044-02-29"));
        }
        assertThat(entries.findByEventDateBetweenOrderByEventDateAscIdAsc(LocalDate.of(2044, 2, 29), LocalDate.of(2044, 2, 29)))
                .singleElement().satisfies(e -> assertThat(e.getCreatedBy().getId()).isEqualTo(staff.getId()));
    }

    @Test void eventsAndHolidaysNotifyEveryActiveStudentAndHodThroughExistingInbox() throws Exception {
        User inactive = createUser("ROLE_STUDENT", false);
        User otherStaff = createUser("ROLE_STAFF", true);
        for (boolean holiday : List.of(false, true)) {
            var response = mvc.perform(post("/calendar").header("Authorization", bearer(staff))
                    .contentType(MediaType.APPLICATION_JSON).content(body(holiday ? "Department holiday" : "Presentation", holiday)))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.data.holiday").value(holiday))
                    .andReturn().getResponse().getContentAsString();
            long id = Long.parseLong(objectMapper.readTree(response).path("data").path("id").asText().replace("entry-", ""));
            var recipients = jdbc.queryForList("SELECT recipient_id FROM notifications WHERE module = 'CALENDAR' AND reference_id = ?", Long.class, id);
            assertThat(recipients).containsExactlyInAnyOrderElementsOf(userRepository.findActiveIdsByRoleNames(List.of("ROLE_STUDENT", "ROLE_HOD")));
            assertThat(recipients).doesNotContain(staff.getId(), otherStaff.getId(), inactive.getId());
            for (User recipient : List.of(student, hod)) {
                String inbox = mvc.perform(get("/notifications").param("size", "100").header("Authorization", bearer(recipient)))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
                assertThat(objectMapper.readTree(inbox).path("data").path("content"))
                        .anySatisfy(n -> {
                            assertThat(n.path("module").asText()).isEqualTo("CALENDAR");
                            assertThat(n.path("reference_id").asLong()).isEqualTo(id);
                            assertThat(n.path("message").asText()).contains("2044-02-29");
                        });
            }
        }
    }

    @Test void governmentCacheIsReusedDeduplicatedAndVisibleWithoutPublishing() throws Exception {
        LocalDate date = LocalDate.of(2044, 2, 10);
        holidays.saveAndFlush(new Holiday(date, "Government holiday", Holiday.Scope.NATIONAL, "Government dataset", LocalDateTime.now()));
        holidays.saveAndFlush(new Holiday(date, "Government holiday", Holiday.Scope.TAMIL_NADU, "State dataset", LocalDateTime.now()));
        mvc.perform(get(MONTH).header("Authorization", bearer(student))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entries.length()").value(1))
                .andExpect(jsonPath("$.data.entries[0].government").value(true))
                .andExpect(jsonPath("$.data.entries[0].holiday").value(true))
                .andExpect(jsonPath("$.data.governmentYearsAvailable[0]").value(2044));
    }

    @Test void dateRangesIncludeBoundariesAndExcludeOtherMonths() throws Exception {
        mvc.perform(post("/calendar").header("Authorization", bearer(staff)).contentType(MediaType.APPLICATION_JSON)
                .content(body("Leap day", true))).andExpect(status().isCreated());
        mvc.perform(get("/calendar?dateFrom=2044-02-29&dateTo=2044-02-29").header("Authorization", bearer(hod)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.entries.length()").value(1));
        mvc.perform(get("/calendar?dateFrom=2044-03-01&dateTo=2044-03-31").header("Authorization", bearer(hod)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.entries").isEmpty())
                .andExpect(jsonPath("$.data.governmentYearsAvailable").isEmpty());
    }

    @Test void invalidRequestsCannotPersistOrNotify() throws Exception {
        long before = entries.count();
        for (String invalid : List.of(body("   ", false), body("x".repeat(256), false),
                "{\"date\":\"2044-02-30\",\"title\":\"Invalid\",\"holiday\":false}",
                "{\"title\":\"Missing date\",\"holiday\":false}",
                "{\"date\":\"2044-02-29\",\"title\":\"Missing kind\"}")) {
            mvc.perform(post("/calendar").header("Authorization", bearer(staff)).contentType(MediaType.APPLICATION_JSON)
                    .content(invalid)).andExpect(status().isBadRequest());
        }
        assertThat(entries.count()).isEqualTo(before);
        mvc.perform(get("/calendar?dateFrom=2044-03-01&dateTo=2044-02-01").header("Authorization", bearer(staff)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/calendar?dateFrom=2044-01-01&dateTo=2046-01-01").header("Authorization", bearer(staff)))
                .andExpect(status().isBadRequest());
    }

    @Test void authenticationIsRequired() throws Exception {
        mvc.perform(get(MONTH)).andExpect(status().isUnauthorized());
        mvc.perform(post("/calendar").contentType(MediaType.APPLICATION_JSON).content(body("No session", false)))
                .andExpect(status().isUnauthorized());
    }
}

package com.java.erp.modules.holiday;

import com.fasterxml.jackson.databind.JsonNode;
import com.java.erp.modules.auth.entity.User;
import com.java.erp.modules.holiday.entity.Holiday;
import com.java.erp.modules.holiday.repository.HolidayRepository;
import com.java.erp.modules.system.entity.Batch;
import com.java.erp.modules.system.entity.Section;
import com.java.erp.support.ErpModulesTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HOD holiday management, calendar publication and who sees what (years 2032+ keep clear of real data). */
class HolidayCalendarManagementIntegrationTest extends ErpModulesTestBase {

    @Autowired private HolidayRepository holidayRepository;

    private static Map<String, Object> holiday(String name, String start, String end, String category, List<Long> batchIds) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("startDate", start);
        body.put("endDate", end);
        body.put("category", category);
        body.put("description", null);
        body.put("batchIds", batchIds);
        return body;
    }

    private JsonNode create(Map<String, Object> body) throws Exception {
        return data(postAs(hod, "/holidays", body).andExpect(status().isCreated()));
    }

    @Test
    void draftsAreHodOnlyUntilPublishedAndNeverAffectAttendance() throws Exception {
        theoryOffering();   // gives the section a class, so the attendance day is judged for this cohort
        JsonNode created = create(holiday("Founders Day", "2032-03-10", null, "INSTITUTIONAL", List.of()));
        assertThat(created.get("calendarStatus").asText()).isEqualTo("DRAFT");
        assertThat(created.get("date").asText()).isEqualTo("2032-03-10");
        assertThat(created.get("startDate").asText()).isEqualTo("2032-03-10");
        assertThat(created.get("endDate").isNull()).isTrue();
        assertThat(created.get("scope").asText()).isEqualTo("COLLEGE");
        assertThat(created.get("batchIds")).isEmpty();

        StudentLogin student = studentLogin(section);
        for (User user : List.of(staff, student.user())) {
            getAs(user, "/holidays?year=2032").andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
            getAs(user, "/holidays/calendars/2032").andExpect(jsonPath("$.data.status").value("NOT_PUBLISHED"))
                    .andExpect(jsonPath("$.data.holidayCount").isEmpty());
        }
        getAs(hod, "/holidays?year=2032").andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].calendarStatus").value("DRAFT"));
        getAs(hod, "/holidays/calendars/2032").andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.holidayCount").value(1));
        // A draft never marks a day as a holiday.
        String day = "/attendance/day?date=2032-03-10&sectionId=" + section.getId();
        assertThat(data(getAs(hod, day)).get("holiday").asBoolean()).isFalse();

        JsonNode published = data(postAs(hod, "/holidays/calendars/2032/publish", null).andExpect(status().isOk()));
        assertThat(published.get("status").asText()).isEqualTo("PUBLISHED");
        assertThat(published.get("publishedBy").asText()).isEqualTo(hod.getName());
        assertThat(published.get("publishedAt").isNull()).isFalse();
        postAs(hod, "/holidays/calendars/2032/publish", null).andExpect(status().isConflict());
        for (User user : List.of(staff, student.user())) {
            getAs(user, "/holidays?year=2032").andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].name").value("Founders Day"));
            getAs(user, "/holidays/calendars/2032").andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                    .andExpect(jsonPath("$.data.holidayCount").value(1));
        }
        JsonNode holidayDay = data(getAs(hod, day));
        assertThat(holidayDay.get("holiday").asBoolean()).isTrue();
        assertThat(holidayDay.get("holidayName").asText()).isEqualTo("Founders Day");
        assertThat(holidayDay.get("holidayCategory").asText()).isEqualTo("INSTITUTIONAL");

        // Published years stay editable; the change is visible at once.
        long id = created.get("id").asLong();
        putAs(hod, "/holidays/" + id, holiday("Founders' Day", "2032-03-11", null, "INSTITUTIONAL", List.of()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.calendarStatus").value("PUBLISHED"));
        getAs(staff, "/holidays?year=2032").andExpect(jsonPath("$.data[0].date").value("2032-03-11"));

        postAs(hod, "/holidays/calendars/2032/unpublish", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
        postAs(hod, "/holidays/calendars/2032/unpublish", null).andExpect(status().isConflict());
        getAs(staff, "/holidays?year=2032").andExpect(jsonPath("$.data.length()").value(0));

        deleteAs(hod, "/holidays/" + id).andExpect(status().isOk());
        getAs(hod, "/holidays?year=2032").andExpect(jsonPath("$.data.length()").value(0));
        deleteAs(hod, "/holidays/" + id).andExpect(status().isNotFound());
    }

    @Test
    void anEmptyPublishedCalendarDiffersFromAnUnpublishedOne() throws Exception {
        getAs(hod, "/holidays/calendars/2033").andExpect(jsonPath("$.data.status").value("NOT_CREATED"))
                .andExpect(jsonPath("$.data.holidayCount").value(0));
        postAs(hod, "/holidays/calendars/2033/publish", null).andExpect(status().isOk());
        getAs(staff, "/holidays/calendars/2033").andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.holidayCount").value(0));
        getAs(staff, "/holidays/calendars/2034").andExpect(jsonPath("$.data.status").value("NOT_PUBLISHED"));
        getAs(hod, "/holidays/calendars/1999").andExpect(status().isBadRequest());
    }

    @Test
    void onlyHodWritesAndPublishes() throws Exception {
        long id = create(holiday("Sports Day", "2032-02-02", null, "OTHER", List.of())).get("id").asLong();
        StudentLogin student = studentLogin(section);
        Map<String, Object> body = holiday("Other", "2032-02-03", null, "OTHER", List.of());
        for (User user : List.of(staff, student.user())) {
            postAs(user, "/holidays", body).andExpect(status().isForbidden());
            putAs(user, "/holidays/" + id, body).andExpect(status().isForbidden());
            deleteAs(user, "/holidays/" + id).andExpect(status().isForbidden());
            postAs(user, "/holidays/calendars/2032/publish", null).andExpect(status().isForbidden());
            postAs(user, "/holidays/calendars/2032/unpublish", null).andExpect(status().isForbidden());
        }
        mvc.perform(post("/holidays").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/holidays/calendars/2032/publish")).andExpect(status().isUnauthorized());
        assertThat(holidayRepository.findById(id)).isPresent();
    }

    @Test
    void invalidHolidaysAreRejected() throws Exception {
        postAs(hod, "/holidays", holiday("Winter Break", "2032-12-28", "2033-01-02", "ACADEMIC_BREAK", List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(org.hamcrest.Matchers.containsString("cannot cross into another calendar year")));
        postAs(hod, "/holidays", holiday("Long", "2032-01-01", "2032-03-01", "ACADEMIC_BREAK", List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(org.hamcrest.Matchers.containsString("at most 60 days")));
        postAs(hod, "/holidays", holiday("Backwards", "2032-05-05", "2032-05-01", "OTHER", List.of()))
                .andExpect(status().isBadRequest());
        postAs(hod, "/holidays", holiday("Gov", "2032-05-05", null, "PUBLIC_HOLIDAY", List.of()))
                .andExpect(status().isBadRequest());
        postAs(hod, "/holidays", holiday("Nobody", "2032-05-05", null, "OTHER", List.of(999_999_999L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("batchIds: cohort 999999999 does not exist"));
        postAs(hod, "/holidays", holiday(" ", "2032-05-05", null, "OTHER", List.of())).andExpect(status().isBadRequest());
        postAs(hod, "/holidays", holiday("X", "2032-05-05", null, "FESTIVAL", List.of())).andExpect(status().isBadRequest());
        postAs(hod, "/holidays", holiday("X", "1990-05-05", null, "OTHER", List.of())).andExpect(status().isBadRequest());

        create(holiday("Pongal", "2032-01-14", "2032-01-16", "STATE", List.of()));
        postAs(hod, "/holidays", holiday("PONGAL", "2032-06-01", null, "STATE", List.of())).andExpect(status().isConflict());

        // Government reference rows are read-only.
        Holiday national = holidayRepository.saveAndFlush(new Holiday(LocalDate.of(2032, 8, 15), "Independence Day",
                Holiday.Scope.NATIONAL, "test", LocalDateTime.now()));
        putAs(hod, "/holidays/" + national.getId(), holiday("Independence Day", "2032-08-15", null, "NATIONAL", List.of()))
                .andExpect(status().isConflict());
        deleteAs(hod, "/holidays/" + national.getId()).andExpect(status().isConflict());
    }

    @Test
    void governmentListsAndOtherCohortsNeverMarkAClassAsHoliday() throws Exception {
        theoryOffering();
        Batch otherBatch = batchRepository.save(new Batch("HB-" + unique(), 2026, 2030, (short) 1));
        Section otherBatchSection = sectionRepository.save(new Section(otherBatch, "A"));
        offering(subject(com.java.erp.modules.academic.entity.Subject.SubjectType.THEORY), otherBatchSection, staff);
        // 2032-09-06..08 (Mon-Wed): cohort-only break for this batch; 2032-09-09: national list only.
        JsonNode range = create(holiday("Cohort trip", "2032-09-06", "2032-09-08", "ACADEMIC_BREAK", List.of(batch.getId())));
        assertThat(range.get("batchIds")).extracting(JsonNode::asLong).containsExactly(batch.getId());
        holidayRepository.saveAndFlush(new Holiday(LocalDate.of(2032, 9, 9), "Some national day", Holiday.Scope.NATIONAL,
                "test", LocalDateTime.now()));
        postAs(hod, "/holidays/calendars/2032/publish", null).andExpect(status().isOk());

        // Default list = college only; the government row needs an explicit scope.
        getAs(staff, "/holidays?year=2032").andExpect(jsonPath("$.data.length()").value(1));
        getAs(staff, "/holidays?year=2032&scope=NATIONAL").andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].category").value("PUBLIC_HOLIDAY"))
                .andExpect(jsonPath("$.data[0].calendarStatus").isEmpty());
        getAs(staff, "/holidays?year=2032&batchId=" + otherBatch.getId()).andExpect(jsonPath("$.data.length()").value(0));
        getAs(staff, "/holidays?dateFrom=2032-09-07&dateTo=2032-09-07").andExpect(jsonPath("$.data.length()").value(1));

        String mine = "/attendance/day?sectionId=" + section.getId() + "&date=";
        String theirs = "/attendance/day?sectionId=" + otherBatchSection.getId() + "&date=";
        assertThat(data(getAs(hod, mine + "2032-09-07")).get("holiday").asBoolean()).isTrue();      // middle of the range
        assertThat(data(getAs(hod, mine + "2032-09-08")).get("holiday").asBoolean()).isTrue();      // inclusive end
        assertThat(data(getAs(hod, theirs + "2032-09-07")).get("holiday").asBoolean()).isFalse();   // other cohort
        assertThat(data(getAs(hod, mine + "2032-09-09")).get("holiday").asBoolean()).isFalse();     // government only
    }
}

package com.java.erp.modules.holiday;

import com.fasterxml.jackson.databind.JsonNode;
import com.java.erp.modules.holiday.entity.Holiday;
import com.java.erp.modules.holiday.repository.HolidayRepository;
import com.java.erp.support.ErpModulesTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The college calendar (real bundled files, no network) is the effective 2026 calendar. */
class CollegeHolidayCalendarIntegrationTest extends ErpModulesTestBase {

    @Autowired private HolidayRepository holidayRepository;

    private JsonNode sync2026() throws Exception {
        return data(postAs(hod, "/holidays/sync?year=2026", null).andExpect(status().isOk()));
    }

    @Test
    void effective2026CalendarIsExactlyTheCollegeCalendar() throws Exception {
        JsonNode result = sync2026();
        assertThat(result.get("status").asText()).isEqualTo("SYNCED");

        JsonNode holidays = data(getAs(hod, "/holidays?year=2026").andExpect(status().isOk()));
        assertThat(holidays).hasSize(24);
        Set<String> dates = new HashSet<>();
        Map<String, String> byDate = new LinkedHashMap<>();
        for (JsonNode h : holidays) {
            assertThat(dates.add(h.get("date").asText())).as("duplicate effective date %s", h.get("date")).isTrue();
            assertThat(h.get("category").asText()).isEqualTo("GENERAL_HOLIDAY");
            assertThat(h.get("scope").asText()).isEqualTo("COLLEGE");
            byDate.put(h.get("date").asText(), h.get("name").asText());
        }
        assertThat(byDate)
                .containsEntry("2026-01-23", "Project Day - Classes Suspended")
                .containsEntry("2026-04-23", "Legislative Assembly Election 2026")
                .containsEntry("2026-10-19", "Ayudha Pooja")
                .containsEntry("2026-10-20", "Vijaya Dasami")
                .containsEntry("2026-11-08", "Deepavali")
                .containsEntry("2026-01-01", "New Year's Day")
                .containsEntry("2026-12-25", "Christmas")
                .doesNotContainKey("2026-02-01")      // Thai Poosam: government list only
                .doesNotContainKey("2026-11-24");     // Guru Nanak's Birthday: central list only

        // Government lists stay stored for provenance and are still readable by scope.
        assertThat(data(getAs(hod, "/holidays?year=2026&scope=NATIONAL"))).hasSize(14);
        assertThat(data(getAs(hod, "/holidays?year=2026&scope=TAMIL_NADU"))).hasSize(23);
    }

    @Test
    void resyncIsIdempotentAndNeverTouchesCollegeRows() throws Exception {
        sync2026();
        Set<Long> collegeIds = new HashSet<>();
        holidayRepository.findByYearAndScope(2026, Holiday.Scope.COLLEGE).forEach(h -> collegeIds.add(h.getId()));
        assertThat(collegeIds).hasSize(24);

        JsonNode again = sync2026();
        assertThat(again.get("inserted").asInt()).isZero();
        assertThat(again.get("removed").asInt()).isZero();
        assertThat(again.get("sources")).hasSize(2);
        // The first import created the 2026 calendar as PUBLISHED; from then on the HOD owns it and the file is skipped.
        assertThat(again.get("sources")).anySatisfy(s -> {
            assertThat(s.get("source").asText()).isEqualTo("college-calendar");
            assertThat(s.get("status").asText()).isEqualTo("SKIPPED");
        });
        assertThat(data(getAs(staff, "/holidays/calendars/2026")).get("status").asText()).isEqualTo("PUBLISHED");
        Set<Long> after = new HashSet<>();
        holidayRepository.findByYearAndScope(2026, Holiday.Scope.COLLEGE).forEach(h -> after.add(h.getId()));
        assertThat(after).isEqualTo(collegeIds);
    }

    @Test
    void collegeClosuresAreHolidaysForTimetableAndAttendance() throws Exception {
        sync2026();
        // 2026-01-23 is a Friday, 2026-04-23 a Thursday.
        JsonNode project = data(getAs(staff, "/attendance/day?date=2026-01-23").andExpect(status().isOk()));
        assertThat(project.get("holiday").asBoolean()).isTrue();
        assertThat(project.get("holidayName").asText()).isEqualTo("Project Day - Classes Suspended");
        assertThat(project.get("holidayCategory").asText()).isEqualTo("GENERAL_HOLIDAY");
        JsonNode election = data(getAs(staff, "/attendance/day?date=2026-04-23"));
        assertThat(election.get("holidayName").asText()).isEqualTo("Legislative Assembly Election 2026");
        assertThat(data(getAs(staff, "/attendance/day?date=2026-04-22")).get("holiday").asBoolean()).isFalse();
    }
}

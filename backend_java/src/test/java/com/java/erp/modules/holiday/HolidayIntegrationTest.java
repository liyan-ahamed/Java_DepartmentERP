package com.java.erp.modules.holiday;

import com.fasterxml.jackson.databind.JsonNode;
import com.java.erp.modules.auth.entity.User;
import com.java.erp.modules.holiday.entity.Holiday;
import com.java.erp.modules.holiday.repository.HolidayRepository;
import com.java.erp.modules.holiday.source.OfficialHolidayDatasetSource;
import com.java.erp.modules.holiday.source.HolidaySource.HolidayRecord;
import com.java.erp.modules.holiday.source.HolidaySource.HolidaySourceUnavailableException;
import com.java.erp.support.ErpModulesTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Holiday cache, sync and API. The source is mocked: the suite never needs the internet. */
class HolidayIntegrationTest extends ErpModulesTestBase {

    private static final int YEAR = 2031;

    /** The government-list source is mocked; the real college-calendar source has no file for 2031 (NO_DATA). */
    @MockBean private OfficialHolidayDatasetSource source;
    @Autowired private HolidayRepository holidayRepository;

    private static HolidayRecord national(String date, String name) {
        return new HolidayRecord(LocalDate.parse(date), name, Holiday.Scope.NATIONAL, "Central O.M.");
    }

    private static HolidayRecord tamilNadu(String date, String name) {
        return new HolidayRecord(LocalDate.parse(date), name, Holiday.Scope.TAMIL_NADU, "TN G.O.");
    }

    private static final List<HolidayRecord> OFFICIAL = List.of(
            national("2031-01-26", "Republic Day"),
            national("2031-08-15", "Independence Day"),
            tamilNadu("2031-01-15", "Pongal"),
            tamilNadu("2031-01-26", "Republic Day"),
            tamilNadu("2031-04-14", "Tamil New Year's Day"));

    @BeforeEach
    void officialSource() {
        when(source.name()).thenReturn("test-source");
        when(source.scopes()).thenReturn(EnumSet.of(Holiday.Scope.NATIONAL, Holiday.Scope.TAMIL_NADU));
        when(source.fetch(YEAR)).thenReturn(Optional.of(OFFICIAL));
    }

    private JsonNode sync() throws Exception {
        return data(postAs(hod, "/holidays/sync?year=" + YEAR, null).andExpect(status().isOk()));
    }

    @Test
    void syncCachesHolidaysByScopeAndIsIdempotent() throws Exception {
        JsonNode first = sync();
        assertThat(first.get("status").asText()).isEqualTo("SYNCED");
        assertThat(first.get("inserted").asInt()).isEqualTo(5);

        JsonNode second = sync();
        assertThat(second.get("inserted").asInt()).isZero();
        assertThat(second.get("updated").asInt()).isZero();
        assertThat(second.get("unchanged").asInt()).isEqualTo(5);
        assertThat(holidayRepository.findByYearAndScope(YEAR, Holiday.Scope.NATIONAL)).hasSize(2);
        assertThat(holidayRepository.findByYearAndScope(YEAR, Holiday.Scope.TAMIL_NADU)).hasSize(3);

        // Government lists are reference only: never in the default (college) list, only with an explicit scope.
        assertThat(data(getAs(hod, "/holidays?year=" + YEAR).andExpect(status().isOk()))).isEmpty();
        JsonNode tn = data(getAs(hod, "/holidays?year=" + YEAR + "&scope=TAMIL_NADU").andExpect(status().isOk()));
        assertThat(tn).hasSize(3);
        assertThat(tn).allSatisfy(h -> assertThat(h.get("category").asText()).isEqualTo("PUBLIC_HOLIDAY"));
        assertThat(tn.get(0).get("date").asText()).isEqualTo("2031-01-15");
        assertThat(tn.get(0).get("scope").asText()).isEqualTo("TAMIL_NADU");
        assertThat(tn.get(0).get("year").asInt()).isEqualTo(YEAR);
        assertThat(tn.get(0).get("source").asText()).isEqualTo("TN G.O.");
        assertThat(tn.get(0).get("calendarStatus").isNull()).isTrue();

        getAs(hod, "/holidays?year=" + YEAR + "&scope=NATIONAL")
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].name").value("Republic Day"))
                .andExpect(jsonPath("$.data[0].scope").value("NATIONAL"));
        getAs(hod, "/holidays?scope=TAMIL_NADU&dateFrom=2031-01-20&dateTo=2031-12-31")
                .andExpect(jsonPath("$.data.length()").value(2));
        getAs(hod, "/holidays?dateFrom=2031-02-01&dateTo=2031-01-01").andExpect(status().isBadRequest());
    }

    @Test
    void changedDetailsAreUpdatedInPlaceAndDroppedHolidaysRemovedOnlyAfterASuccessfulRead() throws Exception {
        sync();
        Long pongalId = holidayRepository.findByYearAndScope(YEAR, Holiday.Scope.TAMIL_NADU).stream()
                .filter(h -> h.getName().equals("Pongal")).findFirst().orElseThrow().getId();
        when(source.fetch(YEAR)).thenReturn(Optional.of(List.of(
                national("2031-01-26", "Republic Day"),
                national("2031-08-15", "Independence Day"),
                tamilNadu("2031-01-14", "Pongal"),                 // date moved
                tamilNadu("2031-01-26", "Republic Day"),
                tamilNadu("2031-04-14", "Tamil New Year"))));      // renamed

        JsonNode result = sync();
        assertThat(result.get("inserted").asInt()).isZero();
        assertThat(result.get("updated").asInt()).isEqualTo(2);
        assertThat(result.get("removed").asInt()).isZero();
        Holiday pongal = holidayRepository.findById(pongalId).orElseThrow();
        assertThat(pongal.getHolidayDate()).isEqualTo(LocalDate.of(2031, 1, 14));
        assertThat(holidayRepository.findByYearAndScope(YEAR, Holiday.Scope.TAMIL_NADU))
                .extracting(Holiday::getName).containsExactlyInAnyOrder("Pongal", "Republic Day", "Tamil New Year");

        when(source.fetch(YEAR)).thenReturn(Optional.of(List.of(national("2031-01-26", "Republic Day"))));
        JsonNode trimmed = sync();
        assertThat(trimmed.get("removed").asInt()).isEqualTo(1);
        // TAMIL_NADU was not part of that read, so its cached rows stay.
        assertThat(holidayRepository.findByYearAndScope(YEAR, Holiday.Scope.TAMIL_NADU)).hasSize(3);
    }

    @Test
    void cachedHolidaysAreKeptWhenTheSourceIsUnavailableOrHasNoData() throws Exception {
        sync();
        doThrow(new HolidaySourceUnavailableException("down", null)).when(source).fetch(YEAR);
        assertThat(sync().get("status").asText()).isEqualTo("SOURCE_UNAVAILABLE");
        getAs(staff, "/holidays?year=" + YEAR + "&scope=TAMIL_NADU").andExpect(jsonPath("$.data.length()").value(3));

        doReturn(Optional.empty()).when(source).fetch(YEAR);
        assertThat(sync().get("status").asText()).isEqualTo("NO_DATA");
        doReturn(Optional.of(List.of())).when(source).fetch(YEAR);
        assertThat(sync().get("status").asText()).isEqualTo("NO_DATA");
        assertThat(holidayRepository.findByYearAndScope(YEAR, Holiday.Scope.NATIONAL)).hasSize(2);
        assertThat(holidayRepository.findByYearAndScope(YEAR, Holiday.Scope.TAMIL_NADU)).hasSize(3);
    }

    @Test
    void theDatabaseRejectsDuplicateHolidays() {
        LocalDateTime now = LocalDateTime.now();
        holidayRepository.saveAndFlush(new Holiday(LocalDate.of(YEAR, 5, 1), "May Day", Holiday.Scope.TAMIL_NADU, "x", now));
        holidayRepository.saveAndFlush(new Holiday(LocalDate.of(YEAR, 5, 1), "May Day", Holiday.Scope.NATIONAL, "x", now));
        assertThatThrownBy(() -> holidayRepository.saveAndFlush(
                new Holiday(LocalDate.of(YEAR, 5, 1), "May Day", Holiday.Scope.TAMIL_NADU, "y", now)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void everyRoleReadsButOnlyHodSyncs() throws Exception {
        sync();
        StudentLogin student = studentLogin(section);
        for (User user : List.of(hod, staff, student.user())) {
            getAs(user, "/holidays?year=" + YEAR + "&scope=NATIONAL").andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(2));
        }
        postAs(student.user(), "/holidays/sync?year=" + YEAR, null).andExpect(status().isForbidden());
        postAs(staff, "/holidays/sync?year=" + YEAR, null).andExpect(status().isForbidden());
        mvc.perform(get("/holidays")).andExpect(status().isUnauthorized());
        mvc.perform(post("/holidays/sync?year=" + YEAR)).andExpect(status().isUnauthorized());
        postAs(hod, "/holidays/sync?year=1800", null).andExpect(status().isBadRequest());
    }
}

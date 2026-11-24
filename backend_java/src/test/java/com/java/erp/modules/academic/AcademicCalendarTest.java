package com.java.erp.modules.academic;

import com.java.erp.modules.academic.service.AcademicCalendar;
import com.java.erp.modules.academic.service.AcademicCalendar.TermStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Term calendar: Year 1 Sep-Dec / Jan-Apr; Years 2-4 Jun-Nov / Jan-Apr. */
class AcademicCalendarTest {

    @Test
    void termDates() {
        assertThat(AcademicCalendar.term(2026, 1).startDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(AcademicCalendar.term(2026, 1).endDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(AcademicCalendar.term(2026, 2).startDate()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(AcademicCalendar.term(2026, 2).endDate()).isEqualTo(LocalDate.of(2027, 4, 30));
        assertThat(AcademicCalendar.term(2025, 3).startDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(AcademicCalendar.term(2025, 3).endDate()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(AcademicCalendar.term(2023, 8).endDate()).isEqualTo(LocalDate.of(2027, 4, 30));
    }

    @Test
    void currentSemesterOfEachCohortOnOneDate() {
        LocalDate today = LocalDate.of(2026, 10, 21);
        assertThat(AcademicCalendar.position(2026, today).currentSemester()).isEqualTo(1);
        assertThat(AcademicCalendar.position(2025, today).currentSemester()).isEqualTo(3);
        assertThat(AcademicCalendar.position(2024, today).currentSemester()).isEqualTo(5);
        assertThat(AcademicCalendar.position(2023, today).currentSemester()).isEqualTo(7);
        assertThat(AcademicCalendar.position(2023, today).yearOfStudy()).isEqualTo(4);
        assertThat(AcademicCalendar.position(2025, today).status()).isEqualTo(TermStatus.IN_SESSION);
        assertThat(AcademicCalendar.academicYear(2026, 1)).isEqualTo("2026-2027");
        assertThat(AcademicCalendar.academicYear(2026, 2)).isEqualTo("2026-2027");
        assertThat(AcademicCalendar.academicYear(2025, 3)).isEqualTo("2026-2027");
        assertThat(AcademicCalendar.academicYear(2023, 8)).isEqualTo("2026-2027");
    }

    @Test
    void gapsBeforeStartAndAfterTheLastSemester() {
        // December for Year 2: Semester 3 ended, Semester 4 not started -> still 3, between terms.
        var december = AcademicCalendar.position(2025, LocalDate.of(2026, 12, 10));
        assertThat(december.currentSemester()).isEqualTo(3);
        assertThat(december.status()).isEqualTo(TermStatus.BETWEEN_TERMS);
        // January: Semester 4.
        assertThat(AcademicCalendar.position(2025, LocalDate.of(2027, 1, 1)).currentSemester()).isEqualTo(4);
        // May after Year 1: Semester 2 until Semester 3 starts in June.
        assertThat(AcademicCalendar.position(2026, LocalDate.of(2027, 5, 15)).currentSemester()).isEqualTo(2);
        assertThat(AcademicCalendar.position(2026, LocalDate.of(2027, 6, 1)).currentSemester()).isEqualTo(3);
        // First years before September of their admission year.
        var before = AcademicCalendar.position(2027, LocalDate.of(2027, 7, 1));
        assertThat(before.status()).isEqualTo(TermStatus.NOT_STARTED);
        assertThat(before.currentSemester()).isNull();
        // After Semester 8 ends.
        var done = AcademicCalendar.position(2022, LocalDate.of(2026, 5, 1));
        assertThat(done.status()).isEqualTo(TermStatus.COMPLETED);
        assertThat(done.currentSemester()).isNull();
    }
}

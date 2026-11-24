package com.java.erp.modules.academic.service;

import java.time.LocalDate;
import java.time.Month;

/**
 * The institution's term calendar (user-defined rule, the single definition used by the backend):
 *  - Year 1: Semester 1 runs September-December (admission year), Semester 2 January-April.
 *  - Years 2-4: odd semesters run June-November, even semesters January-April (of the following calendar year).
 * A cohort's position follows from its admission year and the date: the current semester is the latest one that
 * has started. Between terms (December for Years 2-4, May, June-August before Year 1 starts) the last started
 * semester stays current with status BETWEEN_TERMS. Before Semester 1 starts: NOT_STARTED; after Semester 8 ends:
 * COMPLETED (both without a current semester).
 */
public final class AcademicCalendar {

    public static final int MAX_SEMESTER = 8;

    public enum TermStatus { NOT_STARTED, IN_SESSION, BETWEEN_TERMS, COMPLETED }

    public record Term(int semester, LocalDate startDate, LocalDate endDate) {
        public boolean contains(LocalDate date) {
            return !date.isBefore(startDate) && !date.isAfter(endDate);
        }
    }

    /** currentSemester and yearOfStudy are null when status is NOT_STARTED or COMPLETED. */
    public record Position(Integer currentSemester, Integer yearOfStudy, TermStatus status, Term currentTerm) {
    }

    private AcademicCalendar() {
    }

    /** Dates of one semester (1-8) of a cohort admitted in admissionYear. */
    public static Term term(int admissionYear, int semester) {
        if (semester < 1 || semester > MAX_SEMESTER) {
            throw new IllegalArgumentException("semester must be 1-" + MAX_SEMESTER);
        }
        int yearOfStudy = (semester + 1) / 2;
        if (semester % 2 == 1) {
            int year = admissionYear + yearOfStudy - 1;
            return yearOfStudy == 1
                    ? new Term(semester, LocalDate.of(year, Month.SEPTEMBER, 1), LocalDate.of(year, Month.DECEMBER, 31))
                    : new Term(semester, LocalDate.of(year, Month.JUNE, 1), LocalDate.of(year, Month.NOVEMBER, 30));
        }
        int year = admissionYear + yearOfStudy;
        return new Term(semester, LocalDate.of(year, Month.JANUARY, 1), LocalDate.of(year, Month.APRIL, 30));
    }

    /** Academic year label ("2026-2027") of a semester: the odd semester's calendar year and the next one. */
    public static String academicYear(int admissionYear, int semester) {
        int start = admissionYear + (semester + 1) / 2 - 1;
        return start + "-" + (start + 1);
    }

    public static Position position(int admissionYear, LocalDate today) {
        for (int semester = MAX_SEMESTER; semester >= 1; semester--) {
            Term term = term(admissionYear, semester);
            if (term.startDate().isAfter(today)) {
                continue;
            }
            if (semester == MAX_SEMESTER && today.isAfter(term.endDate())) {
                return new Position(null, null, TermStatus.COMPLETED, null);
            }
            TermStatus status = term.contains(today) ? TermStatus.IN_SESSION : TermStatus.BETWEEN_TERMS;
            return new Position(semester, (semester + 1) / 2, status, term);
        }
        return new Position(null, null, TermStatus.NOT_STARTED, null);
    }
}

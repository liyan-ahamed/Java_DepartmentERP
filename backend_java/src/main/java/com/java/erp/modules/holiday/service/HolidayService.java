package com.java.erp.modules.holiday.service;

import com.java.erp.common.util.SecurityUtils;
import com.java.erp.exception.BadRequestException;
import com.java.erp.modules.holiday.dto.response.HolidayResponse;
import com.java.erp.modules.holiday.entity.Holiday;
import com.java.erp.modules.holiday.entity.HolidayCalendar;
import com.java.erp.modules.holiday.repository.HolidayCalendarRepository;
import com.java.erp.modules.holiday.repository.HolidayRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Reads holidays. Never calls a holiday source.
 *
 * Effective (institution) calendar = COLLEGE holidays of PUBLISHED calendar years only. It is what the timetable,
 * attendance and dashboards use; callers filter it by cohort with {@link HolidayResponse#appliesTo}. Draft college
 * holidays and the government lists (NATIONAL / TAMIL_NADU, reference only) never affect them.
 */
@Service
public class HolidayService {

    /** Longest date range one request may ask for. */
    private static final long MAX_RANGE_DAYS = 400;
    private static final Set<Holiday.Scope> COLLEGE = EnumSet.of(Holiday.Scope.COLLEGE);

    private final HolidayRepository holidays;
    private final HolidayCalendarRepository calendars;

    public HolidayService(HolidayRepository holidays, HolidayCalendarRepository calendars) {
        this.holidays = holidays;
        this.calendars = calendars;
    }

    /**
     * Without scope (or scope COLLEGE): college holidays; STAFF / STUDENT only see PUBLISHED years, the HOD also sees
     * drafts. scope NATIONAL / TAMIL_NADU: that government list (reference). With no year and no date range the current
     * calendar year is used. batchId keeps holidays that apply to that cohort.
     */
    @Transactional(readOnly = true)
    public List<HolidayResponse> list(Integer year, Holiday.Scope scope, LocalDate dateFrom, LocalDate dateTo, Long batchId) {
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new BadRequestException("dateFrom must not be after dateTo");
        }
        LocalDate from = dateFrom;
        LocalDate to = dateTo;
        if (year != null) {
            from = max(from, LocalDate.of(year, 1, 1));
            to = min(to, LocalDate.of(year, 12, 31));
        } else if (from == null && to == null) {
            int current = LocalDate.now().getYear();
            from = LocalDate.of(current, 1, 1);
            to = LocalDate.of(current, 12, 31);
        } else if (from == null) {
            from = to.minusDays(MAX_RANGE_DAYS);
        } else if (to == null) {
            to = from.plusDays(MAX_RANGE_DAYS);
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new BadRequestException("The date range may cover at most " + MAX_RANGE_DAYS + " days");
        }
        if (from.isAfter(to)) {
            return List.of();
        }
        if (scope != null && scope != Holiday.Scope.COLLEGE) {
            return holidays.findOverlapping(EnumSet.of(scope), from, to).stream()
                    .map(h -> HolidayResponse.from(h, null))
                    .filter(h -> batchId == null || h.appliesTo(batchId))
                    .toList();
        }
        Map<Integer, HolidayCalendar.Status> statuses = calendarStatuses(from.getYear(), to.getYear());
        boolean hod = SecurityUtils.hasAuthority("ROLE_HOD");
        return holidays.findOverlapping(COLLEGE, from, to).stream()
                .map(h -> HolidayResponse.from(h, statuses.get(h.getYear())))
                .filter(h -> hod || h.calendarStatus() == HolidayCalendar.Status.PUBLISHED)
                .filter(h -> batchId == null || h.appliesTo(batchId))
                .toList();
    }

    /**
     * The effective calendar between two dates (inclusive), one entry per covered date: a multi-day holiday is listed
     * on each of its days. Not filtered by cohort; callers use {@link HolidayResponse#appliesTo}.
     */
    @Transactional(readOnly = true)
    public Map<LocalDate, List<HolidayResponse>> byDate(LocalDate from, LocalDate to) {
        Map<LocalDate, List<HolidayResponse>> result = new TreeMap<>();
        Set<Integer> published = new HashSet<>(calendars.findPublishedYears(from.getYear(), to.getYear()));
        if (published.isEmpty()) {
            return result;
        }
        for (Holiday h : holidays.findOverlapping(COLLEGE, from, to)) {
            if (!published.contains(h.getYear())) continue;
            HolidayResponse response = HolidayResponse.from(h, HolidayCalendar.Status.PUBLISHED);
            LocalDate first = max(from, h.getHolidayDate());
            LocalDate last = min(to, h.getLastDate());
            for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
                result.computeIfAbsent(d, k -> new ArrayList<>()).add(response);
            }
        }
        return result;
    }

    /** Effective holidays of one date that apply to the cohort (null batchId = institution-wide holidays only). */
    public static List<HolidayResponse> applicable(List<HolidayResponse> sameDay, Long batchId) {
        if (sameDay == null || sameDay.isEmpty()) return List.of();
        return sameDay.stream().filter(h -> batchId == null ? h.batchIds().isEmpty() : h.appliesTo(batchId)).toList();
    }

    /** Names of several holidays joined for display ("A / B"). */
    public static String joinedNames(List<HolidayResponse> sameDay) {
        return sameDay.isEmpty() ? null
                : sameDay.stream().map(HolidayResponse::name).distinct().collect(Collectors.joining(" / "));
    }

    private Map<Integer, HolidayCalendar.Status> calendarStatuses(int fromYear, int toYear) {
        List<Integer> years = new ArrayList<>();
        for (int y = fromYear; y <= toYear; y++) years.add(y);
        Map<Integer, HolidayCalendar.Status> result = new HashMap<>();
        calendars.findByYearIn(years).forEach(c -> result.put(c.getYear(), c.getStatus()));
        return result;
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a == null || b.isAfter(a) ? b : a;
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a == null || b.isBefore(a) ? b : a;
    }
}

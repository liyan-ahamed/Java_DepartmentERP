package com.java.erp.modules.holiday.service;

import com.java.erp.common.util.SecurityUtils;
import com.java.erp.exception.BadRequestException;
import com.java.erp.exception.ConflictException;
import com.java.erp.exception.ResourceNotFoundException;
import com.java.erp.modules.audit.service.AuditRecorder;
import com.java.erp.modules.auth.repository.UserRepository;
import com.java.erp.modules.holiday.dto.request.HolidayRequest;
import com.java.erp.modules.holiday.dto.response.HolidayCalendarResponse;
import com.java.erp.modules.holiday.dto.response.HolidayResponse;
import com.java.erp.modules.holiday.entity.Holiday;
import com.java.erp.modules.holiday.entity.HolidayCalendar;
import com.java.erp.modules.holiday.repository.HolidayCalendarRepository;
import com.java.erp.modules.holiday.repository.HolidayRepository;
import com.java.erp.modules.system.entity.Batch;
import com.java.erp.modules.system.repository.BatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * HOD management of the college holiday calendar: holidays (create / update / delete) and the publication state of
 * each calendar year. Creating a holiday in a year without a calendar creates that year as DRAFT. A PUBLISHED year
 * may be edited directly; the change is visible at once. Government reference rows are read-only.
 */
@Service
public class HolidayCalendarService {

    /** Longest holiday, inclusive of both ends. */
    public static final int MAX_HOLIDAY_DAYS = 60;

    private final HolidayRepository holidays;
    private final HolidayCalendarRepository calendars;
    private final BatchRepository batches;
    private final UserRepository users;
    private final AuditRecorder audit;

    public HolidayCalendarService(HolidayRepository holidays, HolidayCalendarRepository calendars, BatchRepository batches,
                                  UserRepository users, AuditRecorder audit) {
        this.holidays = holidays;
        this.calendars = calendars;
        this.batches = batches;
        this.users = users;
        this.audit = audit;
    }

    /** STAFF / STUDENT never learn whether a draft exists: NOT_CREATED and DRAFT are both NOT_PUBLISHED for them. */
    @Transactional(readOnly = true)
    public HolidayCalendarResponse calendar(int year) {
        requireYear(year);
        Optional<HolidayCalendar> calendar = calendars.findByYear(year);
        boolean published = calendar.map(HolidayCalendar::isPublished).orElse(false);
        if (!published && !SecurityUtils.hasAuthority("ROLE_HOD")) {
            return new HolidayCalendarResponse(year, HolidayCalendarResponse.NOT_PUBLISHED, null, null, null);
        }
        long count = holidays.countByYearAndScope(year, Holiday.Scope.COLLEGE);
        return calendar.map(c -> new HolidayCalendarResponse(year, c.getStatus().name(), c.getPublishedAt(),
                        c.getPublishedBy() == null ? null : c.getPublishedBy().getName(), count))
                .orElseGet(() -> new HolidayCalendarResponse(year, HolidayCalendarResponse.NOT_CREATED, null, null, count));
    }

    /** NOT_CREATED / DRAFT -> PUBLISHED. An empty calendar may be published. */
    @Transactional
    public HolidayCalendarResponse publish(int year) {
        requireYear(year);
        HolidayCalendar calendar = calendars.findByYear(year).orElseGet(() -> new HolidayCalendar(year));
        if (calendar.isPublished()) {
            throw new ConflictException("The " + year + " holiday calendar is already published");
        }
        calendar.publish(users.getReferenceById(SecurityUtils.getCurrentUserId()), LocalDateTime.now());
        calendars.saveAndFlush(calendar);
        audit.record("CALENDAR_PUBLISHED", "HOLIDAY", "HolidayCalendar", calendar.getId(),
                Map.of("year", year, "holidayCount", holidays.countByYearAndScope(year, Holiday.Scope.COLLEGE)));
        return calendar(year);
    }

    /** PUBLISHED -> DRAFT. */
    @Transactional
    public HolidayCalendarResponse unpublish(int year) {
        requireYear(year);
        HolidayCalendar calendar = calendars.findByYear(year).filter(HolidayCalendar::isPublished)
                .orElseThrow(() -> new ConflictException("The " + year + " holiday calendar is not published"));
        calendar.unpublish();
        calendars.saveAndFlush(calendar);
        audit.record("CALENDAR_UNPUBLISHED", "HOLIDAY", "HolidayCalendar", calendar.getId(), Map.of("year", year));
        return calendar(year);
    }

    @Transactional
    public HolidayResponse create(HolidayRequest request) {
        Valid valid = validate(request, 0L);
        HolidayCalendar calendar = calendarFor(valid.start().getYear());
        Holiday holiday = Holiday.college(valid.start(), valid.name(), request.category());
        apply(holiday, valid);
        holidays.saveAndFlush(holiday);
        audit.record("HOLIDAY_CREATED", "HOLIDAY", "Holiday", holiday.getId(), summary(holiday));
        return HolidayResponse.from(holiday, calendar.getStatus());
    }

    @Transactional
    public HolidayResponse update(Long id, HolidayRequest request) {
        Holiday holiday = editable(id);
        Valid valid = validate(request, id);
        HolidayCalendar calendar = calendarFor(valid.start().getYear());
        holiday.setHolidayDate(valid.start());
        holiday.setName(valid.name());
        holiday.setCategory(request.category());
        apply(holiday, valid);
        holidays.saveAndFlush(holiday);
        audit.record("HOLIDAY_UPDATED", "HOLIDAY", "Holiday", holiday.getId(), summary(holiday));
        return HolidayResponse.from(holiday, calendar.getStatus());
    }

    @Transactional
    public void delete(Long id) {
        Holiday holiday = editable(id);
        Map<String, Object> summary = summary(holiday);
        holidays.delete(holiday);
        holidays.flush();
        audit.record("HOLIDAY_DELETED", "HOLIDAY", "Holiday", id, summary);
    }

    private record Valid(String name, LocalDate start, LocalDate end, String description, List<Long> batchIds) {
    }

    /** Every problem is reported at once (400); a duplicate name in the year is a conflict (409). */
    private Valid validate(HolidayRequest request, Long excludeId) {
        List<String> errors = new ArrayList<>();
        String name = request.name().trim();
        LocalDate start = request.startDate();
        LocalDate end = request.endDate() == null || request.endDate().equals(start) ? null : request.endDate();
        if (start.getYear() < HolidaySyncService.MIN_YEAR || start.getYear() > HolidaySyncService.MAX_YEAR) {
            errors.add("startDate: year must be between " + HolidaySyncService.MIN_YEAR + " and " + HolidaySyncService.MAX_YEAR);
        }
        if (end != null) {
            if (end.isBefore(start)) {
                errors.add("endDate: must not be before startDate");
            } else if (end.getYear() != start.getYear()) {
                errors.add("endDate: a holiday cannot cross into another calendar year; add one holiday per year");
            } else if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_HOLIDAY_DAYS) {
                errors.add("endDate: a holiday may cover at most " + MAX_HOLIDAY_DAYS + " days");
            }
        }
        if (request.category() == Holiday.Category.PUBLIC_HOLIDAY) {
            errors.add("category: PUBLIC_HOLIDAY is reserved for the government reference lists; use NATIONAL or STATE");
        }
        List<Long> batchIds = request.batchIds() == null ? List.of() : request.batchIds();
        if (batchIds.contains(null)) {
            errors.add("batchIds: must not contain null");
        } else {
            if (new HashSet<>(batchIds).size() != batchIds.size()) {
                errors.add("batchIds: must not contain duplicates");
            }
            Set<Long> found = new HashSet<>();
            batches.findAllById(batchIds).stream().map(Batch::getId).forEach(found::add);
            batchIds.stream().distinct().filter(b -> !found.contains(b))
                    .forEach(b -> errors.add("batchIds: cohort " + b + " does not exist"));
        }
        String description = request.description() == null || request.description().isBlank() ? null : request.description().trim();
        if (!errors.isEmpty()) {
            throw new BadRequestException("Invalid holiday", errors);
        }
        if (holidays.existsCollegeName(start.getYear(), name, excludeId)) {
            throw new ConflictException("A holiday named \"" + name + "\" already exists in the " + start.getYear() + " calendar");
        }
        return new Valid(name, start, end, description, batchIds.stream().distinct().toList());
    }

    private static void apply(Holiday holiday, Valid valid) {
        holiday.setEndDate(valid.end());
        holiday.setDescription(valid.description());
        holiday.getBatchIds().clear();
        holiday.getBatchIds().addAll(valid.batchIds());
    }

    private Holiday editable(Long id) {
        Holiday holiday = holidays.findById(id).orElseThrow(() -> new ResourceNotFoundException("Holiday", "id", id));
        if (holiday.getScope() != Holiday.Scope.COLLEGE) {
            throw new ConflictException("Government holiday lists are read-only; add the day to the college calendar instead");
        }
        return holiday;
    }

    /** The year's calendar, created as DRAFT when the year has none yet. */
    private HolidayCalendar calendarFor(int year) {
        return calendars.findByYear(year).orElseGet(() -> calendars.saveAndFlush(new HolidayCalendar(year)));
    }

    private static Map<String, Object> summary(Holiday h) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("holidayId", h.getId());
        summary.put("year", h.getYear());
        summary.put("name", h.getName());
        summary.put("startDate", h.getHolidayDate().toString());
        summary.put("endDate", h.getEndDate() == null ? null : h.getEndDate().toString());
        summary.put("category", h.getCategory().name());
        summary.put("batchIds", h.getBatchIds().stream().sorted().toList());
        return summary;
    }

    private static void requireYear(int year) {
        if (year < HolidaySyncService.MIN_YEAR || year > HolidaySyncService.MAX_YEAR) {
            throw new BadRequestException("Year must be between " + HolidaySyncService.MIN_YEAR + " and " + HolidaySyncService.MAX_YEAR);
        }
    }
}

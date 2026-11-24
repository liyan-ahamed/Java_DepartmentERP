package com.java.erp.modules.holiday.source;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.erp.modules.holiday.entity.Holiday;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.*;

/**
 * The college's own holiday calendar, one checked JSON file per year
 * ({@code <holidays.college-calendar-location><year>.json}, default {@code classpath:holidays/college/}).
 *
 * When a year has a college calendar it is the institution's effective calendar (see HolidayService);
 * it includes college-only closures such as "Project Day - Classes Suspended". Every row is scope COLLEGE,
 * category GENERAL_HOLIDAY. Only this source writes COLLEGE rows, so the government-list sync can never
 * overwrite or delete them.
 */
@Component
public class CollegeCalendarSource implements HolidaySource {

    record Calendar(Integer year, String reference, List<Entry> holidays) {
        record Entry(LocalDate date, String name) {
        }
    }

    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private final String location;

    public CollegeCalendarSource(ResourceLoader resourceLoader, ObjectMapper objectMapper,
                                 @Value("${holidays.college-calendar-location:classpath:holidays/college/}") String location) {
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
        this.location = location.endsWith("/") ? location : location + "/";
    }

    @Override
    public String name() {
        return "college-calendar";
    }

    @Override
    public Set<Holiday.Scope> scopes() {
        return EnumSet.of(Holiday.Scope.COLLEGE);
    }

    @Override
    public Optional<List<HolidayRecord>> fetch(int year) {
        Resource resource = resourceLoader.getResource(location + year + ".json");
        if (!resource.exists()) {
            return Optional.empty();
        }
        Calendar calendar;
        try (InputStream in = resource.getInputStream()) {
            calendar = objectMapper.readValue(in, Calendar.class);
        } catch (IOException e) {
            throw new HolidaySourceUnavailableException("College calendar for " + year + " cannot be read: " + e.getMessage(), e);
        }
        if (calendar.year() == null || calendar.year() != year || calendar.holidays() == null
                || calendar.reference() == null || calendar.reference().isBlank()) {
            throw invalid(year, "year, reference and holidays are required and the year must be " + year);
        }
        List<HolidayRecord> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Calendar.Entry e : calendar.holidays()) {
            if (e.date() == null || e.name() == null || e.name().isBlank()) {
                throw invalid(year, "every holiday needs a date and a name");
            }
            if (e.date().getYear() != year) {
                throw invalid(year, e.date() + " is not in " + year);
            }
            if (!seen.add(e.date() + "|" + e.name().trim())) {
                throw invalid(year, "duplicate holiday " + e.name().trim() + " on " + e.date());
            }
            result.add(new HolidayRecord(e.date(), e.name().trim(), Holiday.Scope.COLLEGE, calendar.reference().trim()));
        }
        return Optional.of(result);
    }

    private static HolidaySourceUnavailableException invalid(int year, String reason) {
        return new HolidaySourceUnavailableException("College calendar for " + year + " is invalid: " + reason, null);
    }
}

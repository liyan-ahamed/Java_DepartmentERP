package com.java.erp.modules.holiday.service;

import com.java.erp.exception.BadRequestException;
import com.java.erp.modules.audit.service.AuditRecorder;
import com.java.erp.modules.holiday.dto.response.HolidaySyncResponse;
import com.java.erp.modules.holiday.dto.response.HolidaySyncResponse.SourceResult;
import com.java.erp.modules.holiday.dto.response.HolidaySyncResponse.Status;
import com.java.erp.modules.holiday.entity.Holiday;
import com.java.erp.modules.holiday.entity.HolidayCalendar;
import com.java.erp.modules.holiday.repository.HolidayCalendarRepository;
import com.java.erp.modules.holiday.repository.HolidayRepository;
import com.java.erp.modules.holiday.source.HolidaySource;
import com.java.erp.modules.holiday.source.HolidaySource.HolidayRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Copies one year of holidays from every {@link HolidaySource} (college calendar, government lists) into
 * the holidays table. Each source only ever writes or removes rows of the scopes it owns, so the
 * government-list sync can never overwrite or delete the college calendar.
 *
 * Idempotent upsert, per scope:
 *   same date + name  -> kept (source / last_synced_at refreshed)
 *   same name, new date (e.g. moon sighting moved it) -> date updated
 *   same date, new name -> name updated
 *   otherwise -> inserted
 * Cached rows of a scope that the source no longer lists are removed, but only after a successful
 * read that returned that scope. If a source has no data or cannot be read, its rows do not change.
 *
 * College calendar: a year's file is imported only while that year has no {@link HolidayCalendar}; the import
 * creates the calendar as PUBLISHED (the file is the college's published calendar). From then on the HOD owns the
 * year and the file is ignored (SKIPPED), so a sync can never overwrite or remove the HOD's edits.
 */
@Service
public class HolidaySyncService {

    private static final Logger logger = LoggerFactory.getLogger(HolidaySyncService.class);
    /** Advisory-lock key for holiday syncs ("HD" in ASCII). */
    private static final long LOCK_KEY = 0x4844L;
    public static final int MIN_YEAR = 2000;
    public static final int MAX_YEAR = 2100;

    private final HolidayRepository holidays;
    private final HolidayCalendarRepository calendars;
    private final List<HolidaySource> sources;
    private final AuditRecorder audit;

    public HolidaySyncService(HolidayRepository holidays, HolidayCalendarRepository calendars, List<HolidaySource> sources,
                              AuditRecorder audit) {
        this.holidays = holidays;
        this.calendars = calendars;
        this.sources = sources;
        this.audit = audit;
    }

    @Transactional
    public HolidaySyncResponse sync(int year) {
        if (year < MIN_YEAR || year > MAX_YEAR) {
            throw new BadRequestException("Year must be between " + MIN_YEAR + " and " + MAX_YEAR);
        }
        holidays.lockHolidays(LOCK_KEY);
        List<SourceResult> results = new ArrayList<>();
        for (HolidaySource source : sources) {
            results.add(syncSource(source, year));
        }

        int inserted = results.stream().mapToInt(SourceResult::inserted).sum();
        int updated = results.stream().mapToInt(SourceResult::updated).sum();
        int unchanged = results.stream().mapToInt(SourceResult::unchanged).sum();
        int removed = results.stream().mapToInt(SourceResult::removed).sum();
        Status status = results.stream().anyMatch(r -> r.status() == Status.SOURCE_UNAVAILABLE) ? Status.SOURCE_UNAVAILABLE
                : results.stream().anyMatch(r -> r.status() == Status.SYNCED) ? Status.SYNCED : Status.NO_DATA;
        String message = switch (status) {
            case SYNCED -> "Holidays synced";
            // SKIPPED is per source only (never the overall status).
            case NO_DATA, SKIPPED -> "No holiday source has data for " + year + " yet; cached holidays were kept";
            case SOURCE_UNAVAILABLE -> "A holiday source was unavailable; its cached holidays were kept";
        };
        String names = sources.stream().map(HolidaySource::name).collect(Collectors.joining("+"));
        return new HolidaySyncResponse(year, names, status, inserted, updated, unchanged, removed, message, results);
    }

    private SourceResult syncSource(HolidaySource source, int year) {
        boolean college = source.scopes().contains(Holiday.Scope.COLLEGE);
        if (college && calendars.existsByYear(year)) {
            return new SourceResult(source.name(), Status.SKIPPED, 0, 0, 0, 0,
                    "The " + year + " college calendar is managed in the ERP; the file was ignored");
        }
        Optional<List<HolidayRecord>> fetched;
        try {
            fetched = source.fetch(year);
        } catch (RuntimeException e) {
            logger.warn("Holiday sync {} from {} failed, cached holidays kept: {}", year, source.name(), e.getMessage());
            return new SourceResult(source.name(), Status.SOURCE_UNAVAILABLE, 0, 0, 0, 0, "Source unavailable; cached holidays kept");
        }
        if (fetched.isEmpty() || fetched.get().isEmpty()) {
            return new SourceResult(source.name(), Status.NO_DATA, 0, 0, 0, 0, "No data for " + year + "; cached holidays kept");
        }
        Set<Holiday.Scope> owned = source.scopes();
        if (fetched.get().stream().anyMatch(r -> !owned.contains(r.scope()))) {
            logger.warn("Holiday sync {} from {} rejected: it returned holidays outside its scopes {}", year, source.name(), owned);
            return new SourceResult(source.name(), Status.SOURCE_UNAVAILABLE, 0, 0, 0, 0,
                    "Source returned holidays outside its scopes; cached holidays kept");
        }

        LocalDateTime now = LocalDateTime.now();
        // One record per (date, scope, name); a source listing the same holiday twice must not insert it twice.
        Map<Holiday.Scope, Map<String, HolidayRecord>> byScope = new EnumMap<>(Holiday.Scope.class);
        fetched.get().forEach(r -> byScope.computeIfAbsent(r.scope(), s -> new LinkedHashMap<>())
                .putIfAbsent(r.date() + "|" + r.name(), r));

        int inserted = 0, updated = 0, unchanged = 0, removed = 0;
        for (Map.Entry<Holiday.Scope, Map<String, HolidayRecord>> entry : byScope.entrySet()) {
            List<Holiday> cached = new ArrayList<>(holidays.findByYearAndScope(year, entry.getKey()));
            // Pass 1: exact matches, so pass 2 can never move a row onto a key another record still needs.
            List<HolidayRecord> unmatched = new ArrayList<>();
            for (HolidayRecord record : entry.getValue().values()) {
                Holiday match = take(cached, h -> h.getHolidayDate().equals(record.date()) && h.getName().equals(record.name()));
                if (match == null) {
                    unmatched.add(record);
                    continue;
                }
                boolean changed = !match.getSource().equals(record.reference());
                match.setSource(record.reference());
                match.setLastSyncedAt(now);
                if (changed) updated++; else unchanged++;
            }
            // Pass 2: moved dates, renamed holidays, new holidays.
            for (HolidayRecord record : unmatched) {
                Holiday match = take(cached, h -> h.getName().equals(record.name()));
                if (match == null) {
                    match = take(cached, h -> h.getHolidayDate().equals(record.date()));
                }
                if (match != null) {
                    match.setHolidayDate(record.date());
                    match.setName(record.name());
                    match.setSource(record.reference());
                    match.setLastSyncedAt(now);
                    updated++;
                } else {
                    holidays.save(new Holiday(record.date(), record.name(), record.scope(), record.reference(), now));
                    inserted++;
                }
            }
            // Whatever is left was not listed by this successful read of the scope.
            holidays.deleteAll(cached);
            removed += cached.size();
        }
        holidays.flush();
        if (college) {
            HolidayCalendar calendar = new HolidayCalendar(year);
            calendar.publish(null, now);
            calendars.saveAndFlush(calendar);
        }

        if (inserted + updated + removed > 0) {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("year", year);
            summary.put("source", source.name());
            summary.put("inserted", inserted);
            summary.put("updated", updated);
            summary.put("unchanged", unchanged);
            summary.put("removed", removed);
            audit.record("SYNC", "HOLIDAY", "Holiday", null, summary);
        }
        logger.info("Holiday sync {} from {}: {} inserted, {} updated, {} unchanged, {} removed",
                year, source.name(), inserted, updated, unchanged, removed);
        return new SourceResult(source.name(), Status.SYNCED, inserted, updated, unchanged, removed, "Synced");
    }

    /** Removes and returns the first cached row matching the condition, or null. */
    private static Holiday take(List<Holiday> cached, java.util.function.Predicate<Holiday> condition) {
        for (Iterator<Holiday> it = cached.iterator(); it.hasNext(); ) {
            Holiday h = it.next();
            if (condition.test(h)) {
                it.remove();
                return h;
            }
        }
        return null;
    }
}

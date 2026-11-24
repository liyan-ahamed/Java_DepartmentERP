package com.java.erp.modules.calendar.service;

import com.java.erp.common.util.SecurityUtils;
import com.java.erp.exception.BadRequestException;
import com.java.erp.exception.UnauthorizedException;
import com.java.erp.modules.auth.repository.UserRepository;
import com.java.erp.modules.calendar.dto.*;
import com.java.erp.modules.calendar.entity.CalendarEntry;
import com.java.erp.modules.calendar.repository.CalendarEntryRepository;
import com.java.erp.modules.holiday.entity.Holiday;
import com.java.erp.modules.holiday.repository.HolidayRepository;
import com.java.erp.modules.notification.entity.Notification;
import com.java.erp.modules.notification.service.NotificationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class CalendarService {
    private final CalendarEntryRepository entries;
    private final HolidayRepository holidays;
    private final UserRepository users;
    private final NotificationService notifications;

    public CalendarService(CalendarEntryRepository entries, HolidayRepository holidays,
                           UserRepository users, NotificationService notifications) {
        this.entries = entries; this.holidays = holidays; this.users = users; this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public CalendarResponse list(LocalDate from, LocalDate to) {
        if (from.isAfter(to) || ChronoUnit.DAYS.between(from, to) > 366) {
            throw new BadRequestException("Choose an ordered date range of at most 366 days");
        }
        List<CalendarItemResponse> result = new ArrayList<>();
        entries.findByEventDateBetweenOrderByEventDateAscIdAsc(from, to).forEach(e -> result.add(CalendarItemResponse.from(e)));
        // The same government holiday may occur in both national and state lists.
        Map<String, CalendarItemResponse> government = new LinkedHashMap<>();
        holidays.findOverlapping(EnumSet.of(Holiday.Scope.NATIONAL, Holiday.Scope.TAMIL_NADU), from, to)
                .forEach(h -> government.putIfAbsent(h.getHolidayDate() + ":" + h.getLastDate() + ":"
                        + h.getName().toLowerCase(Locale.ROOT), CalendarItemResponse.from(h)));
        result.addAll(government.values());
        result.sort(Comparator.comparing(CalendarItemResponse::startDate).thenComparing(CalendarItemResponse::title)
                .thenComparing(CalendarItemResponse::id));
        List<Integer> available = new ArrayList<>();
        for (int year = from.getYear(); year <= to.getYear(); year++) {
            if (holidays.existsByYearAndScope(year, Holiday.Scope.NATIONAL)
                    || holidays.existsByYearAndScope(year, Holiday.Scope.TAMIL_NADU)) available.add(year);
        }
        return new CalendarResponse(result, available);
    }

    @Transactional
    @PreAuthorize("hasRole('STAFF')")
    public CalendarItemResponse create(CalendarEntryRequest request) {
        var creator = users.findById(SecurityUtils.getCurrentUserId())
                .orElseThrow(() -> new UnauthorizedException("Authenticated user not found"));
        var entry = entries.saveAndFlush(new CalendarEntry(request.date(), request.title().strip(), request.holiday(), creator));
        String title = entry.isHoliday() ? "Department holiday added" : "Calendar event added";
        String message = entry.getTitle() + " on " + entry.getEventDate()
                + (entry.isHoliday() ? ". No classes on this date." : ".") + " Added by " + creator.getName() + ".";
        // One transaction: either the entry and every notification persist, or neither does.
        for (Long recipientId : users.findActiveIdsByRoleNames(List.of("ROLE_STUDENT", "ROLE_HOD"))) {
            notifications.send(recipientId, Notification.NotificationType.SCHEDULE, title, message,
                    "CALENDAR", entry.getId(), "CALENDAR_ENTRY", Notification.NotificationPriority.NORMAL);
        }
        return CalendarItemResponse.from(entry);
    }
}

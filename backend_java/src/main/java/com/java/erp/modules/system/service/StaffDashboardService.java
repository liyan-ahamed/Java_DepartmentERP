package com.java.erp.modules.system.service;

import com.java.erp.common.util.SecurityUtils;
import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.academic.repository.SubjectOfferingRepository;
import com.java.erp.modules.attendance.entity.AttendanceSession;
import com.java.erp.modules.attendance.repository.AttendanceSessionRepository;
import com.java.erp.modules.gradebook.entity.Assessment;
import com.java.erp.modules.gradebook.repository.AssessmentRepository;
import com.java.erp.modules.holiday.dto.response.HolidayResponse;
import com.java.erp.modules.holiday.service.HolidayService;
import com.java.erp.modules.system.dto.response.StaffDashboardResponse;
import com.java.erp.modules.system.dto.response.StaffDashboardResponse.SkippedHoliday;
import com.java.erp.modules.system.dto.response.StaffDashboardResponse.Task;
import com.java.erp.modules.system.dto.response.StaffDashboardResponse.UpcomingClass;
import com.java.erp.modules.system.repository.StudentRepository;
import com.java.erp.modules.timetable.entity.TimetableSlot;
import com.java.erp.modules.timetable.repository.TimetableSlotRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Staff dashboard: the logged-in staff member's upcoming classes and computed tasks.
 * A fixed number of grouped queries per request; nothing is stored.
 */
@Service
public class StaffDashboardService {

    public static final String ATTENDANCE_FINALIZATION = "ATTENDANCE_FINALIZATION";
    public static final String MARKS_ENTRY = "MARKS_ENTRY";

    private final SubjectOfferingRepository offerings;
    private final TimetableSlotRepository slots;
    private final AttendanceSessionRepository sessions;
    private final AssessmentRepository assessments;
    private final StudentRepository students;
    private final HolidayService holidays;
    private final int horizonDays;
    private final int dueSoonHours;
    private final boolean gradebookEnabled;

    public StaffDashboardService(SubjectOfferingRepository offerings, TimetableSlotRepository slots,
                                 AttendanceSessionRepository sessions, AssessmentRepository assessments,
                                 StudentRepository students, HolidayService holidays,
                                 @Value("${dashboard.staff.upcoming-days:14}") int horizonDays,
                                 @Value("${dashboard.staff.marks-entry-due-soon-hours:48}") int dueSoonHours,
                                 @Value("${features.gradebook.enabled:false}") boolean gradebookEnabled) {
        this.offerings = offerings;
        this.slots = slots;
        this.sessions = sessions;
        this.assessments = assessments;
        this.students = students;
        this.holidays = holidays;
        this.horizonDays = horizonDays;
        this.dueSoonHours = dueSoonHours;
        this.gradebookEnabled = gradebookEnabled;
    }

    @Transactional(readOnly = true)
    public StaffDashboardResponse dashboard() {
        Long staffId = SecurityUtils.getCurrentUserId();
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        LocalDate last = today.plusDays(horizonDays - 1L);

        Map<LocalDate, List<HolidayResponse>> holidayDates = holidays.byDate(today, last);
        List<Long> ownOfferings = offerings.findActiveIdsByStaffId(staffId);
        Map<DayOfWeek, List<TimetableSlot>> byDay = ownOfferings.isEmpty() ? Map.of()
                : slots.findActiveOnDays(EnumSet.allOf(DayOfWeek.class), ownOfferings).stream()
                        .collect(Collectors.groupingBy(TimetableSlot::getDayOfWeek));
        // A holiday is listed when it is institution-wide or applies to a cohort the staff member teaches.
        Set<Long> ownBatches = byDay.values().stream().flatMap(List::stream)
                .map(t -> t.getSubjectOffering().getBatch().getId()).collect(Collectors.toSet());
        List<SkippedHoliday> skipped = holidayDates.entrySet().stream()
                .map(e -> Map.entry(e.getKey(), e.getValue().stream()
                        .filter(h -> h.batchIds().isEmpty() || h.batchIds().stream().anyMatch(ownBatches::contains)).toList()))
                .filter(e -> !e.getValue().isEmpty())
                .map(e -> new SkippedHoliday(e.getKey(), HolidayService.joinedNames(e.getValue())))
                .toList();

        List<UpcomingClass> upcoming = byDay.isEmpty() ? List.of() : upcomingClasses(byDay, today, last, now, holidayDates);

        List<Task> tasks = new ArrayList<>(attendanceTasks(staffId));
        if (gradebookEnabled) {
            tasks.addAll(marksEntryTasks(staffId, OffsetDateTime.now()));
        }
        return new StaffDashboardResponse(now, horizonDays, false, dueSoonHours, upcoming, skipped, tasks);
    }

    private List<UpcomingClass> upcomingClasses(Map<DayOfWeek, List<TimetableSlot>> byDay, LocalDate first, LocalDate last,
                                                LocalDateTime now, Map<LocalDate, List<HolidayResponse>> holidayDates) {
        List<UpcomingClass> result = new ArrayList<>();
        for (LocalDate date = first; !date.isAfter(last); date = date.plusDays(1)) {
            List<HolidayResponse> sameDay = holidayDates.get(date);
            for (TimetableSlot t : byDay.getOrDefault(date.getDayOfWeek(), List.of())) {
                if (!date.atTime(t.getStartTime()).isAfter(now)) continue;   // already started or over
                SubjectOffering o = t.getSubjectOffering();
                if (!HolidayService.applicable(sameDay, o.getBatch().getId()).isEmpty()) continue;   // holiday for this cohort
                result.add(new UpcomingClass(date, date.getDayOfWeek(), t.getPeriodNumber(), t.getStartTime(), t.getEndTime(),
                        t.getId(), o.getId(), o.getSubject().getCode(), o.getSubject().getName(), o.getSection().getId(),
                        o.getSection().getSectionName(), o.getBatch().getId(), o.getBatch().getBatchName(), o.getSemester(),
                        t.getRoom()));
            }
        }
        result.sort(Comparator.comparing(UpcomingClass::date).thenComparing(UpcomingClass::startTime)
                .thenComparing(UpcomingClass::sectionId).thenComparing(UpcomingClass::timetableSlotId));
        return result;
    }

    private List<Task> attendanceTasks(Long staffId) {
        return sessions.findDraftsOfStaff(staffId, AttendanceSession.Status.DRAFT).stream().map(s -> {
            SubjectOffering o = s.getSubjectOffering();
            return new Task(ATTENDANCE_FINALIZATION, "Finalize attendance: " + o.getSubject().getCode() + " "
                    + o.getSection().getSectionName() + " on " + s.getAttendanceDate(), "PENDING", o.getId(),
                    o.getSubject().getCode(), o.getSubject().getName(), o.getSection().getId(), o.getSection().getSectionName(),
                    s.getId(), s.getAttendanceDate(), s.getPeriodNumber(), null, null, null, null, null, null);
        }).toList();
    }

    /**
     * Expected = active students of the offering's section (the gradebook class list); completed = their
     * GRADED / ABSENT / EXEMPTED rows. A task exists only while remaining > 0 and the assessment is a DRAFT.
     */
    private List<Task> marksEntryTasks(Long staffId, OffsetDateTime now) {
        List<Assessment> open = assessments.findDraftsWithDeadlineOfStaff(staffId, Assessment.Status.DRAFT);
        if (open.isEmpty()) return List.of();
        Map<Long, Long> classSize = new HashMap<>();
        students.countActiveBySections(open.stream().map(a -> a.getSubjectOffering().getSection().getId()).distinct().toList())
                .forEach(c -> classSize.put(c.getSectionId(), c.getTotal()));
        Map<Long, Long> completed = new HashMap<>();
        assessments.countCompletedEntries(open.stream().map(Assessment::getId).toList())
                .forEach(c -> completed.put(c.getAssessmentId(), c.getTotal()));

        List<Task> tasks = new ArrayList<>();
        for (Assessment a : open) {
            SubjectOffering o = a.getSubjectOffering();
            long expected = classSize.getOrDefault(o.getSection().getId(), 0L);
            long remaining = expected - completed.getOrDefault(a.getId(), 0L);
            if (remaining <= 0) continue;
            tasks.add(new Task(MARKS_ENTRY, a.getTitle() + " Marks Entry Incomplete", marksStatus(a.getMarksEntryDueAt(), now),
                    o.getId(), o.getSubject().getCode(), o.getSubject().getName(), o.getSection().getId(),
                    o.getSection().getSectionName(), null, null, null, a.getId(), a.getTitle(), a.getAssessmentType().name(),
                    a.getMarksEntryDueAt(), (int) expected, (int) remaining));
        }
        return tasks;
    }

    /** OVERDUE after the deadline, DUE_SOON within dueSoonHours before it, else PENDING. */
    String marksStatus(OffsetDateTime dueAt, OffsetDateTime now) {
        if (now.isAfter(dueAt)) return "OVERDUE";
        if (!now.plusHours(dueSoonHours).isBefore(dueAt)) return "DUE_SOON";
        return "PENDING";
    }
}

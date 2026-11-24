package com.java.erp.modules.attendance.service;

import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.attendance.dto.response.AttendanceDayResponse;
import com.java.erp.modules.attendance.entity.AttendanceSession;
import com.java.erp.modules.attendance.entity.StudentAttendance;
import com.java.erp.modules.attendance.repository.AttendanceSessionRepository;
import com.java.erp.modules.attendance.repository.StudentAttendanceRepository;
import com.java.erp.modules.holiday.dto.response.HolidayResponse;
import com.java.erp.modules.holiday.service.HolidayService;
import com.java.erp.modules.system.entity.Student;
import com.java.erp.modules.timetable.entity.TimetableSlot;
import com.java.erp.modules.timetable.repository.TimetableSlotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Day view: expected classes come from the recurring timetable, recorded attendance from AttendanceSession.
 * Read-only: no session is ever created here. A fixed number of queries per request (offerings, slots,
 * sessions, holidays, and for students their own records).
 */
@Service
public class AttendanceDayService {

    static final String NOT_RECORDED = "NOT_RECORDED";

    private final AttendanceScope scope;
    private final TimetableSlotRepository slots;
    private final AttendanceSessionRepository sessions;
    private final StudentAttendanceRepository records;
    private final HolidayService holidays;

    public AttendanceDayService(AttendanceScope scope, TimetableSlotRepository slots, AttendanceSessionRepository sessions,
                                StudentAttendanceRepository records, HolidayService holidays) {
        this.scope = scope;
        this.slots = slots;
        this.sessions = sessions;
        this.records = records;
        this.holidays = holidays;
    }

    @Transactional(readOnly = true)
    public AttendanceDayResponse day(LocalDate date, Long batchId, Integer semester, Long sectionId) {
        boolean student = AttendanceScope.isStudent();
        List<SubjectOffering> visible = scope.visibleOfferings(batchId, semester, sectionId);
        // Published college holidays only; each class is judged by its own cohort, the day by the cohort asked for.
        List<HolidayResponse> effective = holidays.byDate(date, date).getOrDefault(date, List.of());
        Long dayBatch = student ? scope.student().getBatch().getId() : batchId != null ? batchId : singleBatch(visible);
        List<HolidayResponse> dayHolidays = HolidayService.applicable(effective, dayBatch);
        boolean holiday = !dayHolidays.isEmpty();
        String holidayName = HolidayService.joinedNames(dayHolidays);

        List<AttendanceDayResponse.ClassRow> rows = new ArrayList<>();
        if (!visible.isEmpty()) {
            List<Long> offeringIds = visible.stream().map(SubjectOffering::getId).toList();
            List<TimetableSlot> daySlots = slots.findActiveOnDays(List.of(date.getDayOfWeek()), offeringIds).stream()
                    .sorted(Comparator.comparing(TimetableSlot::getStartTime)
                            .thenComparing(t -> t.getSubjectOffering().getSection().getId())
                            .thenComparing(TimetableSlot::getId))
                    .toList();
            Map<Long, AttendanceSession> sessionBySlot = matchSessions(daySlots, sessions.findOnDate(date, offeringIds));
            if (student) {
                // A student never sees a session that is still a draft.
                sessionBySlot.values().removeIf(AttendanceSession::isDraft);
            }
            Map<Long, StudentAttendance.AttendanceStatus> mine = student ? myStatuses(scope.student(), sessionBySlot.values()) : Map.of();

            for (TimetableSlot t : daySlots) {
                SubjectOffering o = t.getSubjectOffering();
                List<HolidayResponse> classHolidays = HolidayService.applicable(effective, o.getBatch().getId());
                AttendanceSession s = sessionBySlot.get(t.getId());
                String myStatus = null;
                if (student) {
                    StudentAttendance.AttendanceStatus status = s == null ? null : mine.get(s.getId());
                    myStatus = status == null ? NOT_RECORDED : status.name();
                }
                rows.add(new AttendanceDayResponse.ClassRow(date, date.getDayOfWeek(), t.getPeriodNumber(), t.getStartTime(),
                        t.getEndTime(), t.getId(), t.getRoom(), o.getId(), o.getSubject().getId(), o.getSubject().getCode(),
                        o.getSubject().getName(), o.getSection().getId(), o.getSection().getSectionName(), o.getBatch().getId(),
                        o.getBatch().getBatchName(), o.getSemester(), o.getStaff().getId(), o.getStaff().getName(),
                        s == null ? null : s.getId(), s == null ? null : s.getStatus(), !classHolidays.isEmpty(),
                        HolidayService.joinedNames(classHolidays), myStatus));
            }
        }
        return new AttendanceDayResponse(date, date.getDayOfWeek(), holiday, holidayName,
                holiday ? dayHolidays.get(0).category() : null, rows);
    }

    /** The one cohort all visible classes belong to, else null (then only institution-wide holidays mark the day). */
    private static Long singleBatch(List<SubjectOffering> visible) {
        Set<Long> batchIds = visible.stream().map(o -> o.getBatch().getId()).collect(Collectors.toSet());
        return batchIds.size() == 1 ? batchIds.iterator().next() : null;
    }

    /**
     * Pairs each slot with the session recorded for it: a session linked to the slot, else one with the same
     * period number, else (when the offering has one slot that day) a session recorded without a period.
     */
    static Map<Long, AttendanceSession> matchSessions(List<TimetableSlot> daySlots, List<AttendanceSession> daySessions) {
        Map<Long, AttendanceSession> result = new HashMap<>();
        List<AttendanceSession> unused = new ArrayList<>(daySessions);
        for (TimetableSlot t : daySlots) {
            unused.stream().filter(s -> s.getTimetableSlot() != null && s.getTimetableSlot().getId().equals(t.getId()))
                    .findFirst().ifPresent(s -> { result.put(t.getId(), s); unused.remove(s); });
        }
        for (TimetableSlot t : daySlots) {
            if (result.containsKey(t.getId())) continue;
            Long offeringId = t.getSubjectOffering().getId();
            unused.stream().filter(s -> s.getTimetableSlot() == null && s.getSubjectOffering().getId().equals(offeringId)
                            && Objects.equals(s.getPeriodNumber(), t.getPeriodNumber()))
                    .findFirst().ifPresent(s -> { result.put(t.getId(), s); unused.remove(s); });
        }
        Map<Long, Long> slotsPerOffering = daySlots.stream()
                .collect(Collectors.groupingBy(t -> t.getSubjectOffering().getId(), Collectors.counting()));
        for (TimetableSlot t : daySlots) {
            Long offeringId = t.getSubjectOffering().getId();
            if (result.containsKey(t.getId()) || slotsPerOffering.get(offeringId) != 1) continue;
            unused.stream().filter(s -> s.getTimetableSlot() == null && s.getPeriodNumber() == null
                            && s.getSubjectOffering().getId().equals(offeringId))
                    .findFirst().ifPresent(s -> { result.put(t.getId(), s); unused.remove(s); });
        }
        return result;
    }

    private Map<Long, StudentAttendance.AttendanceStatus> myStatuses(Student me, Collection<AttendanceSession> finalized) {
        if (finalized.isEmpty()) return Map.of();
        List<Long> ids = finalized.stream().map(AttendanceSession::getId).toList();
        Map<Long, StudentAttendance.AttendanceStatus> result = new HashMap<>();
        records.findByStudentAndSessions(me.getId(), ids).forEach(r -> result.put(r.getSession().getId(), r.getStatus()));
        return result;
    }
}

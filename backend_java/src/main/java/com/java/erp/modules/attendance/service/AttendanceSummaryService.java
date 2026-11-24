package com.java.erp.modules.attendance.service;

import com.java.erp.exception.ResourceNotFoundException;
import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.academic.repository.SubjectOfferingRepository;
import com.java.erp.modules.academic.service.OfferingAccess;
import com.java.erp.modules.attendance.dto.response.AttendanceStats;
import com.java.erp.modules.attendance.dto.response.ClassAttendanceSummaryResponse;
import com.java.erp.modules.attendance.dto.response.SubjectAttendanceResponse;
import com.java.erp.modules.attendance.entity.AttendanceSession;
import com.java.erp.modules.attendance.entity.StudentAttendance.AttendanceStatus;
import com.java.erp.modules.attendance.repository.AttendanceSessionRepository;
import com.java.erp.modules.attendance.repository.StudentAttendanceRepository;
import com.java.erp.modules.attendance.repository.StudentAttendanceRepository.StatusCount;
import com.java.erp.modules.enrollment.service.EnrollmentRoster;
import com.java.erp.modules.gradebook.service.CurrentStudentResolver;
import com.java.erp.modules.notification.entity.Notification;
import com.java.erp.modules.notification.service.StudentNotifier;
import com.java.erp.modules.system.entity.Student;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.*;

/**
 * Attendance summaries (FINALIZED sessions only, percentages from AttendancePolicy) and the
 * attendance-shortage notification.
 */
@Service
public class AttendanceSummaryService {

    static final String SHORTAGE_REFERENCE_TYPE = "ATTENDANCE_SHORTAGE";

    private final StudentAttendanceRepository records;
    private final AttendanceSessionRepository sessions;
    private final SubjectOfferingRepository offerings;
    private final EnrollmentRoster roster;
    private final CurrentStudentResolver currentStudent;
    private final AttendancePolicy policy;
    private final StudentNotifier notifier;
    private final Duration shortageCooldown;

    public AttendanceSummaryService(StudentAttendanceRepository records, AttendanceSessionRepository sessions,
                                    SubjectOfferingRepository offerings, EnrollmentRoster roster,
                                    CurrentStudentResolver currentStudent, AttendancePolicy policy,
                                    StudentNotifier notifier,
                                    @Value("${attendance.shortage-notification-cooldown-days:7}") long cooldownDays) {
        this.records = records;
        this.sessions = sessions;
        this.offerings = offerings;
        this.roster = roster;
        this.currentStudent = currentStudent;
        this.policy = policy;
        this.notifier = notifier;
        this.shortageCooldown = Duration.ofDays(cooldownDays);
    }

    /** The logged-in student's attendance per subject (offerings they are or were enrolled in, not dropped). */
    @Transactional(readOnly = true)
    public List<SubjectAttendanceResponse> mySummary() {
        Student student = currentStudent.require();
        List<Long> offeringIds = roster.offeringIds(student.getId(), EnrollmentRoster.CURRENT_OR_COMPLETED);
        if (offeringIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Map<AttendanceStatus, Long>> byOffering = new HashMap<>();
        for (StatusCount c : records.countByStudent(student.getId(), AttendanceSession.Status.FINALIZED)) {
            byOffering.computeIfAbsent(c.getOfferingId(), k -> new EnumMap<>(AttendanceStatus.class)).put(c.getStatus(), c.getTotal());
        }
        return offerings.findByIdIn(offeringIds).stream()
                .sorted(Comparator.comparing((SubjectOffering o) -> o.getAcademicYear()).reversed()
                        .thenComparing(SubjectOffering::getSemester, Comparator.reverseOrder())
                        .thenComparing(o -> o.getSubject().getCode()))
                .map(o -> new SubjectAttendanceResponse(o.getId(), o.getSubject().getId(), o.getSubject().getCode(),
                        o.getSubject().getName(), o.getAcademicYear(), o.getSemester(), o.getStaff().getName(),
                        stats(byOffering.get(o.getId()))))
                .toList();
    }

    /** One row per ENROLLED student: HOD, or the staff member assigned to the offering. */
    @Transactional(readOnly = true)
    public ClassAttendanceSummaryResponse classSummary(Long offeringId) {
        SubjectOffering offering = offerings.findWithDetailsById(offeringId)
                .orElseThrow(() -> new ResourceNotFoundException("SubjectOffering", "id", offeringId));
        OfferingAccess.requireView(offering);

        Map<Long, AttendanceStats> stats = classStats(offeringId);
        List<ClassAttendanceSummaryResponse.Row> rows = roster.classList(offeringId).values().stream()
                .map(s -> new ClassAttendanceSummaryResponse.Row(s.getId(), s.getRegisterNumber(), s.getName(),
                        stats.getOrDefault(s.getId(), stats(null))))
                .toList();
        int below = (int) rows.stream().filter(r -> r.stats().belowThreshold()).count();
        return new ClassAttendanceSummaryResponse(offeringId, offering.getSubject().getCode(), offering.getSubject().getName(),
                offering.getSection().getId(), offering.getSection().getSectionName(),
                sessions.countBySubjectOfferingIdAndStatus(offeringId, AttendanceSession.Status.FINALIZED),
                policy.minimumPercentage(), below, rows);
    }

    /**
     * After a session is finalized: tells each ENROLLED student below the minimum, at most once per
     * subject per cooldown period (default 7 days). Returns the number of notifications sent.
     */
    @Transactional
    public int notifyShortages(SubjectOffering offering) {
        Map<Long, AttendanceStats> stats = classStats(offering.getId());
        int sent = 0;
        for (Student student : roster.classList(offering.getId()).values()) {
            AttendanceStats s = stats.get(student.getId());
            if (s == null || !s.belowThreshold()) continue;
            String message = "Your attendance in " + offering.getSubject().getCode() + " " + offering.getSubject().getName()
                    + " is " + s.attendancePercentage().stripTrailingZeros().toPlainString() + "% (minimum "
                    + s.minimumRequiredPercentage().stripTrailingZeros().toPlainString() + "%).";
            sent += notifier.notifyWithCooldown(student, new StudentNotifier.Message(
                    Notification.NotificationType.WARNING, "Attendance below minimum: " + offering.getSubject().getCode(),
                    message, "ATTENDANCE", offering.getId(), SHORTAGE_REFERENCE_TYPE,
                    Notification.NotificationPriority.HIGH), shortageCooldown);
        }
        return sent;
    }

    private Map<Long, AttendanceStats> classStats(Long offeringId) {
        Map<Long, Map<AttendanceStatus, Long>> byStudent = new HashMap<>();
        for (StatusCount c : records.countByOffering(offeringId, AttendanceSession.Status.FINALIZED)) {
            byStudent.computeIfAbsent(c.getStudentId(), k -> new EnumMap<>(AttendanceStatus.class)).put(c.getStatus(), c.getTotal());
        }
        Map<Long, AttendanceStats> result = new HashMap<>();
        byStudent.forEach((id, counts) -> result.put(id, stats(counts)));
        return result;
    }

    private AttendanceStats stats(Map<AttendanceStatus, Long> counts) {
        Map<AttendanceStatus, Long> c = counts == null ? Map.of() : counts;
        return policy.compute(c.getOrDefault(AttendanceStatus.PRESENT, 0L), c.getOrDefault(AttendanceStatus.ABSENT, 0L),
                c.getOrDefault(AttendanceStatus.ON_DUTY, 0L), c.getOrDefault(AttendanceStatus.EXCUSED, 0L));
    }
}

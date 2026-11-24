package com.java.erp.modules.attendance.service;

import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.attendance.dto.response.AttendanceStats;
import com.java.erp.modules.attendance.dto.response.AttendanceTotals;
import com.java.erp.modules.attendance.dto.response.ClassAttendanceOverviewResponse;
import com.java.erp.modules.attendance.dto.response.StudentAttendanceOverviewResponse;
import com.java.erp.modules.attendance.entity.AttendanceSession;
import com.java.erp.modules.attendance.entity.StudentAttendance.AttendanceStatus;
import com.java.erp.modules.attendance.repository.AttendanceSessionRepository;
import com.java.erp.modules.attendance.repository.StudentAttendanceRepository;
import com.java.erp.modules.attendance.repository.StudentAttendanceRepository.StatusCount;
import com.java.erp.modules.enrollment.repository.CourseEnrollmentRepository;
import com.java.erp.modules.system.entity.Student;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * Subject + Overall attendance summaries. Every figure comes from grouped queries (a fixed number per request,
 * never one per student or per session) and every percentage from summed counts via AttendancePolicy.
 */
@Service
public class AttendanceOverviewService {

    private final AttendanceScope scope;
    private final StudentAttendanceRepository records;
    private final AttendanceSessionRepository sessions;
    private final CourseEnrollmentRepository enrollments;
    private final AttendancePolicy policy;

    public AttendanceOverviewService(AttendanceScope scope, StudentAttendanceRepository records,
                                     AttendanceSessionRepository sessions, CourseEnrollmentRepository enrollments,
                                     AttendancePolicy policy) {
        this.scope = scope;
        this.records = records;
        this.sessions = sessions;
        this.enrollments = enrollments;
        this.policy = policy;
    }

    /** Counts of the four statuses (PRESENT, ABSENT, ON_DUTY, EXCUSED). */
    private static final class Counts {
        long present, absent, onDuty, excused;

        void add(AttendanceStatus status, long n) {
            switch (status) {
                case PRESENT -> present += n;
                case ABSENT -> absent += n;
                case ON_DUTY -> onDuty += n;
                case EXCUSED -> excused += n;
            }
        }

        void add(Counts other) {
            present += other.present;
            absent += other.absent;
            onDuty += other.onDuty;
            excused += other.excused;
        }
    }

    private AttendanceStats stats(Counts c) {
        return policy.compute(c.present, c.absent, c.onDuty, c.excused);
    }

    /** STUDENT: active enrolled subjects + Overall from summed counts. */
    @Transactional(readOnly = true)
    public StudentAttendanceOverviewResponse mine() {
        Student me = scope.student();
        List<SubjectOffering> visible = scope.visibleOfferings(null, null, null);
        Map<Long, Counts> byOffering = new HashMap<>();
        for (StatusCount c : records.countByStudent(me.getId(), AttendanceSession.Status.FINALIZED)) {
            byOffering.computeIfAbsent(c.getOfferingId(), k -> new Counts()).add(c.getStatus(), c.getTotal());
        }
        Counts overall = new Counts();
        List<StudentAttendanceOverviewResponse.Subject> subjects = new ArrayList<>();
        for (SubjectOffering o : visible) {
            Counts c = byOffering.getOrDefault(o.getId(), new Counts());
            overall.add(c);
            subjects.add(new StudentAttendanceOverviewResponse.Subject(o.getId(), o.getSubject().getId(), o.getSubject().getCode(),
                    o.getSubject().getName(), o.getAcademicYear(), o.getSemester(), o.getStaff().getName(),
                    AttendanceTotals.of(stats(c))));
        }
        return new StudentAttendanceOverviewResponse(policy.minimumPercentage(), subjects, AttendanceTotals.of(stats(overall)));
    }

    /** HOD (one class) / STAFF (own offerings): per subject + Overall. */
    @Transactional(readOnly = true)
    public ClassAttendanceOverviewResponse classOverview(Long batchId, Integer semester, Long sectionId) {
        List<SubjectOffering> visible = scope.visibleOfferings(batchId, semester, sectionId);
        BigDecimal minimum = policy.minimumPercentage();
        if (visible.isEmpty()) {
            return new ClassAttendanceOverviewResponse(batchId, semester, sectionId, minimum, List.of(),
                    new ClassAttendanceOverviewResponse.OverallRow(0, 0, 0, 0, 0, 0, 0, 0, 0, null, 0, minimum));
        }
        List<Long> ids = visible.stream().map(SubjectOffering::getId).toList();

        Map<Long, Set<Long>> enrolled = new HashMap<>();
        enrollments.findEnrolledPairs(ids).forEach(p ->
                enrolled.computeIfAbsent(p.getOfferingId(), k -> new HashSet<>()).add(p.getStudentId()));
        Map<Long, Long> conducted = new HashMap<>();
        sessions.countByOfferings(ids, AttendanceSession.Status.FINALIZED).forEach(c -> conducted.put(c.getOfferingId(), c.getTotal()));
        // offering -> student -> counts, only for currently enrolled students.
        Map<Long, Map<Long, Counts>> perStudent = new HashMap<>();
        for (StatusCount c : records.countByOfferings(ids, AttendanceSession.Status.FINALIZED)) {
            if (!enrolled.getOrDefault(c.getOfferingId(), Set.of()).contains(c.getStudentId())) continue;
            perStudent.computeIfAbsent(c.getOfferingId(), k -> new HashMap<>())
                    .computeIfAbsent(c.getStudentId(), k -> new Counts()).add(c.getStatus(), c.getTotal());
        }

        List<ClassAttendanceOverviewResponse.SubjectRow> rows = new ArrayList<>();
        Counts classTotal = new Counts();
        Map<Long, Counts> studentOverall = new HashMap<>();
        Set<Long> allStudents = new HashSet<>();
        long allConducted = 0;
        for (SubjectOffering o : visible) {
            Counts total = new Counts();
            long below = 0;
            for (Map.Entry<Long, Counts> e : perStudent.getOrDefault(o.getId(), Map.of()).entrySet()) {
                total.add(e.getValue());
                studentOverall.computeIfAbsent(e.getKey(), k -> new Counts()).add(e.getValue());
                if (stats(e.getValue()).belowThreshold()) below++;
            }
            Set<Long> students = enrolled.getOrDefault(o.getId(), Set.of());
            allStudents.addAll(students);
            classTotal.add(total);
            long classes = conducted.getOrDefault(o.getId(), 0L);
            allConducted += classes;
            AttendanceStats s = stats(total);
            rows.add(new ClassAttendanceOverviewResponse.SubjectRow(o.getId(), o.getSubject().getId(), o.getSubject().getCode(),
                    o.getSubject().getName(), o.getSection().getId(), o.getSection().getSectionName(), o.getBatch().getId(),
                    o.getBatch().getBatchName(), o.getSemester(), o.getStaff().getId(), o.getStaff().getName(), classes,
                    students.size(), total.present, total.absent, total.onDuty, total.excused, s.eligible(), s.attended(),
                    s.attendancePercentage(), below, minimum));
        }
        long overallBelow = studentOverall.values().stream().filter(c -> stats(c).belowThreshold()).count();
        AttendanceStats o = stats(classTotal);
        return new ClassAttendanceOverviewResponse(batchId, semester, sectionId, minimum, rows,
                new ClassAttendanceOverviewResponse.OverallRow(rows.size(), allConducted, allStudents.size(),
                        classTotal.present, classTotal.absent, classTotal.onDuty, classTotal.excused, o.eligible(), o.attended(),
                        o.attendancePercentage(), overallBelow, minimum));
    }
}

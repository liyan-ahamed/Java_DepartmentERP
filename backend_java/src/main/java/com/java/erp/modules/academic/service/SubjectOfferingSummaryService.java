package com.java.erp.modules.academic.service;

import com.java.erp.exception.ResourceNotFoundException;
import com.java.erp.modules.academic.dto.response.SubjectOfferingSummaryResponse;
import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.academic.repository.SubjectOfferingRepository;
import com.java.erp.modules.attendance.service.AttendancePolicy;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** One aggregate query per offering (no per-student queries). HOD, or the assigned STAFF member. */
@Service
public class SubjectOfferingSummaryService {

    private static final String SUMMARY_SQL = """
            SELECT
              (SELECT COUNT(*) FROM course_enrollments e JOIN students s ON s.id = e.student_id
                 WHERE e.subject_offering_id = :id AND e.enrollment_status = 'ENROLLED' AND s.is_active),
              (SELECT COUNT(*) FROM attendance_sessions a WHERE a.subject_offering_id = :id AND a.status = 'FINALIZED'),
              (SELECT COUNT(*) FILTER (WHERE r.attendance_status = 'PRESENT') FROM student_attendance r
                 JOIN attendance_sessions a ON a.id = r.attendance_session_id
                 WHERE a.subject_offering_id = :id AND a.status = 'FINALIZED'),
              (SELECT COUNT(*) FILTER (WHERE r.attendance_status = 'ABSENT') FROM student_attendance r
                 JOIN attendance_sessions a ON a.id = r.attendance_session_id
                 WHERE a.subject_offering_id = :id AND a.status = 'FINALIZED'),
              (SELECT COUNT(*) FILTER (WHERE r.attendance_status = 'ON_DUTY') FROM student_attendance r
                 JOIN attendance_sessions a ON a.id = r.attendance_session_id
                 WHERE a.subject_offering_id = :id AND a.status = 'FINALIZED'),
              (SELECT COUNT(*) FILTER (WHERE r.attendance_status = 'EXCUSED') FROM student_attendance r
                 JOIN attendance_sessions a ON a.id = r.attendance_session_id
                 WHERE a.subject_offering_id = :id AND a.status = 'FINALIZED'),
              (SELECT COUNT(*) FROM assessments x WHERE x.subject_offering_id = :id AND x.status = 'FINALIZED'),
              (SELECT AVG(g.marks * 100.0 / x.max_marks) FROM student_grades g JOIN assessments x ON x.id = g.assessment_id
                 WHERE x.subject_offering_id = :id AND x.status = 'FINALIZED' AND g.grade_status = 'GRADED'),
              (SELECT COUNT(*) FROM exam_results t WHERE t.subject_offering_id = :id AND t.published),
              (SELECT COUNT(*) FROM exam_results t WHERE t.subject_offering_id = :id AND t.published AND t.result_status = 'PASS'),
              (SELECT COUNT(*) FROM exam_results t WHERE t.subject_offering_id = :id AND t.published AND t.result_status = 'FAIL')
            """;

    private final SubjectOfferingRepository offerings;
    private final AttendancePolicy attendancePolicy;
    private final EntityManager entityManager;

    public SubjectOfferingSummaryService(SubjectOfferingRepository offerings, AttendancePolicy attendancePolicy,
                                         EntityManager entityManager) {
        this.offerings = offerings;
        this.attendancePolicy = attendancePolicy;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public SubjectOfferingSummaryResponse summary(Long id) {
        SubjectOffering offering = offerings.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SubjectOffering", "id", id));
        OfferingAccess.requireView(offering);
        Object[] row = (Object[]) entityManager.createNativeQuery(SUMMARY_SQL).setParameter("id", id).getSingleResult();
        BigDecimal attendance = attendancePolicy.compute(count(row[2]), count(row[3]), count(row[4]), count(row[5])).attendancePercentage();
        BigDecimal average = row[7] == null ? null : new BigDecimal(row[7].toString()).setScale(2, RoundingMode.HALF_UP);
        return new SubjectOfferingSummaryResponse(id, count(row[0]), count(row[1]), attendance, count(row[6]), average,
                count(row[8]), count(row[9]), count(row[10]));
    }

    private static long count(Object value) {
        return ((Number) value).longValue();
    }
}

package com.java.erp.modules.attendance.repository;

import com.java.erp.modules.attendance.entity.AttendanceSession;
import com.java.erp.modules.attendance.entity.StudentAttendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface StudentAttendanceRepository extends JpaRepository<StudentAttendance, Long> {

    List<StudentAttendance> findBySessionId(Long sessionId);

    @Query("SELECT COUNT(r) FROM StudentAttendance r WHERE r.session.id = :sessionId AND r.student.id IN :studentIds")
    long countMarked(@Param("sessionId") Long sessionId, @Param("studentIds") Collection<Long> studentIds);

    /** Per student and status counts over the FINALIZED sessions of one offering. */
    @Query("SELECT r.student.id AS studentId, r.session.subjectOffering.id AS offeringId, r.status AS status, COUNT(r) AS total " +
           "FROM StudentAttendance r WHERE r.session.subjectOffering.id = :offeringId AND r.session.status = :finalized " +
           "GROUP BY r.student.id, r.session.subjectOffering.id, r.status")
    List<StatusCount> countByOffering(@Param("offeringId") Long offeringId,
                                      @Param("finalized") AttendanceSession.Status finalized);

    /** Per offering and status counts over the FINALIZED sessions of one student. */
    @Query("SELECT r.student.id AS studentId, r.session.subjectOffering.id AS offeringId, r.status AS status, COUNT(r) AS total " +
           "FROM StudentAttendance r WHERE r.student.id = :studentId AND r.session.status = :finalized " +
           "GROUP BY r.student.id, r.session.subjectOffering.id, r.status")
    List<StatusCount> countByStudent(@Param("studentId") Long studentId,
                                     @Param("finalized") AttendanceSession.Status finalized);

    /** Per student, offering and status counts over the FINALIZED sessions of several offerings (one class view). */
    @Query("SELECT r.student.id AS studentId, r.session.subjectOffering.id AS offeringId, r.status AS status, COUNT(r) AS total " +
           "FROM StudentAttendance r WHERE r.session.subjectOffering.id IN :offeringIds AND r.session.status = :finalized " +
           "GROUP BY r.student.id, r.session.subjectOffering.id, r.status")
    List<StatusCount> countByOfferings(@Param("offeringIds") Collection<Long> offeringIds,
                                       @Param("finalized") AttendanceSession.Status finalized);

    /** One student's records in the given sessions (student day view). */
    @Query("SELECT r FROM StudentAttendance r WHERE r.student.id = :studentId AND r.session.id IN :sessionIds")
    List<StudentAttendance> findByStudentAndSessions(@Param("studentId") Long studentId,
                                                     @Param("sessionIds") Collection<Long> sessionIds);

    interface StatusCount {
        Long getStudentId();
        Long getOfferingId();
        StudentAttendance.AttendanceStatus getStatus();
        Long getTotal();
    }
}

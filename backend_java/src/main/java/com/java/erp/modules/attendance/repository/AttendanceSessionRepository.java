package com.java.erp.modules.attendance.repository;

import com.java.erp.modules.attendance.entity.AttendanceSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AttendanceSessionRepository extends JpaRepository<AttendanceSession, Long>,
        JpaSpecificationExecutor<AttendanceSession> {

    @Override
    @EntityGraph(attributePaths = {"subjectOffering", "subjectOffering.subject", "subjectOffering.section",
            "subjectOffering.staff", "createdBy"})
    Page<AttendanceSession> findAll(Specification<AttendanceSession> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"subjectOffering", "subjectOffering.subject", "subjectOffering.batch",
            "subjectOffering.section", "subjectOffering.staff", "createdBy", "timetableSlot"})
    Optional<AttendanceSession> findWithDetailsById(Long id);

    /** Another session of the offering on the same date and period (excludeId = session being edited, or 0). */
    boolean existsBySubjectOfferingIdAndAttendanceDateAndPeriodNumberAndIdNot(
            Long subjectOfferingId, LocalDate attendanceDate, Integer periodNumber, Long excludeId);

    long countBySubjectOfferingIdAndStatus(Long subjectOfferingId, AttendanceSession.Status status);

    /** Sessions of these offerings on one date (day view). */
    @Query("SELECT s FROM AttendanceSession s LEFT JOIN FETCH s.timetableSlot " +
           "WHERE s.attendanceDate = :date AND s.subjectOffering.id IN :offeringIds")
    List<AttendanceSession> findOnDate(@Param("date") LocalDate date, @Param("offeringIds") Collection<Long> offeringIds);

    /** Number of sessions with the given status per offering. */
    @Query("SELECT s.subjectOffering.id AS offeringId, COUNT(s) AS total FROM AttendanceSession s " +
           "WHERE s.subjectOffering.id IN :offeringIds AND s.status = :status GROUP BY s.subjectOffering.id")
    List<OfferingCount> countByOfferings(@Param("offeringIds") Collection<Long> offeringIds,
                                         @Param("status") AttendanceSession.Status status);

    /** DRAFT sessions of the staff member's active offerings, oldest first (staff dashboard). */
    @Query("SELECT s FROM AttendanceSession s JOIN FETCH s.subjectOffering o JOIN FETCH o.subject JOIN FETCH o.section " +
           "WHERE o.staff.id = :staffId AND o.active = true AND s.status = :draft ORDER BY s.attendanceDate, s.periodNumber, s.id")
    List<AttendanceSession> findDraftsOfStaff(@Param("staffId") Long staffId, @Param("draft") AttendanceSession.Status draft);

    interface OfferingCount {
        Long getOfferingId();
        Long getTotal();
    }

    /** DRAFT -> FINALIZED in one guarded statement; returns 0 if the session was not DRAFT. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE AttendanceSession s SET s.status = :finalized, s.finalizedAt = :now, s.updatedAt = :now " +
           "WHERE s.id = :id AND s.status = :draft")
    int finalizeIfDraft(@Param("id") Long id, @Param("now") LocalDateTime now,
                        @Param("draft") AttendanceSession.Status draft,
                        @Param("finalized") AttendanceSession.Status finalized);
}

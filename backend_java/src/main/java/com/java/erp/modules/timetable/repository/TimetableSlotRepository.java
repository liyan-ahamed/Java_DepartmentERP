package com.java.erp.modules.timetable.repository;

import com.java.erp.modules.timetable.entity.TimetableSlot;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TimetableSlotRepository extends JpaRepository<TimetableSlot, Long>, JpaSpecificationExecutor<TimetableSlot> {

    @Override
    @EntityGraph(attributePaths = {"subjectOffering", "subjectOffering.subject", "subjectOffering.batch",
            "subjectOffering.section", "subjectOffering.staff"})
    List<TimetableSlot> findAll(Specification<TimetableSlot> spec);

    @EntityGraph(attributePaths = {"subjectOffering", "subjectOffering.subject", "subjectOffering.batch",
            "subjectOffering.section", "subjectOffering.staff"})
    Optional<TimetableSlot> findWithDetailsById(Long id);

    /**
     * Active slots of active offerings on the same day whose times overlap [start, end) and that
     * share the section or the staff member (excludeId = the slot being edited, or 0).
     */
    @Query("SELECT t FROM TimetableSlot t JOIN FETCH t.subjectOffering o JOIN FETCH o.subject " +
           "JOIN FETCH o.section JOIN FETCH o.staff " +
           "WHERE t.active = true AND o.active = true AND t.dayOfWeek = :day " +
           "AND t.startTime < :end AND t.endTime > :start AND t.id <> :excludeId " +
           "AND (o.section.id = :sectionId OR o.staff.id = :staffId)")
    List<TimetableSlot> findConflicts(@Param("day") DayOfWeek day, @Param("start") LocalTime start,
                                      @Param("end") LocalTime end, @Param("sectionId") Long sectionId,
                                      @Param("staffId") Long staffId, @Param("excludeId") Long excludeId);

    @Query("SELECT t FROM TimetableSlot t JOIN FETCH t.subjectOffering o JOIN FETCH o.subject JOIN FETCH o.batch " +
           "JOIN FETCH o.section JOIN FETCH o.staff " +
           "WHERE t.active = true AND o.active = true AND o.id IN :offeringIds")
    List<TimetableSlot> findActiveByOfferingIds(@Param("offeringIds") Collection<Long> offeringIds);

    /** Every active slot of an active offering (dev timetable seeding: who is busy when). */
    @Query("SELECT t FROM TimetableSlot t JOIN FETCH t.subjectOffering o JOIN FETCH o.subject JOIN FETCH o.section " +
           "JOIN FETCH o.staff WHERE t.active = true AND o.active = true")
    List<TimetableSlot> findAllActiveOfActiveOfferings();

    List<TimetableSlot> findBySubjectOfferingIdInAndActiveTrue(Collection<Long> offeringIds);

    /** Active slots of active offerings on one weekday, for the given offerings (day views, upcoming classes). */
    @Query("SELECT t FROM TimetableSlot t JOIN FETCH t.subjectOffering o JOIN FETCH o.subject JOIN FETCH o.batch " +
           "JOIN FETCH o.section JOIN FETCH o.staff " +
           "WHERE t.active = true AND o.active = true AND t.dayOfWeek IN :days AND o.id IN :offeringIds")
    List<TimetableSlot> findActiveOnDays(@Param("days") Collection<DayOfWeek> days,
                                         @Param("offeringIds") Collection<Long> offeringIds);

    /** Serializes timetable writes so two concurrent requests cannot both pass the overlap check. */
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(:key)) AS l", nativeQuery = true)
    Integer lockTimetable(@Param("key") long key);
}

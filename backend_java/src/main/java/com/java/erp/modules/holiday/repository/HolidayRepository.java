package com.java.erp.modules.holiday.repository;

import com.java.erp.modules.holiday.entity.Holiday;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface HolidayRepository extends JpaRepository<Holiday, Long>, JpaSpecificationExecutor<Holiday> {

    List<Holiday> findByYearAndScope(Integer year, Holiday.Scope scope);

    boolean existsByYearAndScope(Integer year, Holiday.Scope scope);

    long countByYearAndScope(Integer year, Holiday.Scope scope);

    List<Holiday> findByHolidayDateBetweenOrderByHolidayDateAscScopeAscNameAsc(LocalDate from, LocalDate to);

    /** Holidays of the given scopes whose date range overlaps [from, to], with their cohorts, by start date then name. */
    @EntityGraph(attributePaths = "batchIds")
    @Query("SELECT DISTINCT h FROM Holiday h WHERE h.scope IN :scopes AND h.holidayDate <= :to " +
           "AND COALESCE(h.endDate, h.holidayDate) >= :from ORDER BY h.holidayDate, h.name")
    List<Holiday> findOverlapping(@Param("scopes") Collection<Holiday.Scope> scopes,
                                  @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Another college holiday with this name (case-insensitive) in the year (excludeId = the holiday being edited, or 0). */
    @Query("SELECT COUNT(h) > 0 FROM Holiday h WHERE h.scope = com.java.erp.modules.holiday.entity.Holiday.Scope.COLLEGE " +
           "AND h.year = :year AND LOWER(h.name) = LOWER(:name) AND h.id <> :excludeId")
    boolean existsCollegeName(@Param("year") int year, @Param("name") String name, @Param("excludeId") Long excludeId);

    /** Serializes holiday syncs so two concurrent runs cannot insert the same row twice. */
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(:key)) AS l", nativeQuery = true)
    Integer lockHolidays(@Param("key") long key);
}

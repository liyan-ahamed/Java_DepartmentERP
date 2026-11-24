package com.java.erp.modules.holiday.repository;

import com.java.erp.modules.holiday.entity.HolidayCalendar;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface HolidayCalendarRepository extends JpaRepository<HolidayCalendar, Long> {

    @EntityGraph(attributePaths = "publishedBy")
    Optional<HolidayCalendar> findByYear(Integer year);

    boolean existsByYear(Integer year);

    List<HolidayCalendar> findByYearIn(Collection<Integer> years);

    @Query("SELECT c.year FROM HolidayCalendar c WHERE c.status = com.java.erp.modules.holiday.entity.HolidayCalendar.Status.PUBLISHED " +
           "AND c.year BETWEEN :fromYear AND :toYear")
    List<Integer> findPublishedYears(@Param("fromYear") int fromYear, @Param("toYear") int toYear);
}

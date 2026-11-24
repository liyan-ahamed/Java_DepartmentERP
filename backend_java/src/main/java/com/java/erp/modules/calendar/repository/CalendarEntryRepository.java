package com.java.erp.modules.calendar.repository;

import com.java.erp.modules.calendar.entity.CalendarEntry;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface CalendarEntryRepository extends JpaRepository<CalendarEntry, Long> {
    @EntityGraph(attributePaths = "createdBy")
    List<CalendarEntry> findByEventDateBetweenOrderByEventDateAscIdAsc(LocalDate from, LocalDate to);
}

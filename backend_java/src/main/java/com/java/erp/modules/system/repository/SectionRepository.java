package com.java.erp.modules.system.repository;

import com.java.erp.modules.system.entity.Section;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SectionRepository extends JpaRepository<Section, Long> {

    List<Section> findAllByOrderByBatchCurrentYearAscSectionNameAsc();

    @Query("SELECT s FROM Section s JOIN FETCH s.batch b WHERE b.active = true " +
           "ORDER BY b.currentYear, s.sectionName")
    List<Section> findAllForActiveBatches();

    @Query("SELECT s FROM Section s JOIN s.batch b " +
           "WHERE b.active = true AND b.currentYear = :year AND UPPER(s.sectionName) = UPPER(:sectionName)")
    Optional<Section> findByYearAndSectionName(@Param("year") short year, @Param("sectionName") String sectionName);

    List<Section> findByBatchIdOrderBySectionNameAsc(Long batchId);
}

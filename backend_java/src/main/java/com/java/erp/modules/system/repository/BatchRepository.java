package com.java.erp.modules.system.repository;

import com.java.erp.modules.system.entity.Batch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BatchRepository extends JpaRepository<Batch, Long> {

    List<Batch> findByActiveTrueAndCurrentYear(Short currentYear);

    List<Batch> findByActiveTrueOrderByCurrentYearAscBatchNameAsc();

    Optional<Batch> findByBatchName(String batchName);
}

package com.java.erp.modules.curriculum.repository;

import com.java.erp.modules.curriculum.entity.CurriculumRequirement;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CurriculumRequirementRepository extends JpaRepository<CurriculumRequirement, Long> {

    @EntityGraph(attributePaths = {"batch", "updatedBy"})
    Optional<CurriculumRequirement> findByBatchIdAndSemester(Long batchId, Integer semester);
}

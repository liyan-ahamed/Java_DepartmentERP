package com.java.erp.modules.fees.repository;

import com.java.erp.modules.fees.entity.FeeStructure;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface FeeStructureRepository extends JpaRepository<FeeStructure, Long>, JpaSpecificationExecutor<FeeStructure> {

    @Override
    @EntityGraph(attributePaths = {"batch"})
    Page<FeeStructure> findAll(Specification<FeeStructure> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"batch"})
    Optional<FeeStructure> findWithDetailsById(Long id);

    boolean existsByNameIgnoreCaseAndAcademicYear(String name, String academicYear);

    /** The active charges of the "Year N" template, in creation order. */
    List<FeeStructure> findByStudentYearAndAcademicYearIsNullAndActiveTrueOrderByIdAsc(Short studentYear);
}

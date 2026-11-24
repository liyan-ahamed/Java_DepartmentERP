package com.java.erp.modules.lessonplan.repository;

import com.java.erp.modules.lessonplan.entity.LessonPlan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LessonPlanRepository extends JpaRepository<LessonPlan, Long>, JpaSpecificationExecutor<LessonPlan> {

    @Override
    @EntityGraph(attributePaths = {"subjectOffering", "subjectOffering.subject", "subjectOffering.section",
            "subjectOffering.staff", "createdBy"})
    Page<LessonPlan> findAll(Specification<LessonPlan> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"subjectOffering", "subjectOffering.subject", "subjectOffering.section",
            "subjectOffering.staff", "createdBy"})
    Optional<LessonPlan> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"subjectOffering", "subjectOffering.subject", "subjectOffering.section",
            "subjectOffering.staff", "createdBy"})
    List<LessonPlan> findBySubjectOfferingIdInOrderBySubjectOfferingIdAscPlannedDateAscIdAsc(Collection<Long> offeringIds);

    @Query("SELECT p.status AS status, COUNT(p) AS total FROM LessonPlan p WHERE p.subjectOffering.id = :offeringId GROUP BY p.status")
    List<StatusCount> countByStatus(@Param("offeringId") Long offeringId);

    interface StatusCount {
        LessonPlan.Status getStatus();
        Long getTotal();
    }
}

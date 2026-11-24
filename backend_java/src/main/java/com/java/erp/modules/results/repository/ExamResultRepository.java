package com.java.erp.modules.results.repository;

import com.java.erp.modules.exams.entity.ExamPeriod;
import com.java.erp.modules.results.entity.ExamResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ExamResultRepository extends JpaRepository<ExamResult, Long>, JpaSpecificationExecutor<ExamResult> {

    @Override
    @EntityGraph(attributePaths = {"student", "student.section", "subjectOffering", "subjectOffering.subject",
            "subjectOffering.section", "examPeriod"})
    Page<ExamResult> findAll(Specification<ExamResult> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"student", "subjectOffering", "subjectOffering.subject", "subjectOffering.section", "examPeriod"})
    List<ExamResult> findBySubjectOfferingIdAndExamPeriodId(Long subjectOfferingId, Long examPeriodId);

    @EntityGraph(attributePaths = {"student", "student.section", "subjectOffering", "subjectOffering.subject",
            "subjectOffering.section", "examPeriod"})
    List<ExamResult> findByStudentIdAndPublishedTrueOrderByExamPeriodIdDescSubjectOfferingIdAsc(Long studentId);

    /** Results of these offerings in exam periods of one type (read-only results view). */
    @EntityGraph(attributePaths = {"student", "subjectOffering", "subjectOffering.subject", "subjectOffering.batch",
            "subjectOffering.section", "examPeriod"})
    List<ExamResult> findBySubjectOfferingIdInAndExamPeriodExamType(Collection<Long> subjectOfferingIds,
                                                                    ExamPeriod.ExamType examType);

    /** Publishes every unpublished row of one offering in one exam period; returns the number published. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE ExamResult r SET r.published = true, r.publishedAt = :now, r.publishedBy = :publisher, r.updatedAt = :now " +
           "WHERE r.subjectOffering.id = :offeringId AND r.examPeriod.id = :periodId AND r.published = false")
    int publish(@Param("offeringId") Long offeringId, @Param("periodId") Long periodId,
                @Param("publisher") com.java.erp.modules.auth.entity.User publisher, @Param("now") LocalDateTime now);
}

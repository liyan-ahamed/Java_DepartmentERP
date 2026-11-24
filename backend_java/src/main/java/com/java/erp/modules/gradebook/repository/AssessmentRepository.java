package com.java.erp.modules.gradebook.repository;

import com.java.erp.modules.gradebook.entity.Assessment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AssessmentRepository extends JpaRepository<Assessment, Long>, JpaSpecificationExecutor<Assessment> {

    @Override
    @EntityGraph(attributePaths = {"subjectOffering", "subjectOffering.subject", "subjectOffering.batch",
            "subjectOffering.section", "subjectOffering.staff", "createdBy"})
    Page<Assessment> findAll(Specification<Assessment> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"subjectOffering", "subjectOffering.subject", "subjectOffering.batch",
            "subjectOffering.section", "subjectOffering.staff", "createdBy"})
    Optional<Assessment> findWithDetailsById(Long id);

    boolean existsBySubjectOfferingId(Long subjectOfferingId);

    /** DRAFT assessments with a marks-entry deadline, of the staff member's active offerings (staff tasks). */
    @Query("SELECT a FROM Assessment a JOIN FETCH a.subjectOffering o JOIN FETCH o.subject JOIN FETCH o.section " +
           "WHERE o.staff.id = :staffId AND o.active = true AND a.status = :draft AND a.marksEntryDueAt IS NOT NULL " +
           "ORDER BY a.marksEntryDueAt, a.id")
    List<Assessment> findDraftsWithDeadlineOfStaff(@Param("staffId") Long staffId, @Param("draft") Assessment.Status draft);

    /**
     * Per assessment: grade rows (GRADED, ABSENT or EXEMPTED) of active students of the offering's section,
     * i.e. completed marks entries of the gradebook class list.
     */
    @Query("SELECT g.assessment.id AS assessmentId, COUNT(g) AS total FROM StudentGrade g " +
           "WHERE g.assessment.id IN :ids AND g.student.active = true " +
           "AND g.student.section.id = g.assessment.subjectOffering.section.id GROUP BY g.assessment.id")
    List<AssessmentCount> countCompletedEntries(@Param("ids") Collection<Long> ids);

    interface AssessmentCount {
        Long getAssessmentId();
        Long getTotal();
    }

    /** All assessments (any status) of these offerings (assessment overview). */
    List<Assessment> findBySubjectOfferingIdInOrderByAssessmentDateAscIdAsc(Collection<Long> subjectOfferingIds);

    List<Assessment> findBySubjectOfferingIdInAndStatusOrderByAssessmentDateAscIdAsc(
            Collection<Long> subjectOfferingIds, Assessment.Status status);

    /**
     * FINALIZED assessments a student may see: those of offerings for the student's section,
     * plus any other FINALIZED assessment the student already has a grade in.
     */
    @Query("SELECT a FROM Assessment a JOIN FETCH a.subjectOffering o JOIN FETCH o.subject " +
           "WHERE a.status = :status AND (o.section.id = :sectionId OR EXISTS (" +
           "  SELECT g.id FROM StudentGrade g WHERE g.assessment = a AND g.student.id = :studentId)) " +
           "ORDER BY o.subject.code, a.assessmentDate, a.id")
    List<Assessment> findVisibleToStudent(@Param("status") Assessment.Status status,
                                          @Param("sectionId") Long sectionId,
                                          @Param("studentId") Long studentId);

    /**
     * DRAFT -> FINALIZED in one guarded statement, so two concurrent or repeated requests
     * cannot both finalize (and both notify). Returns 0 if it was not DRAFT.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Assessment a SET a.status = :finalized, a.finalizedAt = :now, a.updatedAt = :now " +
           "WHERE a.id = :id AND a.status = :draft")
    int finalizeIfDraft(@Param("id") Long id, @Param("now") LocalDateTime now,
                        @Param("draft") Assessment.Status draft, @Param("finalized") Assessment.Status finalized);
}

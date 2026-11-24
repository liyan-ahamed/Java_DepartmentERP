package com.java.erp.modules.academic.repository;

import com.java.erp.modules.academic.entity.SubjectOffering;
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

public interface SubjectOfferingRepository extends JpaRepository<SubjectOffering, Long>,
        JpaSpecificationExecutor<SubjectOffering> {

    @Override
    @EntityGraph(attributePaths = {"subject", "batch", "section", "staff"})
    Page<SubjectOffering> findAll(Specification<SubjectOffering> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"subject", "batch", "section", "staff"})
    List<SubjectOffering> findAll(Specification<SubjectOffering> spec);

    @EntityGraph(attributePaths = {"subject", "batch", "section", "staff"})
    Optional<SubjectOffering> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"subject", "batch", "section", "staff"})
    List<SubjectOffering> findBySectionIdOrderByAcademicYearDescSemesterDesc(Long sectionId);

    @EntityGraph(attributePaths = {"subject", "batch", "section", "staff"})
    List<SubjectOffering> findByIdIn(Collection<Long> ids);

    /** Another active offering of the same subject for the same section and term (excludeId = the offering being edited, or 0). */
    boolean existsBySubjectIdAndSectionIdAndAcademicYearAndSemesterAndActiveTrueAndIdNot(
            Long subjectId, Long sectionId, String academicYear, Integer semester, Long excludeId);

    boolean existsBySubjectId(Long subjectId);

    /** The active offering of a subject for one section and term, if any (dev curriculum seeding). */
    @EntityGraph(attributePaths = {"subject", "batch", "section", "staff"})
    Optional<SubjectOffering> findFirstBySubjectIdAndSectionIdAndAcademicYearAndSemesterAndActiveTrue(
            Long subjectId, Long sectionId, String academicYear, Integer semester);

    @EntityGraph(attributePaths = {"subject", "batch", "section", "staff"})
    List<SubjectOffering> findBySubjectCodeInAndBatchIdAndActiveTrue(Collection<String> codes, Long batchId);

    /** Sections the staff member currently teaches (active offerings). Portfolio review scope. */
    @Query("SELECT DISTINCT o.section.id FROM SubjectOffering o WHERE o.staff.id = :staffId AND o.active = true")
    List<Long> findActiveSectionIdsByStaffId(@Param("staffId") Long staffId);

    /** Active offerings of one term (exam periods, results). */
    @Query("SELECT o.id FROM SubjectOffering o WHERE o.academicYear = :academicYear AND o.semester = :semester AND o.active = true")
    List<Long> findActiveIdsByTerm(@Param("academicYear") String academicYear, @Param("semester") Integer semester);

    /** Which of these subjects have an active offering for the cohort + semester (curriculum validation). */
    @Query("SELECT DISTINCT o.subject.id FROM SubjectOffering o WHERE o.batch.id = :batchId AND o.semester = :semester " +
           "AND o.active = true AND o.subject.id IN :subjectIds")
    List<Long> findOfferedSubjectIds(@Param("batchId") Long batchId, @Param("semester") Integer semester,
                                     @Param("subjectIds") Collection<Long> subjectIds);

    /** Number of distinct subjects with an active offering for the cohort + semester. */
    @Query("SELECT COUNT(DISTINCT o.subject.id) FROM SubjectOffering o WHERE o.batch.id = :batchId AND o.semester = :semester " +
           "AND o.active = true")
    long countOfferedSubjects(@Param("batchId") Long batchId, @Param("semester") Integer semester);

    /** Number of distinct subjects with an active offering for the section + semester. */
    @Query("SELECT COUNT(DISTINCT o.subject.id) FROM SubjectOffering o WHERE o.section.id = :sectionId AND o.semester = :semester " +
           "AND o.active = true")
    long countOfferedSubjectsForSection(@Param("sectionId") Long sectionId, @Param("semester") Integer semester);

    /** Active offerings of one section in one semester (registration detail: where a missing subject can be taken). */
    @EntityGraph(attributePaths = {"subject", "batch", "section", "staff"})
    List<SubjectOffering> findBySectionIdAndSemesterAndActiveTrue(Long sectionId, Integer semester);

    /** Distinct (subject, batch, semester) of active offerings of these subjects ("offered in" on subject responses). */
    @Query("SELECT DISTINCT o.subject.id AS subjectId, b.id AS batchId, b.batchName AS batchName, o.semester AS semester " +
           "FROM SubjectOffering o JOIN o.batch b WHERE o.active = true AND o.subject.id IN :subjectIds")
    List<SubjectTerm> findActiveTermsBySubjectIds(@Param("subjectIds") Collection<Long> subjectIds);

    interface SubjectTerm {
        Long getSubjectId();
        Long getBatchId();
        String getBatchName();
        Integer getSemester();
    }

    /** Active offerings assigned to the staff member (timetable / attendance "my classes"). */
    @Query("SELECT o.id FROM SubjectOffering o WHERE o.staff.id = :staffId AND o.active = true")
    List<Long> findActiveIdsByStaffId(@Param("staffId") Long staffId);
}

package com.java.erp.modules.system.repository;

import com.java.erp.modules.system.entity.Student;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Student entity, shared by the dashboard, schedule and LeetCode modules.
 */
@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {

    List<Student> findByActiveTrueOrderByNameAsc();

    List<Student> findBySectionIdAndActiveTrueOrderByNameAsc(Long sectionId);

    /** Class list of a section in register-number order (gradebook, analytics, internal marks). */
    List<Student> findBySectionIdAndActiveTrueOrderByRegisterNumberAsc(Long sectionId);

    List<Student> findDistinctBySectionIdInAndActiveTrue(Collection<Long> sectionIds);

    List<Student> findByBatchId(Long batchId);

    boolean existsByRegisterNumber(String registerNumber);

    Optional<Student> findByUserIdAndActiveTrue(Long userId);

    /** Number of active students per section (gradebook class-list sizes). */
    @Query("SELECT s.section.id AS sectionId, COUNT(s) AS total FROM Student s " +
           "WHERE s.active = true AND s.section.id IN :sectionIds GROUP BY s.section.id")
    List<SectionCount> countActiveBySections(@Param("sectionIds") Collection<Long> sectionIds);

    interface SectionCount {
        Long getSectionId();
        Long getTotal();
    }

    /** Active students of the active cohort(s) currently in the given year of study, by register number. */
    @Query("SELECT s FROM Student s WHERE s.active = true AND s.batch.active = true AND s.batch.currentYear = :year " +
           "ORDER BY s.registerNumber")
    List<Student> findActiveInActiveBatchOfYear(@Param("year") short year);

    /** Active students linked to these logins, with batch and section (user directory). */
    @EntityGraph(attributePaths = {"user", "batch", "section"})
    List<Student> findByUserIdInAndActiveTrue(Collection<Long> userIds);

    Optional<Student> findFirstByEmailIgnoreCaseAndActiveTrue(String email);

    /** Active students of a cohort with batch and section, by register number (registration status). */
    @EntityGraph(attributePaths = {"batch", "section"})
    List<Student> findByBatchIdAndActiveTrueOrderByRegisterNumberAscIdAsc(Long batchId);

    @EntityGraph(attributePaths = {"batch", "section"})
    List<Student> findByBatchIdAndSectionIdAndActiveTrueOrderByRegisterNumberAscIdAsc(Long batchId, Long sectionId);

    @EntityGraph(attributePaths = {"batch", "section"})
    Optional<Student> findWithClassById(Long id);

    /**
     * One page of a section's students (active or inactive) whose name or register number matches the pattern
     * (lower-case LIKE; "%" = everyone). Sort comes from the Pageable.
     */
    @EntityGraph(attributePaths = {"batch", "section"})
    @Query(value = "SELECT s FROM Student s WHERE s.section.id = :sectionId AND s.active = :active " +
                   "AND (LOWER(s.name) LIKE :pattern OR LOWER(s.registerNumber) LIKE :pattern)",
           countQuery = "SELECT COUNT(s) FROM Student s WHERE s.section.id = :sectionId AND s.active = :active " +
                        "AND (LOWER(s.name) LIKE :pattern OR LOWER(s.registerNumber) LIKE :pattern)")
    Page<Student> findSectionRoster(@Param("sectionId") Long sectionId, @Param("active") boolean active,
                                    @Param("pattern") String pattern, Pageable pageable);

    /**
     * The active student record belonging to a STUDENT login: linked by students.user_id,
     * falling back to a matching email for records that are not linked yet.
     */
    default Optional<Student> findLinkedStudent(Long userId, String email) {
        return findByUserIdAndActiveTrue(userId)
                .or(() -> email == null ? Optional.empty() : findFirstByEmailIgnoreCaseAndActiveTrue(email));
    }

    /**
     * Count active students in active batches (same scope as the year/section breakdowns).
     */
    @Query("SELECT COUNT(s) FROM Student s JOIN s.batch b WHERE s.active = true AND b.active = true")
    long countActiveInActiveBatches();

    /**
     * Count students grouped by academic year (batch current_year).
     */
    @Query("SELECT b.currentYear AS year, COUNT(s) AS studentCount " +
           "FROM Student s JOIN s.batch b " +
           "WHERE s.active = true AND b.active = true " +
           "GROUP BY b.currentYear ORDER BY b.currentYear")
    List<YearWiseCount> countStudentsByYear();

    /**
     * Count students grouped by section.
     */
    @Query("SELECT sec.id AS sectionId, sec.sectionName AS sectionName, " +
           "b.batchName AS batchName, COUNT(s) AS studentCount " +
           "FROM Student s JOIN s.section sec JOIN s.batch b " +
           "WHERE s.active = true AND b.active = true " +
           "GROUP BY sec.id, sec.sectionName, b.batchName " +
           "ORDER BY b.batchName, sec.sectionName")
    List<SectionWiseCount> countStudentsBySection();

    /**
     * Active students of one academic year ranked by their stored LeetCode statistics:
     * total solved, then hard, then medium, then name. Students never synced are excluded.
     */
    @Query(value = "SELECT s FROM Student s JOIN FETCH s.section sec JOIN sec.batch b " +
                   "WHERE s.active = true AND b.active = true AND b.currentYear = :year " +
                   "AND s.leetcodeTotalSolved IS NOT NULL " +
                   "ORDER BY s.leetcodeTotalSolved DESC, s.leetcodeHardSolved DESC, " +
                   "s.leetcodeMediumSolved DESC, s.name ASC",
           countQuery = "SELECT COUNT(s) FROM Student s JOIN s.section sec JOIN sec.batch b " +
                        "WHERE s.active = true AND b.active = true AND b.currentYear = :year " +
                        "AND s.leetcodeTotalSolved IS NOT NULL")
    Page<Student> findTopByYear(@Param("year") short year, Pageable pageable);

    /**
     * Writes freshly fetched LeetCode statistics for one student. Only called after a
     * successful LeetCode lookup, so failed syncs never overwrite stored values.
     */
    @Modifying
    @Query("UPDATE Student s SET s.leetcodeTotalSolved = :total, s.leetcodeEasySolved = :easy, " +
           "s.leetcodeMediumSolved = :medium, s.leetcodeHardSolved = :hard, s.leetcodeLastSyncedAt = :syncedAt " +
           "WHERE s.id = :id")
    int updateLeetCodeStats(@Param("id") Long id,
                            @Param("total") int total,
                            @Param("easy") int easy,
                            @Param("medium") int medium,
                            @Param("hard") int hard,
                            @Param("syncedAt") LocalDateTime syncedAt);

    interface YearWiseCount {
        Short getYear();
        Long getStudentCount();
    }

    interface SectionWiseCount {
        Long getSectionId();
        String getSectionName();
        String getBatchName();
        Long getStudentCount();
    }
}

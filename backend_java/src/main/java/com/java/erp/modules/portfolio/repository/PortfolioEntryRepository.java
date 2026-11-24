package com.java.erp.modules.portfolio.repository;

import com.java.erp.modules.portfolio.entity.PortfolioEntry;
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

/**
 * Portfolio entries plus the aggregate counts behind the summaries.
 * Summary queries take a section scope: allSections=true for the HOD (sectionIds is then ignored
 * but must not be empty), otherwise only students of the given sections are counted.
 */
public interface PortfolioEntryRepository extends JpaRepository<PortfolioEntry, Long>,
        JpaSpecificationExecutor<PortfolioEntry> {

    @Override
    @EntityGraph(attributePaths = {"student", "student.batch", "student.section", "reviewedBy"})
    Page<PortfolioEntry> findAll(Specification<PortfolioEntry> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"student", "student.batch", "student.section", "reviewedBy"})
    Optional<PortfolioEntry> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"student", "student.batch", "student.section", "reviewedBy"})
    List<PortfolioEntry> findByStudentIdAndStatusOrderByStartDateDescIdDesc(Long studentId, PortfolioEntry.Status status);

    // ---- One student's own counts ----

    @Query("SELECT e.status AS status, COUNT(e) AS total FROM PortfolioEntry e " +
           "WHERE e.student.id = :studentId GROUP BY e.status")
    List<StatusCount> countByStatusForStudent(@Param("studentId") Long studentId);

    @Query("SELECT e.activityType AS activityType, COUNT(e) AS total FROM PortfolioEntry e " +
           "WHERE e.student.id = :studentId GROUP BY e.activityType")
    List<ActivityTypeCount> countByActivityTypeForStudent(@Param("studentId") Long studentId);

    // ---- Department / staff-scope counts (drafts are private and never counted) ----

    @Query("SELECT e.status AS status, COUNT(e) AS total FROM PortfolioEntry e JOIN e.student s " +
           "WHERE e.status <> :draft AND (:allSections = true OR s.section.id IN :sectionIds) " +
           "GROUP BY e.status")
    List<StatusCount> countByStatus(@Param("draft") PortfolioEntry.Status draft,
                                    @Param("allSections") boolean allSections,
                                    @Param("sectionIds") Collection<Long> sectionIds);

    @Query("SELECT e.activityType AS activityType, COUNT(e) AS total FROM PortfolioEntry e JOIN e.student s " +
           "WHERE e.status = :status AND (:allSections = true OR s.section.id IN :sectionIds) " +
           "GROUP BY e.activityType")
    List<ActivityTypeCount> countByActivityType(@Param("status") PortfolioEntry.Status status,
                                                @Param("allSections") boolean allSections,
                                                @Param("sectionIds") Collection<Long> sectionIds);

    @Query("SELECT e.level AS level, COUNT(e) AS total FROM PortfolioEntry e JOIN e.student s " +
           "WHERE e.status = :status AND e.level IS NOT NULL " +
           "AND (:allSections = true OR s.section.id IN :sectionIds) GROUP BY e.level")
    List<LevelCount> countByLevel(@Param("status") PortfolioEntry.Status status,
                                  @Param("allSections") boolean allSections,
                                  @Param("sectionIds") Collection<Long> sectionIds);

    @Query("SELECT b.id AS batchId, b.batchName AS batchName, b.currentYear AS currentYear, COUNT(e) AS total " +
           "FROM PortfolioEntry e JOIN e.student s JOIN s.batch b " +
           "WHERE e.status = :status AND (:allSections = true OR s.section.id IN :sectionIds) " +
           "GROUP BY b.id, b.batchName, b.currentYear ORDER BY b.currentYear, b.batchName")
    List<BatchCount> countByBatch(@Param("status") PortfolioEntry.Status status,
                                  @Param("allSections") boolean allSections,
                                  @Param("sectionIds") Collection<Long> sectionIds);

    @Query("SELECT sec.id AS sectionId, sec.sectionName AS sectionName, b.id AS batchId, b.batchName AS batchName, " +
           "COUNT(e) AS total FROM PortfolioEntry e JOIN e.student s JOIN s.section sec JOIN sec.batch b " +
           "WHERE e.status = :status AND (:allSections = true OR sec.id IN :sectionIds) " +
           "GROUP BY sec.id, sec.sectionName, b.id, b.batchName ORDER BY b.batchName, sec.sectionName")
    List<SectionCount> countBySection(@Param("status") PortfolioEntry.Status status,
                                      @Param("allSections") boolean allSections,
                                      @Param("sectionIds") Collection<Long> sectionIds);

    @Query("SELECT COUNT(DISTINCT s.id) FROM PortfolioEntry e JOIN e.student s " +
           "WHERE e.status = :status AND (:allSections = true OR s.section.id IN :sectionIds)")
    long countStudents(@Param("status") PortfolioEntry.Status status,
                       @Param("allSections") boolean allSections,
                       @Param("sectionIds") Collection<Long> sectionIds);

    interface StatusCount {
        PortfolioEntry.Status getStatus();
        Long getTotal();
    }

    interface ActivityTypeCount {
        PortfolioEntry.ActivityType getActivityType();
        Long getTotal();
    }

    interface LevelCount {
        PortfolioEntry.Level getLevel();
        Long getTotal();
    }

    interface BatchCount {
        Long getBatchId();
        String getBatchName();
        Short getCurrentYear();
        Long getTotal();
    }

    interface SectionCount {
        Long getSectionId();
        String getSectionName();
        Long getBatchId();
        String getBatchName();
        Long getTotal();
    }
}

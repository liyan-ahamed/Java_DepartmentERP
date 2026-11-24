package com.java.erp.modules.fees.repository;

import com.java.erp.modules.fees.entity.StudentFeeAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface StudentFeeAccountRepository extends JpaRepository<StudentFeeAccount, Long>,
        JpaSpecificationExecutor<StudentFeeAccount> {

    @Override
    @EntityGraph(attributePaths = {"student", "student.section", "feeStructure"})
    Page<StudentFeeAccount> findAll(Specification<StudentFeeAccount> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"student", "student.section", "feeStructure"})
    Optional<StudentFeeAccount> findWithDetailsById(Long id);

    /** Row lock for recording a payment or waiver, so two concurrent payments cannot overpay. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM StudentFeeAccount a WHERE a.id = :id")
    Optional<StudentFeeAccount> findForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {"student", "student.section", "feeStructure"})
    List<StudentFeeAccount> findByStudentIdOrderByDueDateAscIdAsc(Long studentId);

    boolean existsByFeeStructureId(Long feeStructureId);

    long countByFeeStructureId(Long feeStructureId);

    @Query("SELECT a.student.id FROM StudentFeeAccount a WHERE a.feeStructure.id = :structureId")
    Set<Long> findStudentIdsByFeeStructureId(@Param("structureId") Long structureId);

    @Query("SELECT a.status AS status, COUNT(a) AS accounts, SUM(a.amount) AS charged, SUM(a.paidAmount) AS paid " +
           "FROM StudentFeeAccount a GROUP BY a.status")
    List<StatusTotals> totalsByStatus();

    @Query("SELECT COALESCE(SUM(a.amount - a.paidAmount), 0) FROM StudentFeeAccount a " +
           "WHERE a.status IN (com.java.erp.modules.fees.entity.StudentFeeAccount.Status.UNPAID, " +
           "com.java.erp.modules.fees.entity.StudentFeeAccount.Status.PARTIALLY_PAID) AND a.dueDate < :today")
    BigDecimal overdueOutstanding(@Param("today") LocalDate today);

    interface StatusTotals {
        StudentFeeAccount.Status getStatus();
        Long getAccounts();
        BigDecimal getCharged();
        BigDecimal getPaid();
    }
}

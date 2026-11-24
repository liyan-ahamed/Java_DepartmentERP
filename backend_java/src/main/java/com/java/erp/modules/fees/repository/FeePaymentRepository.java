package com.java.erp.modules.fees.repository;

import com.java.erp.modules.fees.entity.FeePayment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface FeePaymentRepository extends JpaRepository<FeePayment, Long>, JpaSpecificationExecutor<FeePayment> {

    @Override
    @EntityGraph(attributePaths = {"account", "account.student", "account.student.section", "account.feeStructure", "recordedBy"})
    Page<FeePayment> findAll(Specification<FeePayment> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"account", "account.student", "account.student.section", "account.feeStructure", "recordedBy"})
    Optional<FeePayment> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"account", "account.student", "account.student.section", "account.feeStructure", "recordedBy"})
    List<FeePayment> findByAccountIdOrderByPaymentDateAscIdAsc(Long accountId);

    @EntityGraph(attributePaths = {"account", "account.student", "account.student.section", "account.feeStructure", "recordedBy"})
    List<FeePayment> findByAccountStudentIdOrderByPaymentDateDescIdDesc(Long studentId);

    @Query(value = "SELECT nextval('fee_receipt_number_seq')", nativeQuery = true)
    long nextReceiptSequence();
}

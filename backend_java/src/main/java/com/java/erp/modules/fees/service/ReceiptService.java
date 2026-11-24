package com.java.erp.modules.fees.service;

import com.java.erp.common.response.PagedResponse;
import com.java.erp.common.util.SecurityUtils;
import com.java.erp.exception.ForbiddenException;
import com.java.erp.exception.ResourceNotFoundException;
import com.java.erp.modules.fees.dto.response.ReceiptResponse;
import com.java.erp.modules.fees.entity.FeePayment;
import com.java.erp.modules.fees.repository.FeePaymentRepository;
import com.java.erp.modules.gradebook.service.CurrentStudentResolver;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Receipts are fee payments viewed as receipts: no separate records. */
@Service
public class ReceiptService {

    private final FeePaymentRepository payments;
    private final CurrentStudentResolver currentStudent;

    public ReceiptService(FeePaymentRepository payments, CurrentStudentResolver currentStudent) {
        this.payments = payments;
        this.currentStudent = currentStudent;
    }

    @Transactional(readOnly = true)
    public List<ReceiptResponse> myReceipts() {
        Long studentId = currentStudent.require().getId();
        return payments.findByAccountStudentIdOrderByPaymentDateDescIdDesc(studentId).stream().map(ReceiptResponse::from).toList();
    }

    /** HOD: any receipt. STUDENT: own receipts only (403 otherwise). */
    @Transactional(readOnly = true)
    public ReceiptResponse get(Long id) {
        FeePayment payment = payments.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt", "id", id));
        if (!SecurityUtils.hasAuthority("ROLE_HOD")
                && !payment.getAccount().getStudent().getId().equals(currentStudent.require().getId())) {
            throw new ForbiddenException("You can only view your own receipts");
        }
        return ReceiptResponse.from(payment);
    }

    @Transactional(readOnly = true)
    public PagedResponse<ReceiptResponse> search(String receiptNumber, Long studentId, Long sectionId, Long feeStructureId,
                                                 FeePayment.PaymentMethod method, LocalDate from, LocalDate to,
                                                 int page, int size) {
        Specification<FeePayment> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (receiptNumber != null && !receiptNumber.isBlank()) {
                p.add(cb.like(cb.upper(root.get("receiptNumber")), "%" + receiptNumber.trim().toUpperCase(Locale.ROOT) + "%"));
            }
            if (studentId != null) p.add(cb.equal(root.get("account").get("student").get("id"), studentId));
            if (sectionId != null) p.add(cb.equal(root.get("account").get("student").get("section").get("id"), sectionId));
            if (feeStructureId != null) p.add(cb.equal(root.get("account").get("feeStructure").get("id"), feeStructureId));
            if (method != null) p.add(cb.equal(root.get("paymentMethod"), method));
            if (from != null) p.add(cb.greaterThanOrEqualTo(root.get("paymentDate"), from));
            if (to != null) p.add(cb.lessThanOrEqualTo(root.get("paymentDate"), to));
            return cb.and(p.toArray(Predicate[]::new));
        };
        Page<FeePayment> result = payments.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("paymentDate"), Sort.Order.desc("id"))));
        return PagedResponse.of(result.getContent().stream().map(ReceiptResponse::from).toList(), result);
    }
}

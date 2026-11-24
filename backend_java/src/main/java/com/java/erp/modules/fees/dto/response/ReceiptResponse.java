package com.java.erp.modules.fees.dto.response;

import com.java.erp.modules.fees.entity.FeePayment;
import com.java.erp.modules.fees.entity.FeeStructure;
import com.java.erp.modules.fees.entity.StudentFeeAccount;
import com.java.erp.modules.system.entity.Student;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** A receipt = one recorded fee payment (enough data to print one later). */
public record ReceiptResponse(
        Long id,
        String receiptNumber,
        Long studentId,
        String registerNumber,
        String studentName,
        String sectionName,
        Long feeAccountId,
        Long feeStructureId,
        String feeName,
        FeeStructure.FeeType feeType,
        String academicYear,
        Integer semester,
        BigDecimal chargeAmount,
        BigDecimal amount,
        LocalDate paymentDate,
        FeePayment.PaymentMethod paymentMethod,
        String reference,
        String remarks,
        String recordedByName,
        LocalDateTime createdAt
) {
    public static ReceiptResponse from(FeePayment p) {
        StudentFeeAccount a = p.getAccount();
        Student s = a.getStudent();
        FeeStructure f = a.getFeeStructure();
        return new ReceiptResponse(p.getId(), p.getReceiptNumber(), s.getId(), s.getRegisterNumber(), s.getName(),
                s.getSection().getSectionName(), a.getId(), f.getId(), f.getName(), f.getFeeType(), f.getAcademicYear(),
                f.getSemester(), a.getAmount(), p.getAmount(), p.getPaymentDate(), p.getPaymentMethod(), p.getReference(),
                p.getRemarks(), p.getRecordedBy() == null ? null : p.getRecordedBy().getName(), p.getCreatedAt());
    }
}

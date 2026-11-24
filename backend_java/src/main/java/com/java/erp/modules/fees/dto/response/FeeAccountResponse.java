package com.java.erp.modules.fees.dto.response;

import com.java.erp.modules.fees.entity.FeeStructure;
import com.java.erp.modules.fees.entity.StudentFeeAccount;
import com.java.erp.modules.system.entity.Student;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One fee charge (one particular). amount = actual amount; payableAmount = amount - concessionAmount;
 * balance = payableAmount - paidAmount (0 when WAIVED); overdue = balance > 0 and past the due date.
 */
public record FeeAccountResponse(
        Long id,
        Long studentId,
        String registerNumber,
        String studentName,
        String sectionName,
        Long feeStructureId,
        String feeName,
        FeeStructure.FeeType feeType,
        String academicYear,
        Integer semester,
        BigDecimal amount,
        BigDecimal paidAmount,
        BigDecimal balance,
        LocalDate dueDate,
        boolean overdue,
        StudentFeeAccount.Status status,
        String waiverReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        BigDecimal concessionAmount,
        BigDecimal payableAmount,
        Short studentYear
) {
    public static FeeAccountResponse from(StudentFeeAccount a) {
        Student s = a.getStudent();
        FeeStructure f = a.getFeeStructure();
        BigDecimal balance = a.balance();
        boolean overdue = balance.signum() > 0 && a.getDueDate() != null && a.getDueDate().isBefore(LocalDate.now());
        return new FeeAccountResponse(a.getId(), s.getId(), s.getRegisterNumber(), s.getName(),
                s.getSection().getSectionName(), f.getId(), f.getName(), f.getFeeType(), f.getAcademicYear(),
                f.getSemester(), a.getAmount(), a.getPaidAmount(), balance, a.getDueDate(), overdue, a.getStatus(),
                a.getWaiverReason(), a.getCreatedAt(), a.getUpdatedAt(), a.getConcessionAmount(), a.payable(),
                f.getStudentYear());
    }
}

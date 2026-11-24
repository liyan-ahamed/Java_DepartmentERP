package com.java.erp.modules.fees.entity;

import com.java.erp.modules.system.entity.Student;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One fee charge of one student. Money is BigDecimal (NUMERIC(12,2)); the status always follows the
 * paid amount: UNPAID (0) -> PARTIALLY_PAID (0 < paid < amount) -> PAID (paid = amount).
 * WAIVED closes the remaining balance without a payment.
 */
@Entity
@Table(name = "student_fee_accounts")
public class StudentFeeAccount {

    public enum Status { UNPAID, PARTIALLY_PAID, PAID, WAIVED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fee_structure_id", nullable = false, updatable = false)
    private FeeStructure feeStructure;

    /** Actual amount (copied from the fee structure). */
    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** 0 <= concession <= amount; payable = amount - concession. */
    @Column(name = "concession_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal concessionAmount = BigDecimal.ZERO.setScale(2);

    @Column(name = "paid_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO.setScale(2);

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.UNPAID;

    @Column(name = "waiver_reason", length = 500)
    private String waiverReason;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public StudentFeeAccount() {
    }

    public StudentFeeAccount(Student student, FeeStructure feeStructure, LocalDate dueDate) {
        this.student = student;
        this.feeStructure = feeStructure;
        this.amount = feeStructure.getAmount();
        this.dueDate = dueDate;
    }

    /** What the student has to pay: amount - concession. */
    public BigDecimal payable() {
        return amount.subtract(concessionAmount);
    }

    /** What is still owed: payable - paid; 0 when PAID or WAIVED. */
    public BigDecimal balance() {
        return status == Status.WAIVED ? BigDecimal.ZERO.setScale(2) : payable().subtract(paidAmount);
    }

    /** Adds a payment (already validated to be > 0 and <= balance) and updates the status. */
    public void applyPayment(BigDecimal payment) {
        paidAmount = paidAmount.add(payment);
        status = paidAmount.compareTo(payable()) == 0 ? Status.PAID : Status.PARTIALLY_PAID;
    }

    /**
     * Sets the concession (caller validated 0 <= concession <= amount and paid <= amount - concession) and
     * re-derives the status from what is now payable.
     */
    public void applyConcession(BigDecimal concession) {
        concessionAmount = concession;
        BigDecimal payable = payable();
        if (paidAmount.compareTo(payable) == 0) status = Status.PAID;
        else status = paidAmount.signum() == 0 ? Status.UNPAID : Status.PARTIALLY_PAID;
    }

    public void waive(String reason) {
        status = Status.WAIVED;
        waiverReason = reason;
    }

    public Long getId() { return id; }
    public Student getStudent() { return student; }
    public FeeStructure getFeeStructure() { return feeStructure; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getConcessionAmount() { return concessionAmount; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public Status getStatus() { return status; }
    public String getWaiverReason() { return waiverReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

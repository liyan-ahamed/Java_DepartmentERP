package com.java.erp.modules.fees.entity;

import com.java.erp.modules.auth.entity.User;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** A recorded payment against a fee charge. Every payment is a receipt with a unique, stable number. Immutable. */
@Entity
@Table(name = "fee_payments")
public class FeePayment {

    public enum PaymentMethod { CASH, CARD, BANK_TRANSFER, UPI, OTHER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_fee_account_id", nullable = false, updatable = false)
    private StudentFeeAccount account;

    @Column(name = "receipt_number", nullable = false, unique = true, length = 30, updatable = false)
    private String receiptNumber;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "payment_date", nullable = false, updatable = false)
    private LocalDate paymentDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20, updatable = false)
    private PaymentMethod paymentMethod;

    @Column(name = "reference", length = 100, updatable = false)
    private String reference;

    @Column(name = "remarks", length = 255, updatable = false)
    private String remarks;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by", updatable = false)
    private User recordedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public FeePayment() {
    }

    public FeePayment(StudentFeeAccount account, String receiptNumber, BigDecimal amount, LocalDate paymentDate,
                      PaymentMethod paymentMethod, String reference, String remarks, User recordedBy) {
        this.account = account;
        this.receiptNumber = receiptNumber;
        this.amount = amount;
        this.paymentDate = paymentDate;
        this.paymentMethod = paymentMethod;
        this.reference = reference;
        this.remarks = remarks;
        this.recordedBy = recordedBy;
    }

    public Long getId() { return id; }
    public StudentFeeAccount getAccount() { return account; }
    public String getReceiptNumber() { return receiptNumber; }
    public BigDecimal getAmount() { return amount; }
    public LocalDate getPaymentDate() { return paymentDate; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public String getReference() { return reference; }
    public String getRemarks() { return remarks; }
    public User getRecordedBy() { return recordedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}

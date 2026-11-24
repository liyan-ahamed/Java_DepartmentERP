package com.java.erp.modules.fees.service;

import com.java.erp.modules.fees.repository.FeePaymentRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Receipt numbers: RCPT-&lt;payment year&gt;-&lt;6-digit sequence&gt;, e.g. RCPT-2026-000042.
 * The sequence (fee_receipt_number_seq) never repeats, and receipt_number is UNIQUE in the database.
 */
@Component
public class ReceiptNumberGenerator {

    private final FeePaymentRepository payments;

    public ReceiptNumberGenerator(FeePaymentRepository payments) {
        this.payments = payments;
    }

    public String next(LocalDate paymentDate) {
        return String.format("RCPT-%d-%06d", paymentDate.getYear(), payments.nextReceiptSequence());
    }
}

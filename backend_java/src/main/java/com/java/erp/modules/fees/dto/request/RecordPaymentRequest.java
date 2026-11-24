package com.java.erp.modules.fees.dto.request;

import com.java.erp.modules.fees.entity.FeePayment;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** paymentDate defaults to today and cannot be in the future. amount cannot exceed the balance. */
public record RecordPaymentRequest(
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
        LocalDate paymentDate,
        @NotNull FeePayment.PaymentMethod paymentMethod,
        @Size(max = 100) String reference,
        @Size(max = 255) String remarks
) {
}

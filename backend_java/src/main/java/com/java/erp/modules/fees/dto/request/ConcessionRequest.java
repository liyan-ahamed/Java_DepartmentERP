package com.java.erp.modules.fees.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Concession on one charge: 0 <= concessionAmount <= actual amount, and not below what is already paid off. */
public record ConcessionRequest(
        @NotNull @DecimalMin(value = "0.00") @Digits(integer = 10, fraction = 2) BigDecimal concessionAmount,
        @Size(max = 255) String reason
) {
}

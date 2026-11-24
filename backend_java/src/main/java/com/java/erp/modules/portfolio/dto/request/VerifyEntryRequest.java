package com.java.erp.modules.portfolio.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Size;

/** Optional note for the student. The request body itself may be omitted. */
public record VerifyEntryRequest(
        @JsonAlias("verificationNote") @Size(max = 1000) String reviewNote
) {
}

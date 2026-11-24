package com.java.erp.modules.portfolio.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A rejection must tell the student what to change (at least 10 characters, checked in the service). */
public record RejectEntryRequest(
        @JsonAlias("verificationNote") @NotBlank @Size(max = 1000) String reviewNote
) {
}

package com.java.erp.modules.fees.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WaiveFeeRequest(
        @NotBlank @Size(max = 500) String reason
) {
}

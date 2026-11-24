package com.java.erp.modules.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Self-service email change; the current password re-authenticates the user. */
public record ChangeEmailRequest(
        @NotBlank(message = "Current password is required") String currentPassword,
        @NotBlank(message = "New email is required")
        @Email(message = "Email must be valid")
        @Size(max = 100, message = "Email must not exceed 100 characters") String newEmail
) {
}

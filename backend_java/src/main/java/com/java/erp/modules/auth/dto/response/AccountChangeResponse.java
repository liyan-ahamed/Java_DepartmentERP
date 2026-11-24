package com.java.erp.modules.auth.dto.response;

/**
 * Result of a self-service account change. reauthenticationRequired = true: every access and refresh token
 * issued before the change (including the one used for this request) has been revoked; the user must log in again.
 */
public record AccountChangeResponse(
        boolean reauthenticationRequired,
        UserResponse user
) {
}

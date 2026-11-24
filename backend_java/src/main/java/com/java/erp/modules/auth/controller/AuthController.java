package com.java.erp.modules.auth.controller;

import com.java.erp.common.response.ApiResponse;
import com.java.erp.modules.auth.dto.request.ChangeEmailRequest;
import com.java.erp.modules.auth.dto.request.ChangePasswordRequest;
import com.java.erp.modules.auth.dto.request.LoginRequest;
import com.java.erp.modules.auth.dto.request.RefreshTokenRequest;
import com.java.erp.modules.auth.dto.response.AccountChangeResponse;
import com.java.erp.modules.auth.dto.response.AuthResponse;
import com.java.erp.modules.auth.dto.response.UserResponse;
import com.java.erp.modules.auth.service.AccountSettingsService;
import com.java.erp.modules.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for authentication endpoints.
 */
@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "Endpoints for login, token refresh, and current user")
public class AuthController {

    private final AuthService authService;
    private final AccountSettingsService accountSettings;

    public AuthController(AuthService authService, AccountSettingsService accountSettings) {
        this.authService = authService;
        this.accountSettings = accountSettings;
    }

    @PostMapping("/login")
    @Operation(summary = "Login user", description = "Authenticate a user with email and password, returns JWT tokens and user info")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Login successful"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid email or password"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Account deactivated")
    })
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest loginRequest) {
        AuthResponse authResponse = authService.login(loginRequest);
        return ResponseEntity.ok(ApiResponse.success("Login successful", authResponse));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh tokens", description = "Exchange a valid refresh token for new access and refresh tokens")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Token refreshed successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid or expired refresh token")
    })
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse authResponse = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", authResponse));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user", description = "Get the currently authenticated user's profile (requires valid JWT)")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Current user fetched successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser() {
        UserResponse userResponse = authService.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success("Current user fetched successfully", userResponse));
    }

    @PutMapping("/me/email")
    @Operation(summary = "Change my email",
               description = "Needs the current password. 400 invalid input or wrong current password, 409 email in use. "
                       + "All tokens are revoked: log in again with the new email.")
    public ResponseEntity<ApiResponse<AccountChangeResponse>> changeEmail(@Valid @RequestBody ChangeEmailRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Email changed. Please log in again.", accountSettings.changeEmail(request)));
    }

    @PutMapping("/me/password")
    @Operation(summary = "Change my password",
               description = "Needs the current password; new password 8-100 characters. All tokens are revoked: log in again.")
    public ResponseEntity<ApiResponse<AccountChangeResponse>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Password changed. Please log in again.", accountSettings.changePassword(request)));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout user", description = "Revoke all access and refresh tokens issued to the current user")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Logout successful"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ResponseEntity<ApiResponse<Void>> logout() {
        authService.logout();
        return ResponseEntity.ok(ApiResponse.success("Logout successful", null));
    }
}

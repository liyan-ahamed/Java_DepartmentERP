package com.java.erp.modules.user.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import com.java.erp.common.response.ApiResponse;
import com.java.erp.common.response.PagedResponse;
import com.java.erp.modules.user.dto.request.CreateUserRequest;
import com.java.erp.modules.user.dto.request.UpdateUserRequest;
import com.java.erp.modules.user.dto.response.UserDetailResponse;
import com.java.erp.modules.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for user management operations.
 * Exposes endpoints for managing users with RBAC permission checks.
 */
@RestController
@RequestMapping("/users")
@Tag(name = "User Management", description = "Endpoints for comprehensive user management")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "Create a new user",
               description = "Creates a new user and assigns roles. HOD only.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201", description = "User created successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "Invalid request data"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "User with given email/username/staffCode already exists")
    })
    public ResponseEntity<ApiResponse<UserDetailResponse>> createUser(
            @Valid @RequestBody CreateUserRequest request) {
        UserDetailResponse user = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("User created successfully", user));
    }

    @GetMapping
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "Get all users",
               description = "Paginated users. Filters: search (name/email), active, role (HOD | STAFF | STUDENT), and for "
                       + "students batchId / sectionId. HOD only.")
    public ResponseEntity<ApiResponse<PagedResponse<UserDetailResponse>>> getAllUsers(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) Long sectionId) {
        PagedResponse<UserDetailResponse> users = userService.getAllUsers(page, size, search, active, role, batchId, sectionId);
        return ResponseEntity.ok(ApiResponse.success("Users fetched successfully", users));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "Get user details",
               description = "Returns detailed information about a specific user. HOD only.")
    public ResponseEntity<ApiResponse<UserDetailResponse>> getUser(@PathVariable Long id) {
        UserDetailResponse user = userService.getUser(id);
        return ResponseEntity.ok(ApiResponse.success("User fetched successfully", user));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "Update an existing user",
               description = "Updates user fields. HOD only.")
    public ResponseEntity<ApiResponse<UserDetailResponse>> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request) {
        UserDetailResponse user = userService.updateUser(id, request);
        return ResponseEntity.ok(ApiResponse.success("User updated successfully", user));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "Deactivate a user",
               description = "Soft deletes/deactivates a user account. HOD only.")
    public ResponseEntity<ApiResponse<UserDetailResponse>> deactivateUser(@PathVariable Long id) {
        UserDetailResponse user = userService.deactivateUser(id);
        return ResponseEntity.ok(ApiResponse.success("User deactivated successfully", user));
    }

    @PatchMapping("/{id}/reactivate")
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "Reactivate a user",
               description = "Restores a deactivated user account. HOD only.")
    public ResponseEntity<ApiResponse<UserDetailResponse>> reactivateUser(@PathVariable Long id) {
        UserDetailResponse user = userService.reactivateUser(id);
        return ResponseEntity.ok(ApiResponse.success("User reactivated successfully", user));
    }
}

package com.java.erp.modules.auth.service;

import com.java.erp.common.mapper.UserMapper;
import com.java.erp.common.util.SecurityUtils;
import com.java.erp.exception.AccountDeactivatedException;
import com.java.erp.exception.BadRequestException;
import com.java.erp.exception.ConflictException;
import com.java.erp.exception.UnauthorizedException;
import com.java.erp.modules.audit.service.AuditRecorder;
import com.java.erp.modules.auth.dto.request.ChangeEmailRequest;
import com.java.erp.modules.auth.dto.request.ChangePasswordRequest;
import com.java.erp.modules.auth.dto.response.AccountChangeResponse;
import com.java.erp.modules.auth.entity.User;
import com.java.erp.modules.auth.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Self-service account settings of the logged-in user (never another user; no id is accepted).
 * Both changes need the current password and revoke every issued token (users.token_version + 1),
 * so the user logs in again with the new credentials. Audit rows never contain passwords or hashes.
 */
@Service
public class AccountSettingsService {

    static final String WRONG_PASSWORD = "Current password is incorrect";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuditRecorder audit;

    public AccountSettingsService(UserRepository users, PasswordEncoder passwordEncoder, AuditRecorder audit) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    @Transactional
    public AccountChangeResponse changeEmail(ChangeEmailRequest request) {
        User user = reauthenticate(request.currentPassword());
        String newEmail = request.newEmail().trim();
        String oldEmail = user.getEmail();
        if (newEmail.equalsIgnoreCase(oldEmail)) {
            throw new BadRequestException("The new email is the same as the current one");
        }
        if (users.existsByEmailIgnoreCaseAndIdNot(newEmail, user.getId())) {
            throw new ConflictException("User", "email", newEmail);
        }
        user.setEmail(newEmail);
        user.revokeTokens();
        users.saveAndFlush(user);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("userId", user.getId());
        summary.put("oldEmail", oldEmail);
        summary.put("newEmail", newEmail);
        audit.record("SELF_EMAIL_CHANGED", "AUTH", "User", user.getId(), summary);
        return new AccountChangeResponse(true, UserMapper.toUserResponse(user));
    }

    @Transactional
    public AccountChangeResponse changePassword(ChangePasswordRequest request) {
        User user = reauthenticate(request.currentPassword());
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new BadRequestException("The new password must be different from the current one");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.revokeTokens();
        users.saveAndFlush(user);

        audit.record("SELF_PASSWORD_CHANGED", "AUTH", "User", user.getId(), Map.of("userId", user.getId()));
        return new AccountChangeResponse(true, UserMapper.toUserResponse(user));
    }

    private User reauthenticate(String currentPassword) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new UnauthorizedException("User is not authenticated");
        }
        User user = users.findById(userId).orElseThrow(() -> new UnauthorizedException("User not found"));
        if (!user.isActive()) {
            throw new AccountDeactivatedException("Your account has been deactivated.");
        }
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new BadRequestException(WRONG_PASSWORD);
        }
        return user;
    }
}

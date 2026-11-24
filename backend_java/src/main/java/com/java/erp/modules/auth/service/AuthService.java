package com.java.erp.modules.auth.service;

import com.java.erp.common.audit.Auditable;
import com.java.erp.common.mapper.UserMapper;
import com.java.erp.common.util.SecurityUtils;
import com.java.erp.exception.AccountDeactivatedException;
import com.java.erp.exception.UnauthorizedException;
import com.java.erp.modules.auth.dto.request.LoginRequest;
import com.java.erp.modules.auth.dto.request.RefreshTokenRequest;
import com.java.erp.modules.auth.dto.response.AuthResponse;
import com.java.erp.modules.auth.dto.response.UserResponse;
import com.java.erp.modules.auth.entity.User;
import com.java.erp.modules.auth.repository.UserRepository;
import com.java.erp.security.CustomUserDetails;
import com.java.erp.security.JwtService;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service handling authentication operations: login, token refresh, logout.
 * User creation lives in UserService (POST /users).
 */
@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    public AuthService(AuthenticationManager authenticationManager,
                       UserRepository userRepository,
                       JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    /**
     * Authenticate a user by email and return JWT tokens with user info.
     */
    @Auditable(action = "LOGIN", module = "AUTH", entityType = "User")
    public AuthResponse login(LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getEmail(),
                        loginRequest.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        String accessToken = jwtService.generateAccessToken(userDetails);
        String refreshToken = jwtService.generateRefreshToken(userDetails);

        // Load full user entity for the response
        User user = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        logger.info("User '{}' logged in successfully", loginRequest.getEmail());

        return new AuthResponse(accessToken, refreshToken, UserMapper.toUserResponse(user));
    }

    /**
     * Refresh JWT tokens using a valid, non-revoked refresh token.
     */
    @Transactional(readOnly = true)
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        Claims claims = jwtService.parse(request.getRefreshToken())
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired refresh token."));

        User user = userRepository.findByEmail(claims.getSubject())
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired refresh token."));

        if (!user.isActive()) {
            throw new AccountDeactivatedException("Your account has been deactivated.");
        }

        CustomUserDetails userDetails = new CustomUserDetails(user);
        if (!JwtService.isCurrent(claims, JwtService.REFRESH, userDetails)) {
            throw new UnauthorizedException("Invalid or expired refresh token.");
        }

        logger.info("Tokens refreshed for user '{}'", user.getEmail());

        return new AuthResponse(
                jwtService.generateAccessToken(userDetails),
                jwtService.generateRefreshToken(userDetails));
    }

    /**
     * Retrieve the currently authenticated user's information.
     */
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new UnauthorizedException("User is not authenticated");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        return UserMapper.toUserResponse(user);
    }

    /**
     * Logout the currently authenticated user by revoking every access and
     * refresh token issued to them (on all devices).
     */
    @Transactional
    @Auditable(action = "LOGOUT", module = "AUTH", entityType = "User")
    public void logout() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new UnauthorizedException("User is not authenticated");
        }
        userRepository.findById(userId).ifPresent(user -> {
            user.revokeTokens();
            logger.info("User '{}' logged out; issued tokens revoked", user.getEmail());
        });
    }
}

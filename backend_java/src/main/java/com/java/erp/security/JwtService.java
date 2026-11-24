package com.java.erp.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for JWT token generation and parsing.
 * Every token carries the user's token version ("ver"); a token is only honoured
 * while that version still matches users.token_version.
 */
@Component
public class JwtService {

    public static final String ACCESS = "ACCESS";
    public static final String REFRESH = "REFRESH";

    /** HS512 requires a key of at least 512 bits. */
    static final int MIN_SECRET_BYTES = 64;

    private static final Logger logger = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey key;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;
    private final String issuer;

    public JwtService(
            @Value("${app.security.jwt.secret}") String jwtSecret,
            @Value("${app.security.jwt.expiration-ms}") long accessTokenExpirationMs,
            @Value("${app.security.jwt.refresh-expiration-ms}") long refreshTokenExpirationMs,
            @Value("${app.security.jwt.issuer}") String issuer) {
        this.key = Keys.hmacShaKeyFor(requireStrongSecret(jwtSecret));
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
        this.issuer = issuer;
    }

    /**
     * Fail fast on a missing or weak signing secret. The secret value itself is
     * never included in the error message or logs.
     */
    static byte[] requireStrongSecret(String jwtSecret) {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET is not set. Configure a random secret of at least "
                    + MIN_SECRET_BYTES + " bytes in the environment or .env file.");
        }
        byte[] bytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET is too short for HS512: it must be at least " + MIN_SECRET_BYTES + " bytes.");
        }
        return bytes;
    }

    public String generateAccessToken(CustomUserDetails userDetails) {
        return generateToken(userDetails, accessTokenExpirationMs, ACCESS);
    }

    public String generateRefreshToken(CustomUserDetails userDetails) {
        return generateToken(userDetails, refreshTokenExpirationMs, REFRESH);
    }

    private String generateToken(CustomUserDetails userDetails, long expirationMs, String tokenType) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        String roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        return Jwts.builder()
                .subject(userDetails.getUsername())
                .issuer(issuer)
                .claim("userId", userDetails.getId())
                .claim("name", userDetails.getName())
                .claim("roles", roles)
                .claim("tokenType", tokenType)
                .claim("ver", userDetails.getTokenVersion())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    /**
     * Verify the signature and expiry of a token and return its claims,
     * or empty if the token is invalid.
     */
    public Optional<Claims> parse(String token) {
        try {
            return Optional.of(Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload());
        } catch (JwtException | IllegalArgumentException ex) {
            logger.debug("Rejected JWT: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * True when the claims are of the expected type and were issued for the
     * user's current token version.
     */
    public static boolean isCurrent(Claims claims, String expectedType, CustomUserDetails user) {
        Integer version = claims.get("ver", Integer.class);
        return expectedType.equals(claims.get("tokenType", String.class))
                && version != null
                && version == user.getTokenVersion();
    }
}

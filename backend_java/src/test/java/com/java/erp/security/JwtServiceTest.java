package com.java.erp.security;

import com.java.erp.modules.auth.entity.Role;
import com.java.erp.modules.auth.entity.User;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static String randomSecret(int bytes) {
        byte[] raw = new byte[bytes];
        new SecureRandom().nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private static JwtService service(String secret) {
        return new JwtService(secret, 60_000, 120_000, "test");
    }

    private static CustomUserDetails user() {
        User user = new User("u", "User", "u@test.local", "x");
        user.setId(1L);
        user.addRole(new Role("ROLE_STAFF"));
        return new CustomUserDetails(user);
    }

    @Test
    void missingSecretFailsFast() {
        for (String missing : new String[]{null, "", "   "}) {
            assertThatThrownBy(() -> service(missing))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("JWT_SECRET is not set");
        }
    }

    @Test
    void shortSecretFailsFastWithoutRevealingIt() {
        String weak = "short-but-not-empty-secret-value-32b";

        assertThatThrownBy(() -> service(weak))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("too short")
                .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain(weak));
    }

    @Test
    void strongSecretSignsHs512TokensThatVerifyOnlyWithTheSameSecret() {
        JwtService jwt = service(randomSecret(64));
        String token = jwt.generateAccessToken(user());

        String header = new String(Base64.getUrlDecoder().decode(token.substring(0, token.indexOf('.'))));
        assertThat(header).contains("HS512");
        assertThat(jwt.parse(token)).isPresent();
        assertThat(JwtService.isCurrent(jwt.parse(token).orElseThrow(), JwtService.ACCESS, user())).isTrue();

        assertThat(service(randomSecret(64)).parse(token)).isEmpty();
    }
}

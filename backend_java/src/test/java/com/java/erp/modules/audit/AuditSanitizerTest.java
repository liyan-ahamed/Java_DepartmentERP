package com.java.erp.modules.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.erp.modules.audit.service.AuditSanitizer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditSanitizerTest {

    private static final String JWT = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhQGIuY29tIn0.c2lnbmF0dXJlc2lnbmF0dXJl";

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void redactsSensitiveKeysAtAnyDepth() {
        String json = AuditSanitizer.toSafeJson(mapper, Map.of(
                "email", "a@b.com",
                "password", "plain",
                "nested", Map.of("refreshToken", "r", "Authorization", "Bearer x", "clientSecret", "s"),
                "list", List.of(Map.of("newPassword", "p"))));

        assertThat(json).contains("a@b.com")
                .doesNotContain("plain").doesNotContain("\"r\"").doesNotContain("Bearer x").doesNotContain("\"s\"")
                .doesNotContain("\"p\"");
    }

    @Test
    void redactsTokenAndHashLookingValuesUnderInnocentKeys() {
        String json = AuditSanitizer.toSafeJson(mapper, Map.of(
                "note", JWT,
                "hash", "$2a$12$abcdefghijklmnopqrstuv",
                "items", List.of(JWT)));

        assertThat(json).doesNotContain(JWT).doesNotContain("$2a$12$");
    }

    @Test
    void sanitizesLegacyStoredValuesOnRead() {
        String legacy = "{\"email\":\"a@b.com\",\"password\":\"password123\"}";

        assertThat(AuditSanitizer.sanitizeStored(mapper, legacy))
                .contains("a@b.com").doesNotContain("password123");
        assertThat(AuditSanitizer.sanitizeStored(mapper, JWT)).isEqualTo(AuditSanitizer.REDACTED);
        assertThat(AuditSanitizer.sanitizeStored(mapper, null)).isNull();
    }
}

package com.java.erp.modules.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.java.erp.modules.audit.entity.AuditLog;
import com.java.erp.modules.audit.repository.AuditLogRepository;
import com.java.erp.modules.auth.entity.User;
import com.java.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuditSecurityIntegrationTest extends IntegrationTestBase {

    @Autowired private AuditLogRepository auditLogRepository;

    private List<String> storedPayloads() {
        return auditLogRepository.findAll().stream()
                .flatMap(log -> java.util.stream.Stream.of(log.getOldValue(), log.getNewValue()))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Test
    void loginAuditStoresNoPasswordOrTokens() throws Exception {
        User user = createUser("ROLE_STAFF", true);
        JsonNode tokens = login(user);

        AuditLog loginLog = auditLogRepository.findAll().stream()
                .filter(log -> "LOGIN".equals(log.getAction()) && user.getId().equals(log.getEntityId()))
                .findFirst().orElseThrow();
        assertThat(loginLog.getUser().getId()).isEqualTo(user.getId());
        assertThat(loginLog.getOldValue()).isNull();

        assertThat(storedPayloads()).allSatisfy(payload -> assertThat(payload)
                .doesNotContain(PASSWORD)
                .doesNotContain(tokens.get("accessToken").asText())
                .doesNotContain(tokens.get("refreshToken").asText())
                .doesNotContain(user.getPassword()));
    }

    @Test
    void userCreationAuditStoresNoPassword() throws Exception {
        String rawPassword = "VerySecret@987";
        mvc.perform(post("/users").header("Authorization", bearer(createUser("ROLE_HOD", true)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Audit Person", "username", "audit.person",
                                "email", "audit.person@test.local", "password", rawPassword))))
                .andExpect(status().isCreated());

        User created = userRepository.findByEmail("audit.person@test.local").orElseThrow();
        AuditLog createLog = auditLogRepository.findAll().stream()
                .filter(log -> "CREATE".equals(log.getAction()) && "USER_MANAGEMENT".equals(log.getModule()))
                .findFirst().orElseThrow();
        assertThat(createLog.getEntityId()).isEqualTo(created.getId());

        assertThat(storedPayloads()).allSatisfy(payload -> assertThat(payload)
                .doesNotContain(rawPassword)
                .doesNotContain(created.getPassword())
                .doesNotContain("\"password\""));
    }

    @Test
    void logoutAuditRecordsTheUser() throws Exception {
        User user = createUser("ROLE_STAFF", true);
        mvc.perform(post("/auth/logout").header("Authorization", bearer(user))).andExpect(status().isOk());

        assertThat(auditLogRepository.findAll()).anySatisfy(log -> {
            assertThat(log.getAction()).isEqualTo("LOGOUT");
            assertThat(log.getEntityId()).isEqualTo(user.getId());
        });
    }
}

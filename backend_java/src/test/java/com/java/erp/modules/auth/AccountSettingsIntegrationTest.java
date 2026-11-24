package com.java.erp.modules.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.java.erp.modules.audit.entity.AuditLog;
import com.java.erp.modules.audit.repository.AuditLogRepository;
import com.java.erp.modules.auth.entity.User;
import com.java.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Self-service email / password change (PUT /auth/me/email, PUT /auth/me/password). */
class AccountSettingsIntegrationTest extends IntegrationTestBase {

    private static final String NEW_PASSWORD = "Changed@4567";

    @Autowired private AuditLogRepository auditLogRepository;

    private ResultActions put(String path, String token, Object body) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(path)
                .header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    private ResultActions loginAs(String email, String password) throws Exception {
        return mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "password", password))));
    }

    @Test
    void emailChangeNeedsTheCurrentPasswordAndRevokesTokens() throws Exception {
        User user = createUser("ROLE_STAFF", true);
        String oldEmail = user.getEmail();
        String token = bearer(user);
        String newEmail = "renamed." + user.getUsername() + "@test.local";

        put("/auth/me/email", token, Map.of("currentPassword", PASSWORD, "newEmail", newEmail))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reauthenticationRequired").value(true))
                .andExpect(jsonPath("$.data.user.email").value(newEmail))
                .andExpect(jsonPath("$.data.user.roles[0]").value("ROLE_STAFF"));

        mvc.perform(get("/auth/me").header("Authorization", token)).andExpect(status().isUnauthorized());
        loginAs(oldEmail, PASSWORD).andExpect(status().isUnauthorized());
        JsonNode login = objectMapper.readTree(loginAs(newEmail, PASSWORD).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("data");
        mvc.perform(get("/auth/me").header("Authorization", "Bearer " + login.get("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(newEmail))
                .andExpect(jsonPath("$.data.roles.length()").value(1));

        AuditLog log = auditLogRepository.findAll().stream().filter(l -> "SELF_EMAIL_CHANGED".equals(l.getAction()))
                .findFirst().orElseThrow();
        assertThat(log.getNewValue()).contains(oldEmail).contains(newEmail).doesNotContain(PASSWORD);
    }

    @Test
    void invalidDuplicateAndWrongPasswordEmailChangesAreRejected() throws Exception {
        User user = createUser("ROLE_STUDENT", true);
        User other = createUser("ROLE_STAFF", true);
        String token = bearer(user);
        put("/auth/me/email", token, Map.of("currentPassword", PASSWORD, "newEmail", "not-an-email")).andExpect(status().isBadRequest());
        put("/auth/me/email", token, Map.of("currentPassword", PASSWORD, "newEmail", other.getEmail().toUpperCase()))
                .andExpect(status().isConflict());
        put("/auth/me/email", token, Map.of("currentPassword", "Wrong@0000", "newEmail", "free." + user.getUsername() + "@test.local"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Current password is incorrect"));
        put("/auth/me/email", token, Map.of("currentPassword", PASSWORD, "newEmail", user.getEmail())).andExpect(status().isBadRequest());
        // No way to aim at another account: unknown fields are refused and nothing changes.
        put("/auth/me/email", token, Map.of("currentPassword", PASSWORD, "newEmail", "x." + user.getUsername() + "@test.local",
                "userId", other.getId())).andExpect(status().isBadRequest());
        assertThat(userRepository.findById(other.getId()).orElseThrow().getEmail()).isEqualTo(other.getEmail());
        assertThat(userRepository.findById(user.getId()).orElseThrow().getEmail()).isEqualTo(user.getEmail());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/auth/me/email")
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("currentPassword", PASSWORD, "newEmail", "a@b.co"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passwordChangeRevokesAccessAndRefreshTokens() throws Exception {
        User user = createUser("ROLE_HOD", true);
        JsonNode tokens = login(user);
        String access = "Bearer " + tokens.get("accessToken").asText();
        String refresh = tokens.get("refreshToken").asText();

        put("/auth/me/password", access, Map.of("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reauthenticationRequired").value(true));

        mvc.perform(get("/auth/me").header("Authorization", access)).andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(json(Map.of("refreshToken", refresh))))
                .andExpect(status().isUnauthorized());
        loginAs(user.getEmail(), PASSWORD).andExpect(status().isUnauthorized());
        loginAs(user.getEmail(), NEW_PASSWORD).andExpect(status().isOk());

        String hash = userRepository.findById(user.getId()).orElseThrow().getPassword();
        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).anyMatch(l -> "SELF_PASSWORD_CHANGED".equals(l.getAction()));
        assertThat(logs).allSatisfy(l -> {
            for (String value : new String[]{l.getNewValue(), l.getOldValue()}) {
                if (value == null) continue;
                assertThat(value).doesNotContain(NEW_PASSWORD).doesNotContain(PASSWORD).doesNotContain(hash);
            }
        });
    }

    @Test
    void invalidPasswordChangesAreRejected() throws Exception {
        User user = createUser("ROLE_STAFF", true);
        String token = bearer(user);
        put("/auth/me/password", token, Map.of("currentPassword", "Wrong@0000", "newPassword", NEW_PASSWORD))
                .andExpect(status().isBadRequest());
        put("/auth/me/password", token, Map.of("currentPassword", PASSWORD, "newPassword", "short")).andExpect(status().isBadRequest());
        put("/auth/me/password", token, Map.of("currentPassword", PASSWORD, "newPassword", PASSWORD)).andExpect(status().isBadRequest());
        // Still the old password; the token was not revoked.
        mvc.perform(get("/auth/me").header("Authorization", token)).andExpect(status().isOk());
        loginAs(user.getEmail(), PASSWORD).andExpect(status().isOk());
    }
}

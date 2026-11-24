package com.java.erp.modules.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.java.erp.modules.auth.entity.User;
import com.java.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends IntegrationTestBase {

    @Test
    void validLoginReturnsTokensAndUser() throws Exception {
        User user = createUser("ROLE_STAFF", true);

        JsonNode data = login(user);

        assertThat(data.get("accessToken").asText()).isNotBlank();
        assertThat(data.get("refreshToken").asText()).isNotBlank();
        assertThat(data.get("user").get("email").asText()).isEqualTo(user.getEmail());
    }

    @Test
    void invalidPasswordIsRejected() throws Exception {
        User user = createUser("ROLE_STAFF", true);

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", user.getEmail(), "password", "wrong-password"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void inactiveUserCannotLogIn() throws Exception {
        User user = createUser("ROLE_STAFF", false);

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", user.getEmail(), "password", PASSWORD))))
                .andExpect(status().isForbidden());
    }

    @Test
    void deactivatedUserIsBlockedEvenWithUnexpiredToken() throws Exception {
        User user = createUser("ROLE_STAFF", true);
        String token = bearer(user);
        mvc.perform(get("/auth/me").header("Authorization", token)).andExpect(status().isOk());

        user.setActive(false);
        userRepository.saveAndFlush(user);

        mvc.perform(get("/auth/me").header("Authorization", token)).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshTokenIssuesNewTokensButAccessTokenCannotBeUsedToRefresh() throws Exception {
        User user = createUser("ROLE_STAFF", true);
        JsonNode tokens = login(user);

        String body = mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", tokens.get("refreshToken").asText()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String newAccess = objectMapper.readTree(body).get("data").get("accessToken").asText();
        mvc.perform(get("/auth/me").header("Authorization", "Bearer " + newAccess)).andExpect(status().isOk());

        mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", tokens.get("accessToken").asText()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshTokenCannotBeUsedAsAccessToken() throws Exception {
        User user = createUser("ROLE_STAFF", true);
        JsonNode tokens = login(user);

        mvc.perform(get("/auth/me").header("Authorization", "Bearer " + tokens.get("refreshToken").asText()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesAccessAndRefreshTokens() throws Exception {
        User user = createUser("ROLE_STAFF", true);
        JsonNode tokens = login(user);
        String access = "Bearer " + tokens.get("accessToken").asText();

        mvc.perform(post("/auth/logout").header("Authorization", access)).andExpect(status().isOk());

        mvc.perform(get("/auth/me").header("Authorization", access)).andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", tokens.get("refreshToken").asText()))))
                .andExpect(status().isUnauthorized());

        // A fresh login still works afterwards.
        mvc.perform(get("/auth/me").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(user.getEmail()));
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        mvc.perform(get("/auth/me").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }
}

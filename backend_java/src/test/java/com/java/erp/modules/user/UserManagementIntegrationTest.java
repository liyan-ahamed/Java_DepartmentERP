package com.java.erp.modules.user;

import com.java.erp.modules.auth.entity.User;
import com.java.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserManagementIntegrationTest extends IntegrationTestBase {

    private Map<String, Object> newUser(String username, String email) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "New Person");
        body.put("username", username);
        body.put("email", email);
        body.put("password", "Secret@1234");
        body.put("roles", List.of("ROLE_STAFF"));
        return body;
    }

    @Test
    void hodCreatesUserWithSyncedRole() throws Exception {
        String hod = bearer(createUser("ROLE_HOD", true));

        mvc.perform(post("/users").header("Authorization", hod).contentType(MediaType.APPLICATION_JSON)
                        .content(json(newUser("new.person", "new.person@test.local"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.username").value("new.person"))
                .andExpect(jsonPath("$.data.roles[0]").value("ROLE_STAFF"));
    }

    @Test
    void duplicateEmailAndUsernameAreConflicts() throws Exception {
        String hod = bearer(createUser("ROLE_HOD", true));
        User existing = createUser("ROLE_STAFF", true);

        mvc.perform(post("/users").header("Authorization", hod).contentType(MediaType.APPLICATION_JSON)
                        .content(json(newUser("unique.name", existing.getEmail()))))
                .andExpect(status().isConflict());
        mvc.perform(post("/users").header("Authorization", hod).contentType(MediaType.APPLICATION_JSON)
                        .content(json(newUser(existing.getUsername(), "unique@test.local"))))
                .andExpect(status().isConflict());
    }

    @Test
    void missingUsernameIsValidationError() throws Exception {
        String hod = bearer(createUser("ROLE_HOD", true));
        Map<String, Object> body = newUser(null, "no.username@test.local");

        mvc.perform(post("/users").header("Authorization", hod).contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownRoleIsValidationError() throws Exception {
        String hod = bearer(createUser("ROLE_HOD", true));
        Map<String, Object> body = newUser("role.test", "role.test@test.local");
        body.put("roles", List.of("ROLE_SUPER_ADMIN"));

        mvc.perform(post("/users").header("Authorization", hod).contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void staffAndStudentsCannotCreateUsers() throws Exception {
        Map<String, Object> body = newUser("escalate", "escalate@test.local");
        body.put("roles", List.of("ROLE_HOD"));

        for (String role : List.of("ROLE_STAFF", "ROLE_STUDENT")) {
            mvc.perform(post("/users").header("Authorization", bearer(createUser(role, true)))
                            .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void studentsCannotListUsersAndBadPagingIsValidationError() throws Exception {
        mvc.perform(get("/users").header("Authorization", bearer(createUser("ROLE_STUDENT", true))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/users").param("page", "-1").header("Authorization", bearer(createUser("ROLE_HOD", true))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerEndpointNoLongerExists() throws Exception {
        mvc.perform(post("/auth/register").header("Authorization", bearer(createUser("ROLE_STUDENT", true)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(newUser("reg", "reg@test.local"))))
                .andExpect(status().isNotFound());
    }
}

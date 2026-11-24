package com.java.erp.modules.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.java.erp.modules.audit.repository.AuditLogRepository;
import com.java.erp.modules.auth.entity.User;
import com.java.erp.modules.system.entity.Batch;
import com.java.erp.modules.system.entity.Section;
import com.java.erp.support.AcademicTestBase;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HOD-only user management, server-side role / cohort filters and the single-role policy. */
class UserDirectoryAndSingleRoleIntegrationTest extends AcademicTestBase {

    @Autowired private EntityManager entityManager;
    @Autowired private AuditLogRepository auditLogRepository;

    private JsonNode list(String query) throws Exception {
        return data(mvc.perform(get("/users?size=100&" + query).header("Authorization", token(hod))).andExpect(status().isOk()))
                .get("content");
    }

    private Map<String, Object> newUser(String key, Object roles) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Person " + key);
        body.put("username", "u" + key);
        body.put("email", "u" + key + "@test.local");
        body.put("password", PASSWORD);
        body.put("roles", roles);
        return body;
    }

    private ResultActions create(Map<String, Object> body) throws Exception {
        return mvc.perform(post("/users").header("Authorization", token(hod)).contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    @Test
    void onlyHodMayUseUserManagement() throws Exception {
        StudentLogin student = studentLogin(section);
        for (User user : List.of(staff, student.user())) {
            mvc.perform(get("/users").header("Authorization", token(user))).andExpect(status().isForbidden());
            mvc.perform(get("/users/" + hod.getId()).header("Authorization", token(user))).andExpect(status().isForbidden());
            mvc.perform(post("/users").header("Authorization", token(user)).contentType(MediaType.APPLICATION_JSON)
                    .content(json(newUser(unique(), List.of("ROLE_STAFF"))))).andExpect(status().isForbidden());
            mvc.perform(put("/users/" + hod.getId()).header("Authorization", token(user)).contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("name", "X Y")))).andExpect(status().isForbidden());
            mvc.perform(patch("/users/" + otherStaff.getId() + "/deactivate").header("Authorization", token(user)))
                    .andExpect(status().isForbidden());
            mvc.perform(patch("/users/" + otherStaff.getId() + "/reactivate").header("Authorization", token(user)))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/users").header("Authorization", token(hod))).andExpect(status().isOk());
        // Self-service stays available to everyone.
        mvc.perform(get("/auth/me").header("Authorization", token(staff))).andExpect(status().isOk());
    }

    @Test
    void roleFilterSeparatesAdminsFacultyAndStudents() throws Exception {
        studentLogin(section);
        JsonNode admins = list("role=HOD");
        assertThat(admins).isNotEmpty().allSatisfy(u -> {
            assertThat(u.get("role").asText()).isEqualTo("HOD");
            assertThat(u.get("roles")).hasSize(1);
        });
        assertThat(admins).anyMatch(u -> u.get("id").asLong() == hod.getId());
        JsonNode faculty = list("role=STAFF");
        assertThat(faculty).isNotEmpty().allSatisfy(u -> {
            assertThat(u.get("role").asText()).isEqualTo("STAFF");
            assertThat(u.get("roles").get(0).asText()).isEqualTo("ROLE_STAFF");
        });
        assertThat(faculty).noneMatch(u -> u.get("id").asLong() == hod.getId());
        assertThat(list("role=student")).isNotEmpty().allSatisfy(u -> assertThat(u.get("role").asText()).isEqualTo("STUDENT"));
        assertThat(list("role=STAFF&search=" + staff.getName())).extracting(u -> u.get("id").asLong()).containsExactly(staff.getId());

        mvc.perform(get("/users?role=ADMIN").header("Authorization", token(hod))).andExpect(status().isBadRequest());
        mvc.perform(get("/users?role=STAFF&batchId=" + batch.getId()).header("Authorization", token(hod))).andExpect(status().isBadRequest());
    }

    @Test
    void studentDirectoryFiltersByCohortAndSection() throws Exception {
        StudentLogin inA = studentLogin(section);
        StudentLogin inB = studentLogin(otherSection);
        Batch otherBatch = batchRepository.save(new Batch("GX-" + unique(), 2024, 2028, (short) 3));
        StudentLogin elsewhere = studentLogin(sectionRepository.save(new Section(otherBatch, "A")));

        JsonNode cohort = list("role=STUDENT&batchId=" + batch.getId());
        assertThat(cohort).extracting(u -> u.get("id").asLong())
                .containsExactlyInAnyOrder(inA.user().getId(), inB.user().getId());
        JsonNode oneSection = list("role=STUDENT&batchId=" + batch.getId() + "&sectionId=" + section.getId());
        assertThat(oneSection).hasSize(1);
        JsonNode row = oneSection.get(0);
        assertThat(row.get("id").asLong()).isEqualTo(inA.user().getId());
        assertThat(row.get("studentId").asLong()).isEqualTo(inA.student().getId());
        assertThat(row.get("registerNumber").asText()).isEqualTo(inA.student().getRegisterNumber());
        assertThat(row.get("batchId").asLong()).isEqualTo(batch.getId());
        assertThat(row.get("batchName").asText()).isEqualTo(batch.getBatchName());
        assertThat(row.get("sectionId").asLong()).isEqualTo(section.getId());
        assertThat(row.get("sectionName").asText()).isEqualTo("A");
        assertThat(row.get("role").asText()).isEqualTo("STUDENT");
        assertThat(row.get("active").asBoolean()).isTrue();
        assertThat(list("role=STUDENT&sectionId=" + otherSection.getId())).extracting(u -> u.get("id").asLong())
                .containsExactly(inB.user().getId());
        assertThat(list("role=STUDENT&batchId=" + otherBatch.getId())).extracting(u -> u.get("id").asLong())
                .containsExactly(elsewhere.user().getId());
        // Single-user lookup carries the same student fields.
        mvc.perform(get("/users/" + inA.user().getId()).header("Authorization", token(hod)))
                .andExpect(jsonPath("$.data.registerNumber").value(inA.student().getRegisterNumber()))
                .andExpect(jsonPath("$.data.sectionName").value("A"));
    }

    @Test
    void createAndUpdateAcceptExactlyOneRole() throws Exception {
        create(newUser(unique(), List.of())).andExpect(status().isBadRequest());
        create(newUser(unique(), List.of("ROLE_STAFF", "ROLE_STUDENT"))).andExpect(status().isBadRequest());
        create(newUser(unique(), List.of("ROLE_HOD", "ROLE_STAFF"))).andExpect(status().isBadRequest());
        JsonNode created = data(create(newUser(unique(), List.of("STAFF"))).andExpect(status().isCreated()));
        assertThat(created.get("roles")).hasSize(1);
        assertThat(created.get("roles").get(0).asText()).isEqualTo("ROLE_STAFF");
        assertThat(created.get("role").asText()).isEqualTo("STAFF");
        long id = created.get("id").asLong();

        mvc.perform(put("/users/" + id).header("Authorization", token(hod)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("roles", List.of("ROLE_STAFF", "ROLE_HOD"))))).andExpect(status().isBadRequest());
        mvc.perform(put("/users/" + id).header("Authorization", token(hod)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("roles", List.of("ROLE_HOD")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles.length()").value(1))
                .andExpect(jsonPath("$.data.roles[0]").value("ROLE_HOD"))
                .andExpect(jsonPath("$.data.role").value("HOD"));
        entityManager.flush();
        assertThat(roleRowCount(id)).isEqualTo(1);
        assertThat(auditLogRepository.findAll()).anyMatch(l -> "USER_ROLE_CHANGED".equals(l.getAction())
                && l.getNewValue().contains("STAFF") && l.getNewValue().contains("HOD"));
    }

    @Test
    void everyUserHasExactlyOneRoleAndTheMirrorMatches() {
        entityManager.flush();
        Number multi = (Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM (SELECT user_id FROM user_roles GROUP BY user_id HAVING COUNT(*) > 1) x").getSingleResult();
        Number none = (Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM users u WHERE NOT EXISTS (SELECT 1 FROM user_roles ur WHERE ur.user_id = u.id)").getSingleResult();
        Number mismatch = (Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM users u JOIN user_roles ur ON ur.user_id = u.id JOIN roles r ON r.id = ur.role_id "
                        + "WHERE u.role::text <> SUBSTRING(r.name FROM 6)").getSingleResult();
        assertThat(multi.longValue()).isZero();
        assertThat(none.longValue()).isZero();
        assertThat(mismatch.longValue()).isZero();
        // The database refuses a second role row.
        Long roleId = roleRepository.findByName("ROLE_HOD").orElseThrow().getId();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> {
            entityManager.createNativeQuery("INSERT INTO user_roles (user_id, role_id) VALUES (?1, ?2)")
                    .setParameter(1, staff.getId()).setParameter(2, roleId).executeUpdate();
        }).isNotNull();
    }

    @Test
    void hodStaffAndStudentLoginsStillWork() throws Exception {
        StudentLogin student = studentLogin(section);
        for (User user : List.of(hod, staff, student.user())) {
            JsonNode login = login(user);
            assertThat(login.get("user").get("roles")).hasSize(1);
        }
        mvc.perform(get("/subjects").header("Authorization", token(staff))).andExpect(status().isOk());
        mvc.perform(get("/fees/me").header("Authorization", token(student.user()))).andExpect(status().isOk());
        mvc.perform(get("/fees/summary").header("Authorization", token(hod))).andExpect(status().isOk());
        mvc.perform(get("/fees/summary").header("Authorization", token(staff))).andExpect(status().isForbidden());
    }

    private long roleRowCount(long userId) {
        return ((Number) entityManager.createNativeQuery("SELECT COUNT(*) FROM user_roles WHERE user_id = ?1")
                .setParameter(1, userId).getSingleResult()).longValue();
    }
}

package com.java.erp.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.erp.modules.auth.entity.User;
import com.java.erp.modules.auth.repository.RoleRepository;
import com.java.erp.modules.auth.repository.UserRepository;
import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base for integration tests. Runs against a separate local PostgreSQL database
 * (erp_db_test, created on first use with the credentials from .env) so the real
 * database is never touched, with a random JWT secret generated for the run. Every test runs in a transaction that is rolled back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class IntegrationTestBase {

    protected static final String TEST_DATABASE = "erp_db_test";
    protected static final String PASSWORD = "Password@123";

    /** Random per test run, so no JWT secret is stored in any file. */
    private static final String TEST_JWT_SECRET = generateSecret();

    private static String encodedPassword;

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected UserRepository userRepository;
    @Autowired protected RoleRepository roleRepository;
    @Autowired protected PasswordEncoder passwordEncoder;

    @DynamicPropertySource
    static void testDatabase(DynamicPropertyRegistry registry) throws Exception {
        Dotenv env = Dotenv.configure().ignoreIfMissing().load();
        String base = "jdbc:postgresql://" + env.get("DB_HOST", "localhost") + ":" + env.get("DB_PORT", "5432") + "/";
        String user = env.get("DB_USERNAME", "postgres");
        String password = env.get("DB_PASSWORD", "postgres");
        if (TEST_DATABASE.equals(env.get("DB_NAME", "erp_db"))) {
            throw new IllegalStateException("Test database must differ from the application database");
        }

        try (Connection connection = DriverManager.getConnection(base + "postgres", user, password);
             PreparedStatement exists = connection.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?")) {
            exists.setString(1, TEST_DATABASE);
            try (ResultSet rs = exists.executeQuery()) {
                if (!rs.next()) {
                    try (Statement create = connection.createStatement()) {
                        create.execute("CREATE DATABASE " + TEST_DATABASE);
                    }
                }
            }
        }
        registry.add("spring.datasource.url", () -> base + TEST_DATABASE);
        registry.add("app.security.jwt.secret", () -> TEST_JWT_SECRET);
    }

    private static String generateSecret() {
        byte[] bytes = new byte[96];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    protected User createUser(String roleName, boolean active) {
        if (encodedPassword == null) {
            encodedPassword = passwordEncoder.encode(PASSWORD);
        }
        String unique = UUID.randomUUID().toString().substring(0, 8);
        User user = new User("t_" + unique, "Test " + unique, "t_" + unique + "@test.local", encodedPassword);
        user.addRole(roleRepository.findByName(roleName).orElseThrow());
        user.setActive(active);
        return userRepository.saveAndFlush(user);
    }

    /** Logs in and returns the response's data node (accessToken, refreshToken, user). */
    protected JsonNode login(User user) throws Exception {
        String body = mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", user.getEmail(), "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data");
    }

    protected String bearer(User user) throws Exception {
        return "Bearer " + login(user).get("accessToken").asText();
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}

package com.java.erp.config;

import com.java.erp.modules.auth.entity.Role;
import com.java.erp.modules.auth.entity.User;
import com.java.erp.modules.auth.repository.RoleRepository;
import com.java.erp.modules.auth.repository.UserRepository;
import com.java.erp.modules.system.repository.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the demo login accounts (HOD, Staff, one Student) for local development.
 * Runs only with the "dev" profile (SPRING_PROFILES_ACTIVE=dev) and only creates
 * accounts that do not already exist. Department data (batches, sections, students,
 * extra staff) comes from Flyway migration V10.
 */
@Component
@Profile("dev")
@Order(1)
public class DataSeeder implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final StudentRepository studentRepository;

    public DataSeeder(UserRepository userRepository,
                      RoleRepository roleRepository,
                      PasswordEncoder passwordEncoder,
                      StudentRepository studentRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.studentRepository = studentRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedUser(
                "hod@department.com",
                "dr.kumar",
                "Dr Kumar",
                "password123",
                "ST001",
                "9876543210",
                "HOD",
                "ROLE_HOD"
        );

        seedUser(
                "staff@department.com",
                "staff.user",
                "Staff User",
                "password123",
                "ST002",
                "9876543211",
                "STAFF",
                "ROLE_STAFF"
        );

        seedUser(
                "aarav.sharma1@student.erp.com",
                "reg2024a001",
                "Aarav Sharma",
                "password123",
                "REG2024A001",
                null,
                "STUDENT",
                "ROLE_STUDENT"
        );

        userRepository.findByEmail("aarav.sharma1@student.erp.com").ifPresent(user ->
                studentRepository.findFirstByEmailIgnoreCaseAndActiveTrue(user.getEmail()).ifPresent(student -> {
                    if (student.getUser() == null) {
                        student.setUser(user);
                        studentRepository.save(student);
                    }
                }));
    }

    private void seedUser(String email, String username, String name,
                          String rawPassword, String staffCode, String phone,
                          String designation, String roleName) {
        if (userRepository.findByEmail(email).isPresent()) {
            logger.info("Seed user '{}' already exists, skipping", email);
            return;
        }

        Role role = roleRepository.findByName(roleName).orElse(null);
        if (role == null) {
            logger.warn("Role '{}' not found, cannot seed user '{}'", roleName, email);
            return;
        }

        User user = new User(username, name, email, passwordEncoder.encode(rawPassword));
        user.setStaffCode(staffCode);
        user.setPhone(phone);
        user.setDesignation(designation);
        user.setActive(true);
        user.addRole(role);

        userRepository.save(user);
        logger.info("Seeded user '{}' with role '{}'", email, roleName);
    }
}

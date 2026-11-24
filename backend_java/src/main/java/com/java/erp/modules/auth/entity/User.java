package com.java.erp.modules.auth.entity;

import com.java.erp.common.entity.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.HashSet;
import java.util.Set;

/**
 * User entity representing system users (HOD, staff and students).
 * Audit fields are inherited from BaseEntity.
 *
 * Authorization source of truth is the {@code roles} collection (user_roles table),
 * which Spring Security turns into role and permission authorities. The legacy
 * {@code users.role} column ({@link #deptRole}) is kept only because the schema
 * requires it, and is always derived from {@code roles} so the two cannot diverge.
 */
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    /**
     * Department role enum — maps to PostgreSQL 'dept_role' enum type.
     */
    public enum DeptRole {
        HOD, STAFF, STUDENT, COE;

        static DeptRole fromRoles(Set<Role> roles) {
            if (roles.stream().anyMatch(role -> "ROLE_STUDENT".equals(role.getName()))) {
                return STUDENT;
            }
            if (roles.stream().anyMatch(role -> "ROLE_HOD".equals(role.getName()))) {
                return HOD;
            }
            if (roles.stream().anyMatch(role -> "ROLE_COE".equals(role.getName()))) {
                return COE;
            }
            return STAFF;
        }
    }

    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "staff_code", unique = true, length = 20)
    private String staffCode;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "designation", length = 50)
    private String designation;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "role", nullable = false, columnDefinition = "dept_role")
    private DeptRole deptRole = DeptRole.STAFF;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /** Embedded in every JWT; incrementing it revokes all of the user's issued tokens. */
    @Column(name = "token_version", nullable = false)
    private int tokenVersion = 0;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    public User() {
    }

    public User(String username, String name, String email, String password) {
        this.username = username;
        this.name = name;
        this.email = email;
        this.password = password;
    }

    // Helper methods

    /**
     * Gives the user its (single) application role, replacing any previous one. Every user has exactly one
     * role (V38: one user_roles row per user); users.role mirrors it.
     */
    public void addRole(Role role) {
        this.roles.clear();
        this.roles.add(role);
        this.deptRole = DeptRole.fromRoles(this.roles);
    }

    public void revokeTokens() {
        this.tokenVersion++;
    }

    // Getters and Setters

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getStaffCode() {
        return staffCode;
    }

    public void setStaffCode(String staffCode) {
        this.staffCode = staffCode;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getDesignation() {
        return designation;
    }

    public void setDesignation(String designation) {
        this.designation = designation;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    /** Exactly one role (single-role policy). */
    public void setRoles(Set<Role> roles) {
        if (roles == null || roles.size() != 1) {
            throw new IllegalArgumentException("A user must have exactly one role");
        }
        addRole(roles.iterator().next());
    }
}

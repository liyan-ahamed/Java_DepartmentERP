package com.java.erp.modules.auth.repository;

import com.java.erp.modules.auth.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository for User entity.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    boolean existsByUsername(String username);

    boolean existsByStaffCode(String staffCode);

    /**
     * Count active users holding the given role (e.g. ROLE_STAFF).
     */
    @Query("SELECT COUNT(DISTINCT u) FROM User u JOIN u.roles r WHERE u.active = true AND r.name = :roleName")
    long countActiveByRoleName(@Param("roleName") String roleName);

    @Query("SELECT DISTINCT u.id FROM User u JOIN u.roles r WHERE u.active = true AND r.name IN :roleNames")
    List<Long> findActiveIdsByRoleNames(@Param("roleNames") Collection<String> roleNames);

    /**
     * Active STUDENT logins whose email matches one of the given (lower-case) emails.
     * Mirrors the email fallback of StudentRepository.findLinkedStudent.
     */
    @Query("SELECT DISTINCT u FROM User u JOIN u.roles r " +
           "WHERE u.active = true AND r.name = 'ROLE_STUDENT' AND LOWER(u.email) IN :emails")
    List<User> findActiveStudentUsersByEmails(@Param("emails") Collection<String> emails);

    /**
     * Find all users with pagination and optional active filter.
     */
    @Query("SELECT u FROM User u WHERE (:active IS NULL OR u.active = :active)")
    Page<User> findAllWithActiveFilter(@Param("active") Boolean active, Pageable pageable);

    /**
     * Search users by name or email with optional active filter.
     */
    @Query("SELECT u FROM User u WHERE " +
           "(:active IS NULL OR u.active = :active) AND " +
           "(LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<User> searchWithActiveFilter(@Param("search") String search,
                                      @Param("active") Boolean active,
                                      Pageable pageable);
}

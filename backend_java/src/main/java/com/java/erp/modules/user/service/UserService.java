package com.java.erp.modules.user.service;

import com.java.erp.common.audit.Auditable;
import com.java.erp.common.response.PagedResponse;
import com.java.erp.exception.BadRequestException;
import com.java.erp.exception.ConflictException;
import com.java.erp.exception.ResourceNotFoundException;
import com.java.erp.modules.auth.entity.Permission;
import com.java.erp.modules.auth.entity.Role;
import com.java.erp.modules.auth.entity.User;
import com.java.erp.modules.auth.repository.RoleRepository;
import com.java.erp.modules.auth.repository.UserRepository;
import com.java.erp.modules.audit.service.AuditRecorder;
import com.java.erp.modules.system.entity.Student;
import com.java.erp.modules.system.repository.StudentRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import com.java.erp.modules.user.dto.request.CreateUserRequest;
import com.java.erp.modules.user.dto.request.UpdateUserRequest;
import com.java.erp.modules.user.dto.response.UserDetailResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for comprehensive user management operations.
 * Handles CRUD, role assignment, and activation status.
 */
@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    private static final String DEFAULT_ROLE = "ROLE_STAFF";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final StudentRepository studentRepository;
    private final AuditRecorder audit;

    public UserService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       StudentRepository studentRepository,
                       AuditRecorder audit) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.studentRepository = studentRepository;
        this.audit = audit;
    }

    /**
     * Create a new user.
     */
    @Transactional
    @Auditable(action = "CREATE", module = "USER_MANAGEMENT", entityType = "User")
    public UserDetailResponse createUser(CreateUserRequest request) {
        // Check for duplicate email
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("User", "email", request.getEmail());
        }

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ConflictException("User", "username", request.getUsername());
        }

        // Check for duplicate staff code if provided
        if (StringUtils.hasText(request.getStaffCode()) && userRepository.existsByStaffCode(request.getStaffCode())) {
            throw new ConflictException("User", "staffCode", request.getStaffCode());
        }

        User user = new User(
                request.getUsername(),
                request.getName(),
                request.getEmail(),
                passwordEncoder.encode(request.getPassword())
        );
        user.setPhone(request.getPhone());
        user.setStaffCode(StringUtils.hasText(request.getStaffCode()) ? request.getStaffCode() : null);
        user.setDesignation(request.getDesignation());
        user.setActive(true);

        // Assign roles
        assignRolesToUser(user, request.getRoles());

        User savedUser = userRepository.save(user);
        logger.info("User created successfully: {}", savedUser.getEmail());

        return toUserDetailResponse(savedUser);
    }

    /**
     * Update an existing user.
     */
    @Transactional
    @Auditable(action = "UPDATE", module = "USER_MANAGEMENT", entityType = "User")
    public UserDetailResponse updateUser(Long id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        // Update fields if provided
        if (StringUtils.hasText(request.getName())) {
            user.setName(request.getName());
        }

        if (StringUtils.hasText(request.getUsername()) && !request.getUsername().equals(user.getUsername())) {
            if (userRepository.existsByUsername(request.getUsername())) {
                throw new ConflictException("User", "username", request.getUsername());
            }
            user.setUsername(request.getUsername());
        }

        if (StringUtils.hasText(request.getEmail()) && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new ConflictException("User", "email", request.getEmail());
            }
            user.setEmail(request.getEmail());
        }

        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }

        if (StringUtils.hasText(request.getStaffCode()) && !request.getStaffCode().equals(user.getStaffCode())) {
            if (userRepository.existsByStaffCode(request.getStaffCode())) {
                throw new ConflictException("User", "staffCode", request.getStaffCode());
            }
            user.setStaffCode(request.getStaffCode());
        }

        if (request.getDesignation() != null) {
            user.setDesignation(request.getDesignation());
        }

        if (request.getRoles() != null) {
            String oldRole = roleOf(user);
            assignRolesToUser(user, request.getRoles());
            String newRole = roleOf(user);
            if (!Objects.equals(oldRole, newRole)) {
                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("userId", user.getId());
                summary.put("oldRole", oldRole);
                summary.put("newRole", newRole);
                audit.record("USER_ROLE_CHANGED", "USER_MANAGEMENT", "User", user.getId(), summary);
            }
        }

        User updatedUser = userRepository.save(user);
        logger.info("User updated successfully: {}", updatedUser.getEmail());

        return toUserDetailResponse(updatedUser);
    }

    /**
     * Get user details by ID.
     */
    @Transactional(readOnly = true)
    public UserDetailResponse getUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        return toUserDetailResponse(user);
    }

    /**
     * Paginated users, filtered in the database: role (HOD | STAFF | STUDENT | COE), active, search (name or email),
     * and for students batchId / sectionId (of the linked student record). Linked students are loaded in one query.
     */
    @Transactional(readOnly = true)
    public PagedResponse<UserDetailResponse> getAllUsers(int page, int size, String search, Boolean active,
                                                         String role, Long batchId, Long sectionId) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        String roleName = StringUtils.hasText(role) ? normalizeRole(role) : null;
        if (roleName != null && roleRepository.findByName(roleName).isEmpty()) {
            throw new BadRequestException("role must be HOD, STAFF, STUDENT or COE");
        }
        if ((batchId != null || sectionId != null) && roleName != null && !"ROLE_STUDENT".equals(roleName)) {
            throw new BadRequestException("batchId and sectionId filter students only (role=STUDENT)");
        }
        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (active != null) p.add(cb.equal(root.get("active"), active));
            if (StringUtils.hasText(search)) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("name")), pattern), cb.like(cb.lower(root.get("email")), pattern)));
            }
            if (roleName != null) {
                p.add(cb.equal(root.join("roles").get("name"), roleName));
            }
            if (batchId != null || sectionId != null) {
                Subquery<Long> linked = query.subquery(Long.class);
                Root<Student> s = linked.from(Student.class);
                List<Predicate> sp = new ArrayList<>();
                sp.add(cb.isTrue(s.get("active")));
                if (batchId != null) sp.add(cb.equal(s.get("batch").get("id"), batchId));
                if (sectionId != null) sp.add(cb.equal(s.get("section").get("id"), sectionId));
                linked.select(s.get("user").get("id")).where(sp.toArray(Predicate[]::new));
                p.add(root.get("id").in(linked));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        Page<User> usersPage = userRepository.findAll(spec, pageable);

        List<Long> ids = usersPage.getContent().stream().map(User::getId).toList();
        Map<Long, Student> studentByUser = new HashMap<>();
        if (!ids.isEmpty()) {
            studentRepository.findByUserIdInAndActiveTrue(ids).forEach(s -> studentByUser.put(s.getUser().getId(), s));
        }
        List<UserDetailResponse> content = usersPage.getContent().stream()
                .map(u -> toUserDetailResponse(u, studentByUser.get(u.getId())))
                .collect(Collectors.toList());

        return PagedResponse.of(content, usersPage);
    }

    /**
     * Deactivate a user.
     */
    @Transactional
    @Auditable(action = "DEACTIVATE", module = "USER_MANAGEMENT", entityType = "User")
    public UserDetailResponse deactivateUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        
        user.setActive(false);
        user.revokeTokens();
        User savedUser = userRepository.save(user);
        logger.info("User deactivated: {}", savedUser.getEmail());
        
        return toUserDetailResponse(savedUser);
    }

    /**
     * Reactivate a user.
     */
    @Transactional
    @Auditable(action = "REACTIVATE", module = "USER_MANAGEMENT", entityType = "User")
    public UserDetailResponse reactivateUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        
        user.setActive(true);
        User savedUser = userRepository.save(user);
        logger.info("User reactivated: {}", savedUser.getEmail());
        
        return toUserDetailResponse(savedUser);
    }

    /**
     * Gives the user exactly one role. roles must hold exactly one name (ROLE_STAFF or STAFF style);
     * an omitted list on create keeps the old default ROLE_STAFF. Unknown names are rejected.
     */
    private void assignRolesToUser(User user, Set<String> roleNames) {
        Set<String> names = roleNames == null ? Set.of(DEFAULT_ROLE) : roleNames;
        if (names.size() != 1) {
            throw new BadRequestException("roles must contain exactly one role (HOD, STAFF, STUDENT or COE)");
        }
        String roleName = normalizeRole(names.iterator().next());
        user.addRole(roleRepository.findByName(roleName)
                .orElseThrow(() -> new BadRequestException("Unknown role: " + roleName)));
    }

    /** "staff" / "STAFF" / "ROLE_STAFF" -> "ROLE_STAFF". */
    private static String normalizeRole(String name) {
        String upper = name == null ? "" : name.trim().toUpperCase(Locale.ROOT);
        return upper.startsWith("ROLE_") ? upper : "ROLE_" + upper;
    }

    private static String roleOf(User user) {
        return user.getRoles().stream().map(r -> r.getName().replaceFirst("^ROLE_", "")).findFirst().orElse(null);
    }

    /** Map a User entity to UserDetailResponse DTO (looks up the linked student). */
    private UserDetailResponse toUserDetailResponse(User user) {
        UserDetailResponse response = toUserDetailResponse(user, null);
        studentRepository.findByUserIdAndActiveTrue(user.getId()).ifPresent(s -> attachStudent(response, s));
        return response;
    }

    private static void attachStudent(UserDetailResponse response, Student s) {
        response.setStudent(s.getId(), s.getRegisterNumber(), s.getBatch().getId(), s.getBatch().getBatchName(),
                s.getSection().getId(), s.getSection().getSectionName());
    }

    /**
     * Map a User entity to UserDetailResponse DTO.
     */
    private UserDetailResponse toUserDetailResponse(User user, Student student) {
        Set<String> roleNames = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());

        Set<String> permissionNames = new HashSet<>();
        for (Role role : user.getRoles()) {
            if (role.getPermissions() != null) {
                for (Permission permission : role.getPermissions()) {
                    permissionNames.add(permission.getName());
                }
            }
        }

        UserDetailResponse response = new UserDetailResponse(
                user.getId(),
                user.getUsername(),
                user.getName(),
                user.getEmail(),
                user.getStaffCode(),
                user.getPhone(),
                user.getDesignation(),
                user.isActive(),
                roleNames,
                permissionNames,
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
        response.setRole(roleOf(user));
        if (student != null) {
            attachStudent(response, student);
        }
        return response;
    }
}

package com.java.erp.modules.academic.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A subject taught by the department (e.g. CS301 Computer Networks).
 * A subject is not tied to a staff member or section; see {@link SubjectOffering}.
 */
@Entity
@Table(name = "subjects")
public class Subject {

    /**
     * Decides the shape of the future internal-marks summary:
     * THEORY has a theory component only, THEORY_CUM_PRACTICAL has theory + practical,
     * PROJECT_BASED is project-assessed (course codes ending in "R").
     * A subject may be unclassified (null) until the HOD records an authoritative type.
     */
    public enum SubjectType { THEORY, THEORY_CUM_PRACTICAL, PROJECT_BASED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", length = 30)
    private SubjectType subjectType;

    /** Whole-number credits (0 allowed); null = not recorded. */
    @Column(name = "credits")
    private Integer credits;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Subject() {
    }

    public Subject(String code, String name, SubjectType subjectType) {
        this.code = code;
        this.name = name;
        this.subjectType = subjectType;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public SubjectType getSubjectType() { return subjectType; }
    public void setSubjectType(SubjectType subjectType) { this.subjectType = subjectType; }
    public Integer getCredits() { return credits; }
    public void setCredits(Integer credits) { this.credits = credits; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

package com.java.erp.modules.enrollment.entity;

import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.auth.entity.User;
import com.java.erp.modules.system.entity.Student;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A student taking a subject offering. One row per student per offering; the status changes in
 * place (ENROLLED -> DROPPED -> ENROLLED, ENROLLED -> COMPLETED).
 */
@Entity
@Table(name = "course_enrollments")
public class CourseEnrollment {

    public enum Status { ENROLLED, DROPPED, COMPLETED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_offering_id", nullable = false, updatable = false)
    private SubjectOffering subjectOffering;

    @Enumerated(EnumType.STRING)
    @Column(name = "enrollment_status", nullable = false, length = 20)
    private Status status = Status.ENROLLED;

    @Column(name = "enrolled_at", nullable = false)
    private LocalDateTime enrolledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enrolled_by")
    private User enrolledBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        if (enrolledAt == null) enrolledAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public CourseEnrollment() {
    }

    public CourseEnrollment(Student student, SubjectOffering subjectOffering, User enrolledBy) {
        this.student = student;
        this.subjectOffering = subjectOffering;
        this.enrolledBy = enrolledBy;
    }

    public Long getId() { return id; }
    public Student getStudent() { return student; }
    public SubjectOffering getSubjectOffering() { return subjectOffering; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public LocalDateTime getEnrolledAt() { return enrolledAt; }
    public void setEnrolledAt(LocalDateTime enrolledAt) { this.enrolledAt = enrolledAt; }
    public User getEnrolledBy() { return enrolledBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

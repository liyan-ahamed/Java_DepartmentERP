package com.java.erp.modules.exams.entity;

import com.java.erp.modules.academic.entity.SubjectOffering;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/** One subject offering a registration covers. The student must be enrolled in it. */
@Entity
@Table(name = "exam_registration_subjects")
public class ExamRegistrationSubject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_registration_id", nullable = false, updatable = false)
    private ExamRegistration registration;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_offering_id", nullable = false, updatable = false)
    private SubjectOffering subjectOffering;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public ExamRegistrationSubject() {
    }

    public ExamRegistrationSubject(ExamRegistration registration, SubjectOffering subjectOffering) {
        this.registration = registration;
        this.subjectOffering = subjectOffering;
    }

    public Long getId() { return id; }
    public ExamRegistration getRegistration() { return registration; }
    public SubjectOffering getSubjectOffering() { return subjectOffering; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}

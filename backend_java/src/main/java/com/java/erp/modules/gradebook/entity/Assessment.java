package com.java.erp.modules.gradebook.entity;

import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.auth.entity.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * An offline assessment of a subject offering (CAT, assignment, practical, ...).
 * Starts as DRAFT (editable, hidden from students) and becomes FINALIZED once
 * (immutable, visible to students). There is no reopening yet.
 */
@Entity
@Table(name = "assessments")
public class Assessment {

    /**
     * Classification only. No internal-mark formula is attached to any type yet,
     * and several assessments may share a type (Assignment 1, Assignment 2).
     */
    public enum AssessmentType {
        CAT_1, CAT_2, ASSIGNMENT, QUIZ, LAB, PRACTICAL, RECORD,
        GROUP_PRESENTATION, SEMINAR, PROJECT, OTHER
    }

    public enum Status { DRAFT, FINALIZED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_offering_id", nullable = false, updatable = false)
    private SubjectOffering subjectOffering;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "assessment_type", nullable = false, length = 30)
    private AssessmentType assessmentType;

    @Column(name = "max_marks", nullable = false, precision = 6, scale = 2)
    private BigDecimal maxMarks;

    @Column(name = "pass_marks", precision = 6, scale = 2)
    private BigDecimal passMarks;

    @Column(name = "weightage", precision = 5, scale = 2)
    private BigDecimal weightage;

    @Column(name = "assessment_date", nullable = false)
    private LocalDate assessmentDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "finalized_at")
    private LocalDateTime finalizedAt;

    /** Optional deadline (set by the HOD) for the assigned staff member to finish entering marks. */
    @Column(name = "marks_entry_due_at")
    private OffsetDateTime marksEntryDueAt;

    public OffsetDateTime getMarksEntryDueAt() { return marksEntryDueAt; }
    public void setMarksEntryDueAt(OffsetDateTime marksEntryDueAt) { this.marksEntryDueAt = marksEntryDueAt; }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isDraft() { return status == Status.DRAFT; }

    public Long getId() { return id; }
    public SubjectOffering getSubjectOffering() { return subjectOffering; }
    public void setSubjectOffering(SubjectOffering subjectOffering) { this.subjectOffering = subjectOffering; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public AssessmentType getAssessmentType() { return assessmentType; }
    public void setAssessmentType(AssessmentType assessmentType) { this.assessmentType = assessmentType; }
    public BigDecimal getMaxMarks() { return maxMarks; }
    public void setMaxMarks(BigDecimal maxMarks) { this.maxMarks = maxMarks; }
    public BigDecimal getPassMarks() { return passMarks; }
    public void setPassMarks(BigDecimal passMarks) { this.passMarks = passMarks; }
    public BigDecimal getWeightage() { return weightage; }
    public void setWeightage(BigDecimal weightage) { this.weightage = weightage; }
    public LocalDate getAssessmentDate() { return assessmentDate; }
    public void setAssessmentDate(LocalDate assessmentDate) { this.assessmentDate = assessmentDate; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getFinalizedAt() { return finalizedAt; }
    public void setFinalizedAt(LocalDateTime finalizedAt) { this.finalizedAt = finalizedAt; }
}

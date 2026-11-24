package com.java.erp.modules.feedback.entity;

import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.auth.entity.User;
import com.java.erp.modules.system.entity.Batch;
import com.java.erp.modules.system.entity.Section;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** A feedback form created by the HOD: DRAFT (editable) -> OPEN (students answer) -> CLOSED. */
@Entity
@Table(name = "feedback_forms")
public class FeedbackForm {

    public enum TargetType { DEPARTMENT, BATCH, SECTION, SUBJECT_OFFERING }

    public enum Status { DRAFT, OPEN, CLOSED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private TargetType targetType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_batch_id")
    private Batch targetBatch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_section_id")
    private Section targetSection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_subject_offering_id")
    private SubjectOffering targetSubjectOffering;

    @Column(name = "anonymous", nullable = false)
    private boolean anonymous = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private User createdBy;

    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @OneToMany(mappedBy = "form", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<FeedbackQuestion> questions = new ArrayList<>();

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

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public TargetType getTargetType() { return targetType; }
    public void setTargetType(TargetType targetType) { this.targetType = targetType; }
    public Batch getTargetBatch() { return targetBatch; }
    public void setTargetBatch(Batch targetBatch) { this.targetBatch = targetBatch; }
    public Section getTargetSection() { return targetSection; }
    public void setTargetSection(Section targetSection) { this.targetSection = targetSection; }
    public SubjectOffering getTargetSubjectOffering() { return targetSubjectOffering; }
    public void setTargetSubjectOffering(SubjectOffering targetSubjectOffering) { this.targetSubjectOffering = targetSubjectOffering; }
    public boolean isAnonymous() { return anonymous; }
    public void setAnonymous(boolean anonymous) { this.anonymous = anonymous; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getOpenedAt() { return openedAt; }
    public void setOpenedAt(LocalDateTime openedAt) { this.openedAt = openedAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
    public List<FeedbackQuestion> getQuestions() { return questions; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

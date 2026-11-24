package com.java.erp.modules.curriculum.entity;

import com.java.erp.modules.auth.entity.User;
import com.java.erp.modules.system.entity.Batch;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * The registration requirements of one cohort (batch) in one semester: compulsory subjects and elective groups
 * (see {@link CurriculumSubject}, {@link CurriculumElectiveGroup}). No row = requirements unknown.
 * Saved as a whole document; version guards against overwriting a newer save.
 */
@Entity
@Table(name = "curriculum_requirements")
public class CurriculumRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false, updatable = false)
    private Batch batch;

    @Column(name = "semester", nullable = false, updatable = false)
    private Integer semester;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    public CurriculumRequirement() {
    }

    public CurriculumRequirement(Batch batch, Integer semester) {
        this.batch = batch;
        this.semester = semester;
    }

    /** Marks a save. Always dirties the row, so the version moves on every save. */
    public void touch(User by) {
        updatedBy = by;
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Batch getBatch() { return batch; }
    public Integer getSemester() { return semester; }
    public Long getVersion() { return version; }
    public User getUpdatedBy() { return updatedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

package com.java.erp.modules.curriculum.entity;

import com.java.erp.modules.academic.entity.Subject;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * One subject of a curriculum: compulsory when electiveGroup is null, otherwise an option of that group.
 * A subject appears at most once per curriculum (DB unique key).
 */
@Entity
@Table(name = "curriculum_subjects")
public class CurriculumSubject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "curriculum_id", nullable = false, updatable = false)
    private CurriculumRequirement curriculum;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false, updatable = false)
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "elective_group_id", updatable = false)
    private CurriculumElectiveGroup electiveGroup;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public CurriculumSubject() {
    }

    public CurriculumSubject(CurriculumRequirement curriculum, Subject subject, CurriculumElectiveGroup electiveGroup,
                             int displayOrder) {
        this.curriculum = curriculum;
        this.subject = subject;
        this.electiveGroup = electiveGroup;
        this.displayOrder = displayOrder;
    }

    public Long getId() { return id; }
    public CurriculumRequirement getCurriculum() { return curriculum; }
    public Subject getSubject() { return subject; }
    public CurriculumElectiveGroup getElectiveGroup() { return electiveGroup; }
    public Integer getDisplayOrder() { return displayOrder; }
}

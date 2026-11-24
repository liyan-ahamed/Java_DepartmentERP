package com.java.erp.modules.curriculum.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** "Choose requiredSelections of these options"; the options are {@link CurriculumSubject} rows of this group. */
@Entity
@Table(name = "curriculum_elective_groups")
public class CurriculumElectiveGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "curriculum_id", nullable = false, updatable = false)
    private CurriculumRequirement curriculum;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "required_selections", nullable = false)
    private Integer requiredSelections;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

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

    public CurriculumElectiveGroup() {
    }

    public CurriculumElectiveGroup(CurriculumRequirement curriculum) {
        this.curriculum = curriculum;
    }

    public Long getId() { return id; }
    public CurriculumRequirement getCurriculum() { return curriculum; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getRequiredSelections() { return requiredSelections; }
    public void setRequiredSelections(Integer requiredSelections) { this.requiredSelections = requiredSelections; }
    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }
}

package com.java.erp.modules.portfolio.entity;

import com.java.erp.modules.auth.entity.User;
import com.java.erp.modules.system.entity.Student;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One achievement or activity in a student's departmental portfolio.
 * DRAFT (private to the student) -> SUBMITTED -> VERIFIED or REJECTED; a REJECTED entry can be
 * edited and submitted again. Only VERIFIED entries form the student's verified portfolio.
 * Transition rules live in the service layer; {@link #version} stops concurrent changes
 * (e.g. two reviewers deciding at once) from overwriting each other.
 */
@Entity
@Table(name = "portfolio_entries")
public class PortfolioEntry {

    public enum ActivityType {
        HACKATHON("Hackathon"),
        CERTIFICATION("Certification"),
        COMPETITION("Competition"),
        WORKSHOP("Workshop"),
        INTERNSHIP("Internship"),
        RESEARCH_PUBLICATION("Research publication"),
        CONFERENCE("Conference"),
        TECHNICAL_EVENT("Technical event"),
        PROJECT_SHOWCASE("Project showcase"),
        CLUB_ACTIVITY("Club activity"),
        OTHER("Activity");

        private final String label;

        ActivityType(String label) {
            this.label = label;
        }

        /** Human-readable name used in notification texts ("Your Hackathon entry ..."). */
        public String label() {
            return label;
        }
    }

    /**
     * Scope of the activity. Use the widest geographic scope that applies (an online international
     * hackathon is INTERNATIONAL); ONLINE is for online-only activities without a geographic
     * scope, such as MOOC certifications.
     */
    public enum Level { COLLEGE, INTER_COLLEGE, STATE, NATIONAL, INTERNATIONAL, ONLINE, OTHER }

    public enum Status { DRAFT, SUBMITTED, VERIFIED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private Student student;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, length = 30)
    private ActivityType activityType;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "organization", length = 200)
    private String organization;

    @Column(name = "description", length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "level", length = 20)
    private Level level;

    @Column(name = "role_or_position", length = 100)
    private String roleOrPosition;

    @Column(name = "achievement_result", length = 150)
    private String achievementResult;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "evidence_url", length = 500)
    private String evidenceUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    /** Latest review decision (verify or reject). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_note", length = 1000)
    private String reviewNote;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

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

    public boolean isEditableByStudent() {
        return status == Status.DRAFT || status == Status.REJECTED;
    }

    /** Records a review decision on a SUBMITTED entry. */
    public void decide(Status decision, User reviewer, String note) {
        status = decision;
        reviewedBy = reviewer;
        reviewedAt = LocalDateTime.now();
        reviewNote = note;
    }

    public void submit() {
        status = Status.SUBMITTED;
        submittedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }
    public ActivityType getActivityType() { return activityType; }
    public void setActivityType(ActivityType activityType) { this.activityType = activityType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getOrganization() { return organization; }
    public void setOrganization(String organization) { this.organization = organization; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Level getLevel() { return level; }
    public void setLevel(Level level) { this.level = level; }
    public String getRoleOrPosition() { return roleOrPosition; }
    public void setRoleOrPosition(String roleOrPosition) { this.roleOrPosition = roleOrPosition; }
    public String getAchievementResult() { return achievementResult; }
    public void setAchievementResult(String achievementResult) { this.achievementResult = achievementResult; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getEvidenceUrl() { return evidenceUrl; }
    public void setEvidenceUrl(String evidenceUrl) { this.evidenceUrl = evidenceUrl; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public User getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(User reviewedBy) { this.reviewedBy = reviewedBy; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }
    public Long getVersion() { return version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

package com.java.erp.modules.gradebook.entity;

import com.java.erp.modules.system.entity.Student;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One student's raw result for one assessment. This is the only place raw marks are stored.
 * GRADED requires marks; ABSENT and EXEMPTED have none.
 */
@Entity
@Table(name = "student_grades")
public class StudentGrade {

    public enum GradeStatus { GRADED, ABSENT, EXEMPTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_id", nullable = false, updatable = false)
    private Assessment assessment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private Student student;

    @Column(name = "marks", precision = 6, scale = 2)
    private BigDecimal marks;

    @Enumerated(EnumType.STRING)
    @Column(name = "grade_status", nullable = false, length = 20)
    private GradeStatus gradeStatus;

    @Column(name = "remarks", length = 255)
    private String remarks;

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

    public StudentGrade() {
    }

    public StudentGrade(Assessment assessment, Student student) {
        this.assessment = assessment;
        this.student = student;
    }

    public Long getId() { return id; }
    public Assessment getAssessment() { return assessment; }
    public Student getStudent() { return student; }
    public BigDecimal getMarks() { return marks; }
    public void setMarks(BigDecimal marks) { this.marks = marks; }
    public GradeStatus getGradeStatus() { return gradeStatus; }
    public void setGradeStatus(GradeStatus gradeStatus) { this.gradeStatus = gradeStatus; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

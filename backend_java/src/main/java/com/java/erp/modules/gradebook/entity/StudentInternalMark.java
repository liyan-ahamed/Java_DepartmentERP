package com.java.erp.modules.gradebook.entity;

import com.java.erp.modules.academic.entity.Subject;
import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.system.entity.Student;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Calculated internal-assessment summary for one student in one subject offering.
 *
 * Holds calculated totals only; raw CAT / assignment / practical / record marks stay in
 * student_grades. The calculation formulas are not defined yet, so rows are created as
 * PENDING_CALCULATION_CONFIGURATION with every total NULL. A future calculator will load the
 * offering, inspect the subject type, read the FINALIZED assessments grouped by type, apply the
 * agreed formula and write the totals here with status CALCULATED and a calculation_version.
 *
 * THEORY subjects never have a practical component; THEORY_CUM_PRACTICAL subjects may have
 * theory, practical and total.
 */
@Entity
@Table(name = "student_internal_marks")
public class StudentInternalMark {

    public enum CalculationStatus { PENDING_CALCULATION_CONFIGURATION, CALCULATED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_offering_id", nullable = false, updatable = false)
    private SubjectOffering subjectOffering;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private Student student;

    @Column(name = "theory_component_total", precision = 6, scale = 2)
    private BigDecimal theoryComponentTotal;

    @Column(name = "practical_component_total", precision = 6, scale = 2)
    private BigDecimal practicalComponentTotal;

    @Column(name = "total_internal_marks", precision = 6, scale = 2)
    private BigDecimal totalInternalMarks;

    @Enumerated(EnumType.STRING)
    @Column(name = "calculation_status", nullable = false, length = 40)
    private CalculationStatus calculationStatus = CalculationStatus.PENDING_CALCULATION_CONFIGURATION;

    @Column(name = "calculation_version", length = 30)
    private String calculationVersion;

    @Column(name = "calculated_at")
    private LocalDateTime calculatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        checkSubjectStructure();
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        checkSubjectStructure();
        updatedAt = LocalDateTime.now();
    }

    /** A THEORY subject has no practical component (cannot be a DB CHECK: the type lives in subjects). */
    private void checkSubjectStructure() {
        if (practicalComponentTotal != null
                && subjectOffering.getSubject().getSubjectType() == Subject.SubjectType.THEORY) {
            throw new IllegalStateException("A THEORY subject has no practical component");
        }
    }

    public StudentInternalMark() {
    }

    public StudentInternalMark(SubjectOffering subjectOffering, Student student) {
        this.subjectOffering = subjectOffering;
        this.student = student;
    }

    public Long getId() { return id; }
    public SubjectOffering getSubjectOffering() { return subjectOffering; }
    public Student getStudent() { return student; }
    public BigDecimal getTheoryComponentTotal() { return theoryComponentTotal; }
    public void setTheoryComponentTotal(BigDecimal value) { this.theoryComponentTotal = value; }
    public BigDecimal getPracticalComponentTotal() { return practicalComponentTotal; }
    public void setPracticalComponentTotal(BigDecimal value) { this.practicalComponentTotal = value; }
    public BigDecimal getTotalInternalMarks() { return totalInternalMarks; }
    public void setTotalInternalMarks(BigDecimal value) { this.totalInternalMarks = value; }
    public CalculationStatus getCalculationStatus() { return calculationStatus; }
    public void setCalculationStatus(CalculationStatus calculationStatus) { this.calculationStatus = calculationStatus; }
    public String getCalculationVersion() { return calculationVersion; }
    public void setCalculationVersion(String calculationVersion) { this.calculationVersion = calculationVersion; }
    public LocalDateTime getCalculatedAt() { return calculatedAt; }
    public void setCalculatedAt(LocalDateTime calculatedAt) { this.calculatedAt = calculatedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

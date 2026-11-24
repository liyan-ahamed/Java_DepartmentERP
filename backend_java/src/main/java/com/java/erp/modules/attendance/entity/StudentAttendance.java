package com.java.erp.modules.attendance.entity;

import com.java.erp.modules.system.entity.Student;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/** One student's mark in one attendance session. */
@Entity
@Table(name = "student_attendance")
public class StudentAttendance {

    /** PRESENT and ON_DUTY count as attended; ABSENT counts against; EXCUSED is left out of the percentage. */
    public enum AttendanceStatus { PRESENT, ABSENT, ON_DUTY, EXCUSED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attendance_session_id", nullable = false, updatable = false)
    private AttendanceSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private Student student;

    @Enumerated(EnumType.STRING)
    @Column(name = "attendance_status", nullable = false, length = 20)
    private AttendanceStatus status;

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

    public StudentAttendance() {
    }

    public StudentAttendance(AttendanceSession session, Student student) {
        this.session = session;
        this.student = student;
    }

    public Long getId() { return id; }
    public AttendanceSession getSession() { return session; }
    public Student getStudent() { return student; }
    public AttendanceStatus getStatus() { return status; }
    public void setStatus(AttendanceStatus status) { this.status = status; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

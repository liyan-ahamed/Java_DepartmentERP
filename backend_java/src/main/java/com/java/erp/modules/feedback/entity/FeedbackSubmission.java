package com.java.erp.modules.feedback.entity;

import com.java.erp.modules.system.entity.Student;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Records THAT a student submitted a form (duplicate prevention and "submitted" state).
 * It holds no answers and no link to the FeedbackResponse, so anonymous answers cannot be traced back.
 */
@Entity
@Table(name = "feedback_submissions")
public class FeedbackSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feedback_form_id", nullable = false, updatable = false)
    private FeedbackForm form;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private Student student;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private LocalDateTime submittedAt;

    public FeedbackSubmission() {
    }

    public FeedbackSubmission(FeedbackForm form, Student student) {
        this.form = form;
        this.student = student;
        this.submittedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public FeedbackForm getForm() { return form; }
    public Student getStudent() { return student; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
}

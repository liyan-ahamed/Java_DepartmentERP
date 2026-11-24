package com.java.erp.modules.feedback.entity;

import com.java.erp.modules.system.entity.Student;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One set of answers to a form. Random UUID id. For anonymous forms student and submittedAt are
 * always null, so nothing in the row identifies or orders the author.
 */
@Entity
@Table(name = "feedback_responses")
public class FeedbackResponse implements org.springframework.data.domain.Persistable<UUID> {

    /** Ids are assigned in Java, so tell Spring Data when the row is new (avoids a SELECT before INSERT). */
    @Transient
    private boolean isNew = true;

    @PostLoad
    @PostPersist
    void markNotNew() {
        isNew = false;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feedback_form_id", nullable = false, updatable = false)
    private FeedbackForm form;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", updatable = false)
    private Student student;

    @Column(name = "submitted_at", updatable = false)
    private LocalDateTime submittedAt;

    @OneToMany(mappedBy = "response", cascade = CascadeType.ALL)
    private List<FeedbackAnswer> answers = new ArrayList<>();

    public FeedbackResponse() {
    }

    /** identifiedStudent = null for anonymous forms. */
    public FeedbackResponse(FeedbackForm form, Student identifiedStudent) {
        this.id = UUID.randomUUID();
        this.form = form;
        this.student = identifiedStudent;
        this.submittedAt = identifiedStudent == null ? null : LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public FeedbackForm getForm() { return form; }
    public Student getStudent() { return student; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public List<FeedbackAnswer> getAnswers() { return answers; }
}

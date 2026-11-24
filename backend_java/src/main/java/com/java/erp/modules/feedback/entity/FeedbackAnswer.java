package com.java.erp.modules.feedback.entity;

import jakarta.persistence.*;

import java.util.UUID;

/** Exactly one of rating (1-5) or textAnswer is set. Random UUID id (no ordering by insertion). */
@Entity
@Table(name = "feedback_answers")
public class FeedbackAnswer implements org.springframework.data.domain.Persistable<UUID> {

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
    @JoinColumn(name = "feedback_response_id", nullable = false, updatable = false)
    private FeedbackResponse response;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feedback_question_id", nullable = false, updatable = false)
    private FeedbackQuestion question;

    @Column(name = "rating")
    private Short rating;

    @Column(name = "text_answer", length = 2000)
    private String textAnswer;

    public FeedbackAnswer() {
    }

    public FeedbackAnswer(FeedbackResponse response, FeedbackQuestion question, Short rating, String textAnswer) {
        this.id = UUID.randomUUID();
        this.response = response;
        this.question = question;
        this.rating = rating;
        this.textAnswer = textAnswer;
    }

    public UUID getId() { return id; }
    public FeedbackResponse getResponse() { return response; }
    public FeedbackQuestion getQuestion() { return question; }
    public Short getRating() { return rating; }
    public String getTextAnswer() { return textAnswer; }
}

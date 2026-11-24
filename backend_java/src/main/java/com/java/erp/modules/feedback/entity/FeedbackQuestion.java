package com.java.erp.modules.feedback.entity;

import jakarta.persistence.*;

/** RATING questions take 1-5; TEXT questions take free text. */
@Entity
@Table(name = "feedback_questions")
public class FeedbackQuestion {

    public enum QuestionType { RATING, TEXT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feedback_form_id", nullable = false, updatable = false)
    private FeedbackForm form;

    @Column(name = "position", nullable = false)
    private Integer position;

    @Column(name = "question_text", nullable = false, length = 500)
    private String questionText;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, length = 10)
    private QuestionType questionType;

    @Column(name = "required", nullable = false)
    private boolean required = true;

    public FeedbackQuestion() {
    }

    public FeedbackQuestion(FeedbackForm form, int position, String questionText, QuestionType questionType, boolean required) {
        this.form = form;
        this.position = position;
        this.questionText = questionText;
        this.questionType = questionType;
        this.required = required;
    }

    public Long getId() { return id; }
    public FeedbackForm getForm() { return form; }
    public Integer getPosition() { return position; }
    public String getQuestionText() { return questionText; }
    public QuestionType getQuestionType() { return questionType; }
    public boolean isRequired() { return required; }
}

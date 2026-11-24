package com.java.erp.modules.feedback.repository;

import com.java.erp.modules.feedback.entity.FeedbackAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface FeedbackAnswerRepository extends JpaRepository<FeedbackAnswer, UUID> {

    /** Rating counts per question and value (aggregates only, no response or student ids). */
    @Query("SELECT a.question.id AS questionId, a.rating AS rating, COUNT(a) AS total FROM FeedbackAnswer a " +
           "WHERE a.response.form.id = :formId AND a.rating IS NOT NULL GROUP BY a.question.id, a.rating")
    List<RatingCount> countRatings(@Param("formId") Long formId);

    /** Text answers of one form, sorted alphabetically so the order reveals nothing about who wrote them. */
    @Query("SELECT a.question.id AS questionId, a.textAnswer AS text FROM FeedbackAnswer a " +
           "WHERE a.response.form.id = :formId AND a.textAnswer IS NOT NULL ORDER BY a.question.id, a.textAnswer")
    List<TextAnswer> findTextAnswers(@Param("formId") Long formId);

    interface RatingCount {
        Long getQuestionId();
        Short getRating();
        Long getTotal();
    }

    interface TextAnswer {
        Long getQuestionId();
        String getText();
    }
}

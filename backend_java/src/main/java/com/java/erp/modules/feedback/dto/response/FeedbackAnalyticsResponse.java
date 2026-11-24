package com.java.erp.modules.feedback.dto.response;

import com.java.erp.modules.feedback.entity.FeedbackForm;
import com.java.erp.modules.feedback.entity.FeedbackQuestion;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Aggregate results of a form. Contains no student identity. Text answers are sorted
 * alphabetically (not by time), so their order does not hint at who wrote them.
 */
public record FeedbackAnalyticsResponse(
        Long formId,
        String title,
        boolean anonymous,
        FeedbackForm.Status status,
        long eligibleStudents,
        long submissions,
        BigDecimal responseRatePercentage,
        List<QuestionStats> questions
) {
    public record QuestionStats(
            Long questionId,
            Integer position,
            String questionText,
            FeedbackQuestion.QuestionType questionType,
            long answers,
            BigDecimal averageRating,
            Map<Integer, Long> ratingDistribution,
            List<String> textAnswers
    ) {
    }
}

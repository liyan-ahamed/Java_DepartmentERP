package com.java.erp.modules.feedback.dto.response;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Individual responses (HOD only). For anonymous forms there is no student, no time and no stable id,
 * and the list is shuffled; for identified forms the student is shown.
 */
public record FeedbackResponsesResponse(
        Long formId,
        boolean anonymous,
        List<Item> responses
) {
    public record Item(
            Long studentId,
            String registerNumber,
            String studentName,
            LocalDateTime submittedAt,
            List<Answer> answers
    ) {
    }

    public record Answer(Long questionId, Integer rating, String textAnswer) {
    }
}

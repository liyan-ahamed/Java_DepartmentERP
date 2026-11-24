package com.java.erp.modules.feedback.dto.request;

import com.java.erp.modules.feedback.entity.FeedbackForm;
import com.java.erp.modules.feedback.entity.FeedbackQuestion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Target: DEPARTMENT (no id), BATCH (targetBatchId), SECTION (targetSectionId) or
 * SUBJECT_OFFERING (targetSubjectOfferingId). anonymous defaults to true; required defaults to true.
 * Questions are numbered in the order given.
 */
public record FeedbackFormRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 1000) String description,
        @NotNull FeedbackForm.TargetType targetType,
        Long targetBatchId,
        Long targetSectionId,
        Long targetSubjectOfferingId,
        Boolean anonymous,
        @NotEmpty @Size(max = 50) List<@NotNull @Valid Question> questions
) {
    public record Question(
            @NotBlank @Size(max = 500) String questionText,
            @NotNull FeedbackQuestion.QuestionType questionType,
            Boolean required
    ) {
    }
}

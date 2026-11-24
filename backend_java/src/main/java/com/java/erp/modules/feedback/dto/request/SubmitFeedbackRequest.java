package com.java.erp.modules.feedback.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** RATING questions: rating 1-5 and no text. TEXT questions: textAnswer and no rating. */
public record SubmitFeedbackRequest(
        @NotEmpty @Size(max = 50) List<@NotNull @Valid Answer> answers
) {
    public record Answer(
            @NotNull Long questionId,
            Integer rating,
            @Size(max = 2000) String textAnswer
    ) {
    }
}

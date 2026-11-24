package com.java.erp.modules.feedback.dto.response;

import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.feedback.entity.FeedbackForm;
import com.java.erp.modules.feedback.entity.FeedbackQuestion;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A form as shown to HOD (submissionCount set) or to a student (submitted set). Never contains
 * any student identity.
 */
public record FeedbackFormResponse(
        Long id,
        String title,
        String description,
        FeedbackForm.TargetType targetType,
        Long targetBatchId,
        Long targetSectionId,
        Long targetSubjectOfferingId,
        String targetLabel,
        boolean anonymous,
        FeedbackForm.Status status,
        LocalDateTime openedAt,
        LocalDateTime closedAt,
        List<Question> questions,
        Long submissionCount,
        Boolean submitted
) {
    public record Question(Long id, Integer position, String questionText,
                           FeedbackQuestion.QuestionType questionType, boolean required) {
    }

    public static FeedbackFormResponse forHod(FeedbackForm f, long submissionCount) {
        return of(f, submissionCount, null);
    }

    public static FeedbackFormResponse forStudent(FeedbackForm f, boolean submitted) {
        return of(f, null, submitted);
    }

    private static FeedbackFormResponse of(FeedbackForm f, Long submissionCount, Boolean submitted) {
        List<Question> questions = f.getQuestions().stream()
                .map(q -> new Question(q.getId(), q.getPosition(), q.getQuestionText(), q.getQuestionType(), q.isRequired()))
                .toList();
        return new FeedbackFormResponse(f.getId(), f.getTitle(), f.getDescription(), f.getTargetType(),
                f.getTargetBatch() == null ? null : f.getTargetBatch().getId(),
                f.getTargetSection() == null ? null : f.getTargetSection().getId(),
                f.getTargetSubjectOffering() == null ? null : f.getTargetSubjectOffering().getId(),
                targetLabel(f), f.isAnonymous(), f.getStatus(), f.getOpenedAt(), f.getClosedAt(), questions,
                submissionCount, submitted);
    }

    private static String targetLabel(FeedbackForm f) {
        return switch (f.getTargetType()) {
            case DEPARTMENT -> "Whole department";
            case BATCH -> "Batch " + f.getTargetBatch().getBatchName();
            case SECTION -> "Section " + f.getTargetSection().getSectionName() + " (" + f.getTargetSection().getBatch().getBatchName() + ")";
            case SUBJECT_OFFERING -> {
                SubjectOffering o = f.getTargetSubjectOffering();
                yield o.getSubject().getCode() + " " + o.getSubject().getName() + " - section " + o.getSection().getSectionName();
            }
        };
    }
}

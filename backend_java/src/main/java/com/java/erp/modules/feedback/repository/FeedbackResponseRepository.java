package com.java.erp.modules.feedback.repository;

import com.java.erp.modules.feedback.entity.FeedbackResponse;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FeedbackResponseRepository extends JpaRepository<FeedbackResponse, UUID> {

    @EntityGraph(attributePaths = {"student", "answers", "answers.question"})
    List<FeedbackResponse> findByFormId(Long formId);
}

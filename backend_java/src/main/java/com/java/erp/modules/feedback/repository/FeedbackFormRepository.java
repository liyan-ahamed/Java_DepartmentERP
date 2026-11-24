package com.java.erp.modules.feedback.repository;

import com.java.erp.modules.feedback.entity.FeedbackForm;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface FeedbackFormRepository extends JpaRepository<FeedbackForm, Long>, JpaSpecificationExecutor<FeedbackForm> {

    @Override
    @EntityGraph(attributePaths = {"targetBatch", "targetSection", "targetSubjectOffering", "targetSubjectOffering.subject"})
    Page<FeedbackForm> findAll(Specification<FeedbackForm> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"targetBatch", "targetSection", "targetSubjectOffering", "targetSubjectOffering.subject"})
    Optional<FeedbackForm> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"targetBatch", "targetSection", "targetSubjectOffering", "targetSubjectOffering.subject"})
    List<FeedbackForm> findByStatusOrderByOpenedAtDescIdDesc(FeedbackForm.Status status);
}

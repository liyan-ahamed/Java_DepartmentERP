package com.java.erp.modules.feedback.repository;

import com.java.erp.modules.feedback.entity.FeedbackSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;

public interface FeedbackSubmissionRepository extends JpaRepository<FeedbackSubmission, Long> {

    boolean existsByFormIdAndStudentId(Long formId, Long studentId);

    long countByFormId(Long formId);

    @Query("SELECT s.form.id FROM FeedbackSubmission s WHERE s.student.id = :studentId")
    Set<Long> findFormIdsByStudentId(@Param("studentId") Long studentId);
}

package com.java.erp.modules.exams.repository;

import com.java.erp.modules.exams.entity.ExamRegistration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface ExamRegistrationRepository extends JpaRepository<ExamRegistration, Long>,
        JpaSpecificationExecutor<ExamRegistration> {

    @Override
    @EntityGraph(attributePaths = {"examPeriod", "student", "student.section", "reviewedBy"})
    Page<ExamRegistration> findAll(Specification<ExamRegistration> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"examPeriod", "student", "student.section", "reviewedBy"})
    Optional<ExamRegistration> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"examPeriod", "student", "student.section", "reviewedBy"})
    Optional<ExamRegistration> findByExamPeriodIdAndStudentId(Long examPeriodId, Long studentId);

    @EntityGraph(attributePaths = {"examPeriod", "student", "student.section", "reviewedBy"})
    List<ExamRegistration> findByStudentIdOrderByIdDesc(Long studentId);
}

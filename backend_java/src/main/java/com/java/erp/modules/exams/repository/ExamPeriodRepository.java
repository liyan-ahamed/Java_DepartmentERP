package com.java.erp.modules.exams.repository;

import com.java.erp.modules.exams.entity.ExamPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface ExamPeriodRepository extends JpaRepository<ExamPeriod, Long>, JpaSpecificationExecutor<ExamPeriod> {

    List<ExamPeriod> findByStatusOrderByRegistrationEndAscIdAsc(ExamPeriod.Status status);
}

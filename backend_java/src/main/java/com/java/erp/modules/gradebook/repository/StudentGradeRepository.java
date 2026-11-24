package com.java.erp.modules.gradebook.repository;

import com.java.erp.modules.gradebook.entity.StudentGrade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

public interface StudentGradeRepository extends JpaRepository<StudentGrade, Long> {

    List<StudentGrade> findByAssessmentId(Long assessmentId);

    List<StudentGrade> findByAssessmentIdIn(Collection<Long> assessmentIds);

    List<StudentGrade> findByStudentIdAndAssessmentIdIn(Long studentId, Collection<Long> assessmentIds);

    @Query("SELECT MAX(g.marks) FROM StudentGrade g WHERE g.assessment.id = :assessmentId")
    BigDecimal findHighestMarks(@Param("assessmentId") Long assessmentId);
}

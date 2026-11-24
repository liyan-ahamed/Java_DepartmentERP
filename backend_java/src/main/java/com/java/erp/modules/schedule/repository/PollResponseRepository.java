package com.java.erp.modules.schedule.repository;

import com.java.erp.modules.schedule.entity.PollResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;

public interface PollResponseRepository extends JpaRepository<PollResponse, Long> {
    long countByScheduleId(Long scheduleId);
    @EntityGraph(attributePaths = {"student", "student.section", "student.batch"})
    Page<PollResponse> findByScheduleId(Long scheduleId, Pageable pageable);
    boolean existsByScheduleIdAndStudentId(Long scheduleId, Long studentId);
    Optional<PollResponse> findByScheduleIdAndStudentId(Long scheduleId, Long studentId);
}

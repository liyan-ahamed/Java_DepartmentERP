package com.java.erp.modules.schedule.repository;

import com.java.erp.modules.schedule.entity.StudentSchedule;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface StudentScheduleRepository extends JpaRepository<StudentSchedule, Long> {
    Page<StudentSchedule> findByTypeAndCreatedById(StudentSchedule.Type type, Long createdById, Pageable pageable);

    // Current active targets plus prior respondents, retained even if their roster membership changes.
    @Query("SELECT COUNT(s) FROM Student s WHERE (s.active = true AND (" +
           "s.id IN (SELECT st.id FROM StudentSchedule sc JOIN sc.students st WHERE sc.id = :id) OR " +
           "s.section.id IN (SELECT sec.id FROM StudentSchedule sc JOIN sc.sections sec WHERE sc.id = :id))) OR " +
           "s.id IN (SELECT r.student.id FROM PollResponse r WHERE r.schedule.id = :id)")
    long countAssignedStudents(@Param("id") Long id);
    List<StudentSchedule> findByTypeOrderByCreatedAtDesc(StudentSchedule.Type type);

    List<StudentSchedule> findByTypeAndCreatedByIdOrderByCreatedAtDesc(StudentSchedule.Type type, Long createdById);

    @Query("select distinct s from StudentSchedule s left join s.sections sec left join s.students st " +
           "where s.type = :type and (sec.id = :sectionId or st.id = :studentId) order by s.createdAt desc")
    List<StudentSchedule> findAssigned(@Param("type") StudentSchedule.Type type,
                                       @Param("sectionId") Long sectionId,
                                       @Param("studentId") Long studentId);
}

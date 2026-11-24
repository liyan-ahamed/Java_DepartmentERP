package com.java.erp.modules.gradebook.repository;

import com.java.erp.modules.gradebook.entity.StudentInternalMark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StudentInternalMarkRepository extends JpaRepository<StudentInternalMark, Long> {

    List<StudentInternalMark> findBySubjectOfferingId(Long subjectOfferingId);

    List<StudentInternalMark> findByStudentId(Long studentId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM StudentInternalMark m WHERE m.subjectOffering.id = :offeringId")
    void deleteBySubjectOfferingId(@Param("offeringId") Long offeringId);
}

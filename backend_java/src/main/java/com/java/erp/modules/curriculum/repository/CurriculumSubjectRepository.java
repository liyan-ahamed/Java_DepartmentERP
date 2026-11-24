package com.java.erp.modules.curriculum.repository;

import com.java.erp.modules.curriculum.entity.CurriculumSubject;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CurriculumSubjectRepository extends JpaRepository<CurriculumSubject, Long> {

    @EntityGraph(attributePaths = {"subject", "electiveGroup"})
    List<CurriculumSubject> findByCurriculumIdOrderByDisplayOrderAscIdAsc(Long curriculumId);
}

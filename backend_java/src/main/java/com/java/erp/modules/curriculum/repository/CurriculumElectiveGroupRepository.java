package com.java.erp.modules.curriculum.repository;

import com.java.erp.modules.curriculum.entity.CurriculumElectiveGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CurriculumElectiveGroupRepository extends JpaRepository<CurriculumElectiveGroup, Long> {

    List<CurriculumElectiveGroup> findByCurriculumIdOrderByDisplayOrderAscIdAsc(Long curriculumId);
}

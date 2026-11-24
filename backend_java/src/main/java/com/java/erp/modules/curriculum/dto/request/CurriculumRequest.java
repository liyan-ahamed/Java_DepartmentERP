package com.java.erp.modules.curriculum.dto.request;

import java.util.List;

/**
 * The whole curriculum of a cohort + semester (replaces what is stored). version: null when creating, else the stored
 * version. Group id: null for a new group, else the id of a stored group of this curriculum. Validated in the service
 * so every problem is reported at once with its position (e.g. electiveGroups[1].requiredSelections).
 */
public record CurriculumRequest(
        Long version,
        List<Long> compulsorySubjectIds,
        List<ElectiveGroupRequest> electiveGroups
) {
    public record ElectiveGroupRequest(Long id, String name, Integer requiredSelections, List<Long> subjectIds) {
    }
}

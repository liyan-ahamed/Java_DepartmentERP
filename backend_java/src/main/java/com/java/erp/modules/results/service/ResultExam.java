package com.java.erp.modules.results.service;

import com.java.erp.modules.gradebook.entity.Assessment;

/**
 * The exams of the results view. No new storage: END_SEMESTER reads exam_results of END_SEMESTER exam periods;
 * CAT_1 / CAT_2 read FINALIZED gradebook assessments of that assessment type.
 */
public enum ResultExam {
    END_SEMESTER(null),
    CAT_1(Assessment.AssessmentType.CAT_1),
    CAT_2(Assessment.AssessmentType.CAT_2);

    private final Assessment.AssessmentType assessmentType;

    ResultExam(Assessment.AssessmentType assessmentType) {
        this.assessmentType = assessmentType;
    }

    /** The gradebook assessment type, or null for END_SEMESTER. */
    public Assessment.AssessmentType assessmentType() {
        return assessmentType;
    }
}

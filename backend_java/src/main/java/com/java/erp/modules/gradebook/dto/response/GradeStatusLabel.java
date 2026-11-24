package com.java.erp.modules.gradebook.dto.response;

import com.java.erp.modules.gradebook.entity.StudentGrade;

/** Grade status as shown in responses: the stored status, or NOT_GRADED when no grade row exists. */
public final class GradeStatusLabel {

    public static final String NOT_GRADED = "NOT_GRADED";

    private GradeStatusLabel() {
    }

    public static String of(StudentGrade grade) {
        return grade == null ? NOT_GRADED : grade.getGradeStatus().name();
    }
}

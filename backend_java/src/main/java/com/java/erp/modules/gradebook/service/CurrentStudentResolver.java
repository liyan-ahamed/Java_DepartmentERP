package com.java.erp.modules.gradebook.service;

import com.java.erp.common.util.SecurityUtils;
import com.java.erp.exception.BadRequestException;
import com.java.erp.modules.system.entity.Student;
import com.java.erp.modules.system.repository.StudentRepository;
import org.springframework.stereotype.Component;

/** The Student record of the logged-in STUDENT user (same linking rule as the schedule module). */
@Component
public class CurrentStudentResolver {

    private final StudentRepository students;

    public CurrentStudentResolver(StudentRepository students) {
        this.students = students;
    }

    public Student require() {
        return students.findLinkedStudent(SecurityUtils.getCurrentUserId(), SecurityUtils.getCurrentUserEmail())
                .orElseThrow(() -> new BadRequestException("This Student account is not linked to a student record"));
    }
}

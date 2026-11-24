package com.java.erp.modules.gradebook.controller;

import com.java.erp.modules.gradebook.config.ConditionalOnGradebookEnabled;
import com.java.erp.common.response.ApiResponse;
import com.java.erp.modules.gradebook.dto.response.StudentGradeResponse;
import com.java.erp.modules.gradebook.service.GradeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@ConditionalOnGradebookEnabled
@RequestMapping("/grades")
@Tag(name = "My Grades", description = "The logged-in student's published grades")
public class StudentGradeController {

    private final GradeService gradeService;

    public StudentGradeController(GradeService gradeService) {
        this.gradeService = gradeService;
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "My grades", description = "FINALIZED assessments only. Optional filters: subjectId, academicYear, semester.")
    public ApiResponse<List<StudentGradeResponse>> myGrades(
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) Integer semester) {
        return ApiResponse.success("Grades fetched successfully", gradeService.myGrades(subjectId, academicYear, semester));
    }
}

package com.java.erp.support;

import com.java.erp.modules.academic.entity.Subject;
import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.auth.entity.User;
import com.java.erp.modules.gradebook.entity.Assessment;
import com.java.erp.modules.gradebook.entity.StudentGrade;
import com.java.erp.modules.gradebook.repository.AssessmentRepository;
import com.java.erp.modules.gradebook.repository.StudentGradeRepository;
import com.java.erp.modules.gradebook.repository.StudentInternalMarkRepository;
import com.java.erp.modules.gradebook.service.InternalMarksService;
import com.java.erp.modules.system.entity.Section;
import com.java.erp.modules.system.entity.Student;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Fixtures for gradebook tests. The gradebook is disabled by default, so these tests switch it on
 * explicitly: the feature stays verified while it is turned off in the running ERP.
 */
@TestPropertySource(properties = "features.gradebook.enabled=true")
public abstract class GradebookTestBase extends AcademicTestBase {

    @Autowired protected AssessmentRepository assessmentRepository;
    @Autowired protected StudentGradeRepository gradeRepository;
    @Autowired protected StudentInternalMarkRepository internalMarkRepository;
    @Autowired protected InternalMarksService internalMarksService;

    /** Same as an offering created through the API: its empty internal-mark records exist too. */
    @Override
    protected SubjectOffering offering(Subject subject, Section target, User owner) {
        SubjectOffering offering = super.offering(subject, target, owner);
        internalMarksService.ensureRecords(offering);
        return offering;
    }

    protected Assessment assessment(SubjectOffering offering, String title, Assessment.AssessmentType type,
                                    int maxMarks, Integer passMarks) {
        Assessment a = new Assessment();
        a.setSubjectOffering(offering);
        a.setCreatedBy(offering.getStaff());
        a.setTitle(title);
        a.setAssessmentType(type);
        a.setMaxMarks(BigDecimal.valueOf(maxMarks));
        a.setPassMarks(passMarks == null ? null : BigDecimal.valueOf(passMarks));
        a.setAssessmentDate(LocalDate.of(2026, 9, 15));
        return assessmentRepository.saveAndFlush(a);
    }

    protected Assessment draftAssessment(SubjectOffering offering) {
        return assessment(offering, "CAT 1", Assessment.AssessmentType.CAT_1, 50, 25);
    }

    protected Assessment finalizedAssessment(SubjectOffering offering, String title, Assessment.AssessmentType type) {
        Assessment a = assessment(offering, title, type, 50, 25);
        a.setStatus(Assessment.Status.FINALIZED);
        a.setFinalizedAt(LocalDateTime.now());
        return assessmentRepository.saveAndFlush(a);
    }

    protected StudentGrade grade(Assessment assessment, Student student, Integer marks, StudentGrade.GradeStatus status) {
        StudentGrade g = new StudentGrade(assessment, student);
        g.setGradeStatus(status);
        g.setMarks(marks == null ? null : BigDecimal.valueOf(marks));
        return gradeRepository.saveAndFlush(g);
    }

    protected static Map<String, Object> row(Long studentId, Object marks, String status) {
        Map<String, Object> row = new HashMap<>();
        row.put("studentId", studentId);
        row.put("marks", marks);
        row.put("status", status);
        return row;
    }

    protected ResultActions putGrades(User user, Long assessmentId, List<Map<String, Object>> rows) throws Exception {
        return mvc.perform(put("/assessments/" + assessmentId + "/grades").header("Authorization", token(user))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("grades", rows))));
    }
}

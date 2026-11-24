package com.java.erp.modules.gradebook;

import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.audit.entity.AuditLog;
import com.java.erp.modules.audit.repository.AuditLogRepository;
import com.java.erp.modules.auth.entity.User;
import com.java.erp.modules.gradebook.entity.Assessment;
import com.java.erp.modules.gradebook.entity.StudentGrade;
import com.java.erp.modules.notification.entity.Notification;
import com.java.erp.modules.notification.repository.NotificationRepository;
import com.java.erp.modules.system.entity.Student;
import com.java.erp.support.GradebookTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GradebookNotificationAuditIntegrationTest extends GradebookTestBase {

    @Autowired private NotificationRepository notificationRepository;
    @Autowired private AuditLogRepository auditLogRepository;

    private List<Notification> gradeNotifications(User user, Long assessmentId) {
        return notificationRepository.findByRecipientIdAndReadStatus(user.getId(), null, PageRequest.of(0, 50)).stream()
                .filter(n -> "ASSESSMENT".equals(n.getReferenceType()) && assessmentId.equals(n.getReferenceId()))
                .toList();
    }

    private List<AuditLog> auditLogs(String action, Long entityId) {
        return auditLogRepository.findAll().stream()
                .filter(log -> "GRADEBOOK".equals(log.getModule()) && action.equals(log.getAction())
                        && entityId.equals(log.getEntityId()))
                .toList();
    }

    private void finalizeAs(User user, Long assessmentId, int expectedStatus) throws Exception {
        mvc.perform(post("/assessments/" + assessmentId + "/finalize").header("Authorization", token(user)))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void finalizationNotifiesStudentsOfTheClassOnce() throws Exception {
        SubjectOffering offering = theoryOffering();
        StudentLogin linked = studentLogin(section);
        // Not linked by user_id, but the login shares the student's email (fallback link)
        User emailOnly = createUser("ROLE_STUDENT", true);
        Student emailStudent = student(section);
        emailStudent.setEmail(emailOnly.getEmail().toUpperCase());
        studentRepository.saveAndFlush(emailStudent);
        StudentLogin elsewhere = studentLogin(otherSection);
        Assessment cat = draftAssessment(offering);
        // Finalization needs a mark or an explicit absent status for every student of the class.
        grade(cat, linked.student(), 30, StudentGrade.GradeStatus.GRADED);
        grade(cat, emailStudent, null, StudentGrade.GradeStatus.ABSENT);

        finalizeAs(staff, cat.getId(), 200);
        finalizeAs(staff, cat.getId(), 409); // retry: no second round of notifications

        List<Notification> received = gradeNotifications(linked.user(), cat.getId());
        assertThat(received).hasSize(1);
        assertThat(received.get(0).getMessage())
                .isEqualTo("CAT 1 marks for " + offering.getSubject().getName() + " have been published.");
        assertThat(received.get(0).getModule()).isEqualTo("GRADEBOOK");
        assertThat(gradeNotifications(emailOnly, cat.getId())).hasSize(1);
        assertThat(gradeNotifications(elsewhere.user(), cat.getId())).isEmpty();
        assertThat(gradeNotifications(staff, cat.getId())).isEmpty();
    }

    @Test
    void draftGradeEditsDoNotNotify() throws Exception {
        StudentLogin linked = studentLogin(section);
        Assessment cat = draftAssessment(theoryOffering());

        putGrades(staff, cat.getId(), List.of(row(linked.student().getId(), 20, "GRADED"))).andExpect(status().isOk());
        putGrades(staff, cat.getId(), List.of(row(linked.student().getId(), 22, "GRADED"))).andExpect(status().isOk());

        assertThat(gradeNotifications(linked.user(), cat.getId())).isEmpty();
        assertThat(notificationRepository.countByRecipientIdAndReadFalse(linked.user().getId())).isZero();
    }

    @Test
    void bulkGradeAuditStoresCountsNotMarks() throws Exception {
        Student first = student(section);
        Student second = student(section);
        Assessment cat = draftAssessment(theoryOffering());

        putGrades(staff, cat.getId(), List.of(row(first.getId(), 37.25, "GRADED"), row(second.getId(), null, "ABSENT")))
                .andExpect(status().isOk());

        List<AuditLog> logs = auditLogs("GRADE_BULK_UPDATE", cat.getId());
        assertThat(logs).hasSize(1);
        AuditLog log = logs.get(0);
        assertThat(log.getUser().getId()).isEqualTo(staff.getId());
        assertThat(log.getEntityType()).isEqualTo("Assessment");
        assertThat(log.getNewValue())
                .contains("\"assessmentId\":" + cat.getId())
                .contains("\"subjectOfferingId\":" + cat.getSubjectOffering().getId())
                .contains("\"changedGradeCount\":2")
                .doesNotContain("37.25")
                .doesNotContain("\"marks\"")
                .doesNotContain("\"studentId\"")
                .doesNotContain("ABSENT");
    }

    @Test
    void finalizationAndAssessmentChangesAreAudited() throws Exception {
        Assessment cat = draftAssessment(theoryOffering());
        Assessment other = draftAssessment(cat.getSubjectOffering());

        finalizeAs(staff, cat.getId(), 200);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/assessments/" + other.getId()).header("Authorization", token(staff)))
                .andExpect(status().isOk());

        List<AuditLog> finalized = auditLogs("FINALIZE", cat.getId());
        assertThat(finalized).hasSize(1);
        assertThat(finalized.get(0).getNewValue()).contains("\"status\":\"FINALIZED\"");
        assertThat(auditLogs("DELETE", other.getId())).hasSize(1);
    }
}

package com.java.erp.modules.portfolio;

import com.java.erp.modules.audit.entity.AuditLog;
import com.java.erp.modules.audit.repository.AuditLogRepository;
import com.java.erp.modules.auth.entity.User;
import com.java.erp.modules.notification.entity.Notification;
import com.java.erp.modules.notification.repository.NotificationRepository;
import com.java.erp.modules.system.entity.Student;
import com.java.erp.support.PortfolioTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PortfolioNotificationAuditIntegrationTest extends PortfolioTestBase {

    @Autowired private NotificationRepository notificationRepository;
    @Autowired private AuditLogRepository auditLogRepository;

    private List<Notification> notifications(User user, Long entryId) {
        return notificationRepository.findByRecipientIdAndReadStatus(user.getId(), null, PageRequest.of(0, 50)).stream()
                .filter(n -> "PORTFOLIO_ENTRY".equals(n.getReferenceType()) && entryId.equals(n.getReferenceId()))
                .toList();
    }

    private List<AuditLog> auditLogs(String action, Long entryId) {
        return auditLogRepository.findAll().stream()
                .filter(log -> "PORTFOLIO".equals(log.getModule()) && action.equals(log.getAction())
                        && entryId.equals(log.getEntityId()))
                .toList();
    }

    @Test
    void verificationNotifiesStudentOnceEvenWhenRetried() throws Exception {
        Long id = submitted(student, completeEntry("HACKATHON", "Smart India Hackathon 2026"));

        verify(staff, id, null).andExpect(status().isOk());
        verify(staff, id, null).andExpect(status().isConflict());
        verify(hod, id, null).andExpect(status().isConflict());

        List<Notification> received = notifications(student.user(), id);
        assertThat(received).hasSize(1);
        assertThat(received.get(0).getMessage()).isEqualTo("Your Hackathon entry \"Smart India Hackathon 2026\" has been verified.");
        assertThat(received.get(0).getModule()).isEqualTo("PORTFOLIO");
        assertThat(received.get(0).getType()).isEqualTo(Notification.NotificationType.INFO);
        assertThat(notifications(otherStudent.user(), id)).isEmpty();
        assertThat(notifications(staff, id)).isEmpty();
    }

    @Test
    void rejectionNotifiesStudentOnceEvenWhenRetried() throws Exception {
        Long id = submitted(student, completeEntry("CERTIFICATION", "AWS Cloud Practitioner"));

        reject(staff, id, "Please attach the certificate link.").andExpect(status().isOk());
        reject(staff, id, "Please attach the certificate link.").andExpect(status().isConflict());

        List<Notification> received = notifications(student.user(), id);
        assertThat(received).hasSize(1);
        assertThat(received.get(0).getMessage()).isEqualTo("Your Certification entry \"AWS Cloud Practitioner\" needs changes.");
        assertThat(received.get(0).getType()).isEqualTo(Notification.NotificationType.ACTION_REQUIRED);
    }

    @Test
    void eachNewDecisionAfterResubmissionNotifiesAgain() throws Exception {
        Long id = submitted(student);
        reject(staff, id, "Please attach the certificate link.").andExpect(status().isOk());
        submit(student.user(), id).andExpect(status().isOk());
        verify(staff, id, null).andExpect(status().isOk());

        assertThat(notifications(student.user(), id)).extracting(Notification::getTitle)
                .containsExactlyInAnyOrder("Portfolio entry needs changes", "Portfolio entry verified");
    }

    @Test
    void draftEditsAndSubmissionDoNotNotify() throws Exception {
        Long id = draft(student);
        update(student.user(), id, completeEntry("WORKSHOP", "Docker workshop")).andExpect(status().isOk());
        submit(student.user(), id).andExpect(status().isOk());

        assertThat(notifications(student.user(), id)).isEmpty();
        assertThat(notifications(staff, id)).isEmpty();
        assertThat(notificationRepository.countByRecipientIdAndReadFalse(student.user().getId())).isZero();
    }

    @Test
    void studentLinkedOnlyByEmailIsNotified() throws Exception {
        User login = createUser("ROLE_STUDENT", true);
        Student unlinked = student(section);
        unlinked.setEmail(login.getEmail().toUpperCase());
        studentRepository.saveAndFlush(unlinked);
        StudentLogin byEmail = new StudentLogin(login, unlinked);

        Long id = submitted(byEmail);
        verify(staff, id, null).andExpect(status().isOk());
        assertThat(notifications(login, id)).hasSize(1);
    }

    @Test
    void lifecycleIsAuditedWithSummariesOnly() throws Exception {
        Map<String, Object> body = completeEntry("HACKATHON", "Smart India Hackathon 2026");
        body.put("description", "SECRET-DESCRIPTION built a flood alert app in 36 hours with my team.");
        body.put("evidenceUrl", "https://example.org/private-evidence-link");
        Long id = draft(student, body);
        update(student.user(), id, body).andExpect(status().isOk());
        submit(student.user(), id).andExpect(status().isOk());
        reject(staff, id, "PRIVATE-NOTE please attach the certificate.").andExpect(status().isOk());
        submit(student.user(), id).andExpect(status().isOk());
        verify(hod, id, null).andExpect(status().isOk());

        assertThat(auditLogs("CREATE", id)).hasSize(1);
        assertThat(auditLogs("UPDATE", id)).hasSize(1);
        assertThat(auditLogs("SUBMIT", id)).hasSize(2);

        AuditLog rejected = auditLogs("REJECT", id).get(0);
        assertThat(rejected.getUser().getId()).isEqualTo(staff.getId());
        assertThat(rejected.getEntityType()).isEqualTo("PortfolioEntry");
        assertThat(rejected.getNewValue())
                .contains("\"entryId\":" + id)
                .contains("\"studentId\":" + student.student().getId())
                .contains("\"activityType\":\"HACKATHON\"")
                .contains("\"oldStatus\":\"SUBMITTED\"")
                .contains("\"newStatus\":\"REJECTED\"")
                .contains("\"reviewerId\":" + staff.getId());

        AuditLog verified = auditLogs("VERIFY", id).get(0);
        assertThat(verified.getUser().getId()).isEqualTo(hod.getId());
        assertThat(verified.getNewValue()).contains("\"newStatus\":\"VERIFIED\"").contains("\"reviewerId\":" + hod.getId());

        AuditLog submittedLog = auditLogs("SUBMIT", id).get(0);
        assertThat(submittedLog.getNewValue()).contains("\"oldStatus\":\"DRAFT\"").contains("\"newStatus\":\"SUBMITTED\"");

        for (AuditLog log : auditLogRepository.findAll().stream()
                .filter(l -> "PORTFOLIO".equals(l.getModule()) && id.equals(l.getEntityId())).toList()) {
            assertThat(log.getNewValue())
                    .doesNotContain("SECRET-DESCRIPTION")
                    .doesNotContain("private-evidence-link")
                    .doesNotContain("PRIVATE-NOTE");
        }
    }

    @Test
    void deletionIsAudited() throws Exception {
        Long id = draft(student);
        mvc.perform(delete("/portfolio/entries/" + id).header("Authorization", token(student.user())))
                .andExpect(status().isOk());

        List<AuditLog> logs = auditLogs("DELETE", id);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getUser().getId()).isEqualTo(student.user().getId());
        assertThat(logs.get(0).getNewValue()).contains("\"oldStatus\":\"DRAFT\"").contains("\"newStatus\":null");
    }
}

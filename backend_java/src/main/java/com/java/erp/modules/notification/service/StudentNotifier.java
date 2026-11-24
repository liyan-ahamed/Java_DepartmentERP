package com.java.erp.modules.notification.service;

import com.java.erp.modules.auth.entity.User;
import com.java.erp.modules.auth.repository.UserRepository;
import com.java.erp.modules.notification.entity.Notification;
import com.java.erp.modules.notification.repository.NotificationRepository;
import com.java.erp.modules.system.entity.Student;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * In-app notifications to students. A student is reachable when their record is linked to an active
 * login (students.user_id), or, for unlinked records, when their email matches an active STUDENT
 * login (same rule as StudentRepository.findLinkedStudent). Students without a login are skipped.
 */
@Component
public class StudentNotifier {

    public record Message(Notification.NotificationType type, String title, String message, String module,
                          Long referenceId, String referenceType, Notification.NotificationPriority priority) {
    }

    private final NotificationService notifications;
    private final NotificationRepository notificationRepository;
    private final UserRepository users;

    public StudentNotifier(NotificationService notifications, NotificationRepository notificationRepository,
                           UserRepository users) {
        this.notifications = notifications;
        this.notificationRepository = notificationRepository;
        this.users = users;
    }

    /** Sends the message once to each reachable student; returns the number of notifications sent. */
    public int notifyAll(Collection<Student> students, Message message) {
        Set<Long> recipients = recipients(students);
        recipients.forEach(id -> send(id, message));
        return recipients.size();
    }

    public int notifyStudent(Student student, Message message) {
        return notifyAll(List.of(student), message);
    }

    /**
     * Sends unless the recipient already received a notification with the same reference type and id
     * within the cooldown (used for repeatable warnings such as attendance shortage).
     */
    public int notifyWithCooldown(Student student, Message message, Duration cooldown) {
        LocalDateTime since = LocalDateTime.now().minus(cooldown);
        int sent = 0;
        for (Long recipientId : recipients(List.of(student))) {
            if (!notificationRepository.existsByRecipientIdAndReferenceTypeAndReferenceIdAndCreatedAtAfter(
                    recipientId, message.referenceType(), message.referenceId(), since)) {
                send(recipientId, message);
                sent++;
            }
        }
        return sent;
    }

    private void send(Long recipientId, Message m) {
        notifications.send(recipientId, m.type(), m.title(), m.message(), m.module(), m.referenceId(),
                m.referenceType(), m.priority());
    }

    private Set<Long> recipients(Collection<Student> students) {
        Set<Long> recipients = new LinkedHashSet<>();
        Set<String> unlinkedEmails = new HashSet<>();
        for (Student student : students) {
            User user = student.getUser();
            if (user != null) {
                if (user.isActive()) recipients.add(user.getId());
            } else if (student.getEmail() != null && !student.getEmail().isBlank()) {
                unlinkedEmails.add(student.getEmail().trim().toLowerCase(Locale.ROOT));
            }
        }
        if (!unlinkedEmails.isEmpty()) {
            users.findActiveStudentUsersByEmails(unlinkedEmails).forEach(u -> recipients.add(u.getId()));
        }
        return recipients;
    }
}

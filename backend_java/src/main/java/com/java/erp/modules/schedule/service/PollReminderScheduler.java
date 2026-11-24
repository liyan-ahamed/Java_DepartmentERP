package com.java.erp.modules.schedule.service;

import com.java.erp.modules.auth.entity.User;
import com.java.erp.modules.auth.repository.UserRepository;
import com.java.erp.modules.notification.entity.Notification;
import com.java.erp.modules.notification.repository.NotificationRepository;
import com.java.erp.modules.notification.service.NotificationService;
import com.java.erp.modules.schedule.entity.StudentSchedule;
import com.java.erp.modules.schedule.repository.PollResponseRepository;
import com.java.erp.modules.schedule.repository.StudentScheduleRepository;
import com.java.erp.modules.system.entity.Student;
import com.java.erp.modules.system.repository.StudentRepository;
import org.slf4j.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

/**
 * Hourly job that reminds students about polls they have not answered.
 * A student gets at most one reminder per poll per {@link #REMINDER_INTERVAL}, and
 * reminders stop once the student responds, once the poll's due date/time has passed,
 * or (for polls without a due date) {@link #MAX_POLL_AGE} after the poll was created.
 */
@Component
public class PollReminderScheduler {
    static final String REFERENCE_TYPE = "POLL";
    static final Duration REMINDER_INTERVAL = Duration.ofHours(24);
    static final Duration MAX_POLL_AGE = Duration.ofDays(7);

    private static final Logger logger = LoggerFactory.getLogger(PollReminderScheduler.class);
    private final StudentScheduleRepository schedules;
    private final PollResponseRepository responses;
    private final StudentRepository students;
    private final UserRepository users;
    private final NotificationService notifications;
    private final NotificationRepository notificationRepository;

    public PollReminderScheduler(StudentScheduleRepository schedules, PollResponseRepository responses,
            StudentRepository students, UserRepository users, NotificationService notifications,
            NotificationRepository notificationRepository) {
        this.schedules = schedules; this.responses = responses; this.students = students;
        this.users = users; this.notifications = notifications; this.notificationRepository = notificationRepository;
    }

    @Scheduled(fixedRateString = "${app.poll-reminder.interval-ms:3600000}",
               initialDelayString = "${app.poll-reminder.initial-delay-ms:3600000}")
    @Transactional
    public void remindStudentsWithUnansweredPolls() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastReminderCutoff = now.minus(REMINDER_INTERVAL);
        int sent = 0;
        for (StudentSchedule poll : schedules.findByTypeOrderByCreatedAtDesc(StudentSchedule.Type.POLL)) {
            if (!isOpen(poll, now)) continue;
            if (poll.getCreatedBy().getRoles().stream().noneMatch(role -> "ROLE_STAFF".equals(role.getName()))) continue;

            Map<Long, Student> assigned = new LinkedHashMap<>();
            poll.getStudents().forEach(student -> assigned.put(student.getId(), student));
            Set<Long> sectionIds = new HashSet<>();
            poll.getSections().forEach(section -> sectionIds.add(section.getId()));
            if (!sectionIds.isEmpty()) {
                students.findDistinctBySectionIdInAndActiveTrue(sectionIds)
                    .forEach(student -> assigned.put(student.getId(), student));
            }

            for (Student student : assigned.values()) {
                if (!student.isActive() || responses.existsByScheduleIdAndStudentId(poll.getId(), student.getId())) continue;
                User recipient = student.getUser();
                if (recipient == null && student.getEmail() != null) recipient = users.findByEmail(student.getEmail()).orElse(null);
                if (recipient == null || !recipient.isActive() || recipient.getRoles().stream()
                        .noneMatch(role -> "ROLE_STUDENT".equals(role.getName()))) continue;
                if (notificationRepository.existsByRecipientIdAndReferenceTypeAndReferenceIdAndCreatedAtAfter(
                        recipient.getId(), REFERENCE_TYPE, poll.getId(), lastReminderCutoff)) continue;

                notifications.send(recipient.getId(), Notification.NotificationType.ACTION_REQUIRED,
                    "Unanswered Poll", "Reminder: You have an unanswered poll. Please respond.",
                    "SCHEDULING", poll.getId(), REFERENCE_TYPE, Notification.NotificationPriority.HIGH);
                sent++;
            }
        }
        if (sent > 0) logger.info("Sent {} unanswered poll reminder notification(s)", sent);
    }

    /** A poll is still worth reminding about until its due date/time, or for MAX_POLL_AGE if it has none. */
    static boolean isOpen(StudentSchedule poll, LocalDateTime now) {
        if (poll.getDueDate() != null) {
            LocalTime dueTime = poll.getDueTime() != null ? poll.getDueTime() : LocalTime.MAX;
            return now.isBefore(LocalDateTime.of(poll.getDueDate(), dueTime));
        }
        return poll.getCreatedAt() != null && now.isBefore(poll.getCreatedAt().plus(MAX_POLL_AGE));
    }
}

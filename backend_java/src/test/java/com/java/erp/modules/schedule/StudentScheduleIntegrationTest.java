package com.java.erp.modules.schedule;

import com.java.erp.modules.auth.entity.User;
import com.java.erp.modules.notification.entity.Notification;
import com.java.erp.modules.notification.repository.NotificationRepository;
import com.java.erp.modules.notification.service.NotificationService;
import com.java.erp.modules.schedule.entity.StudentSchedule;
import com.java.erp.modules.schedule.repository.StudentScheduleRepository;
import com.java.erp.modules.schedule.service.PollReminderScheduler;
import com.java.erp.modules.system.entity.Batch;
import com.java.erp.modules.system.entity.Section;
import com.java.erp.modules.system.entity.Student;
import com.java.erp.modules.system.repository.BatchRepository;
import com.java.erp.modules.system.repository.SectionRepository;
import com.java.erp.modules.system.repository.StudentRepository;
import com.java.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StudentScheduleIntegrationTest extends IntegrationTestBase {

    @Autowired private BatchRepository batchRepository;
    @Autowired private SectionRepository sectionRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private StudentScheduleRepository scheduleRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NotificationService notificationService;
    @Autowired private PollReminderScheduler reminderScheduler;

    private User staff;
    private Section section;
    private Section otherSection;

    @BeforeEach
    void setUp() {
        staff = createUser("ROLE_STAFF", true);
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Batch batch = batchRepository.save(new Batch("TB-" + unique, 2025, 2029, (short) 1));
        section = sectionRepository.save(new Section(batch, "A"));
        otherSection = sectionRepository.save(new Section(batch, "B"));
    }

    private User studentIn(Section target) {
        User user = createUser("ROLE_STUDENT", true);
        Student student = new Student("TR" + UUID.randomUUID().toString().substring(0, 8), user.getName(), null);
        student.setBatch(target.getBatch());
        student.setSection(target);
        student.setEmail(user.getEmail());
        student.setUser(user);
        studentRepository.saveAndFlush(student);
        return user;
    }

    private Map<String, Object> pollFor(Section target) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "POLL");
        body.put("title", "Preferred lab slot");
        body.put("section_ids", List.of(target.getId()));
        body.put("options", List.of("Morning", "Afternoon"));
        return body;
    }

    private long createPoll(Section target) throws Exception {
        String body = mvc.perform(post("/student-schedules").header("Authorization", bearer(staff))
                        .contentType(MediaType.APPLICATION_JSON).content(json(pollFor(target))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("id").asLong();
    }

    private StudentSchedule savePoll(LocalDate dueDate) {
        StudentSchedule poll = new StudentSchedule();
        poll.setCreatedBy(staff);
        poll.setType(StudentSchedule.Type.POLL);
        poll.setTitle("Reminder poll");
        poll.setDueDate(dueDate);
        poll.getSections().add(section);
        poll.getOptions().addAll(List.of("Yes", "No"));
        return scheduleRepository.saveAndFlush(poll);
    }

    private long reminderCount(User user, Long pollId) {
        return notificationRepository.findByRecipientIdAndReadStatus(user.getId(), null, PageRequest.of(0, 50))
                .stream()
                .filter(n -> "POLL".equals(n.getReferenceType()) && pollId.equals(n.getReferenceId()))
                .count();
    }

    @Test
    void assignedStudentRespondsOnceAndOthersAreForbidden() throws Exception {
        long pollId = createPoll(section);
        String assigned = bearer(studentIn(section));
        String unassigned = bearer(studentIn(otherSection));
        String answer = json(Map.of("option_index", 0));

        mvc.perform(post("/student-schedules/" + pollId + "/response").header("Authorization", unassigned)
                        .contentType(MediaType.APPLICATION_JSON).content(answer))
                .andExpect(status().isForbidden());
        mvc.perform(post("/student-schedules/" + pollId + "/response").header("Authorization", assigned)
                        .contentType(MediaType.APPLICATION_JSON).content(answer))
                .andExpect(status().isOk());
        mvc.perform(post("/student-schedules/" + pollId + "/response").header("Authorization", assigned)
                        .contentType(MediaType.APPLICATION_JSON).content(answer))
                .andExpect(status().isConflict());
        mvc.perform(post("/student-schedules/999999999/response").header("Authorization", assigned)
                        .contentType(MediaType.APPLICATION_JSON).content(answer))
                .andExpect(status().isNotFound());
    }

    @Test
    void studentsAndHodCannotCreateSchedules() throws Exception {
        for (User user : List.of(studentIn(section), createUser("ROLE_HOD", true))) {
            mvc.perform(post("/student-schedules").header("Authorization", bearer(user))
                            .contentType(MediaType.APPLICATION_JSON).content(json(pollFor(section))))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void invalidInputIsBadRequestNotServerError() throws Exception {
        String token = bearer(staff);

        Map<String, Object> badType = pollFor(section);
        badType.put("type", "SURVEY");
        mvc.perform(post("/student-schedules").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(json(badType)))
                .andExpect(status().isBadRequest());

        Map<String, Object> badDate = pollFor(section);
        badDate.put("type", "DEADLINE");
        badDate.put("due_date", "not-a-date");
        mvc.perform(post("/student-schedules").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(json(badDate)))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/student-schedules").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{ not json"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/student-schedules").param("type", "MEETING").header("Authorization", token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reminderIsSentOnceNotEveryRun() {
        User student = studentIn(section);
        StudentSchedule poll = savePoll(null);

        reminderScheduler.remindStudentsWithUnansweredPolls();
        reminderScheduler.remindStudentsWithUnansweredPolls();
        reminderScheduler.remindStudentsWithUnansweredPolls();

        assertThat(reminderCount(student, poll.getId())).isEqualTo(1);
    }

    @Test
    void noReminderAfterRespondingOrAfterDueDate() throws Exception {
        User responder = studentIn(section);
        StudentSchedule openPoll = savePoll(null);
        mvc.perform(post("/student-schedules/" + openPoll.getId() + "/response")
                        .header("Authorization", bearer(responder))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("option_index", 1))))
                .andExpect(status().isOk());
        StudentSchedule expiredPoll = savePoll(LocalDate.now().minusDays(1));

        reminderScheduler.remindStudentsWithUnansweredPolls();

        assertThat(reminderCount(responder, openPoll.getId())).isZero();
        assertThat(reminderCount(responder, expiredPoll.getId())).isZero();
    }

    @Test
    void usersCanOnlyTouchTheirOwnNotifications() throws Exception {
        User owner = createUser("ROLE_STAFF", true);
        notificationService.send(owner.getId(), Notification.NotificationType.INFO, "Hello", "Message",
                "TEST", null, null, Notification.NotificationPriority.NORMAL);
        Long id = notificationRepository.findByRecipientIdAndReadStatus(owner.getId(), null, PageRequest.of(0, 1))
                .getContent().get(0).getId();

        mvc.perform(patch("/notifications/" + id + "/read").header("Authorization", bearer(staff)))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/notifications/" + id + "/read").header("Authorization", bearer(owner)))
                .andExpect(status().isOk());
        mvc.perform(patch("/notifications/999999999/read").header("Authorization", bearer(owner)))
                .andExpect(status().isNotFound());
    }
}

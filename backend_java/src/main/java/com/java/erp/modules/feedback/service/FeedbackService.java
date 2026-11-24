package com.java.erp.modules.feedback.service;

import com.java.erp.common.response.PagedResponse;
import com.java.erp.common.util.SecurityUtils;
import com.java.erp.exception.BadRequestException;
import com.java.erp.exception.ConflictException;
import com.java.erp.exception.ForbiddenException;
import com.java.erp.exception.ResourceNotFoundException;
import com.java.erp.modules.academic.repository.SubjectOfferingRepository;
import com.java.erp.modules.audit.service.AuditRecorder;
import com.java.erp.modules.auth.repository.UserRepository;
import com.java.erp.modules.enrollment.service.EnrollmentRoster;
import com.java.erp.modules.feedback.dto.request.FeedbackFormRequest;
import com.java.erp.modules.feedback.dto.request.SubmitFeedbackRequest;
import com.java.erp.modules.feedback.dto.response.FeedbackAnalyticsResponse;
import com.java.erp.modules.feedback.dto.response.FeedbackFormResponse;
import com.java.erp.modules.feedback.dto.response.FeedbackResponsesResponse;
import com.java.erp.modules.feedback.entity.*;
import com.java.erp.modules.feedback.repository.FeedbackAnswerRepository;
import com.java.erp.modules.feedback.repository.FeedbackFormRepository;
import com.java.erp.modules.feedback.repository.FeedbackResponseRepository;
import com.java.erp.modules.feedback.repository.FeedbackSubmissionRepository;
import com.java.erp.modules.gradebook.service.CurrentStudentResolver;
import com.java.erp.modules.notification.entity.Notification;
import com.java.erp.modules.notification.service.StudentNotifier;
import com.java.erp.modules.system.entity.Student;
import com.java.erp.modules.system.repository.BatchRepository;
import com.java.erp.modules.system.repository.SectionRepository;
import com.java.erp.modules.system.repository.StudentRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Academic feedback. HOD creates forms (DRAFT), opens and closes them, and sees aggregate results;
 * targeted students submit once while a form is OPEN. STAFF have no access.
 *
 * Anonymity: who submitted is kept in feedback_submissions (to prevent a second submission); the
 * answers live in feedback_responses / feedback_answers with random UUIDs and, for anonymous forms,
 * no student and no time. Nothing links a submission row to a response row, and no API returns a
 * student for an anonymous form. Submissions are deliberately not written to the audit log (the
 * audit row would name the student).
 */
@Service
public class FeedbackService {

    static final String MODULE = "FEEDBACK";
    static final String ENTITY_TYPE = "FeedbackForm";

    private final FeedbackFormRepository forms;
    private final FeedbackSubmissionRepository submissions;
    private final FeedbackResponseRepository responses;
    private final FeedbackAnswerRepository answers;
    private final StudentRepository students;
    private final BatchRepository batches;
    private final SectionRepository sections;
    private final SubjectOfferingRepository offerings;
    private final UserRepository users;
    private final EnrollmentRoster roster;
    private final CurrentStudentResolver currentStudent;
    private final StudentNotifier notifier;
    private final AuditRecorder audit;

    public FeedbackService(FeedbackFormRepository forms, FeedbackSubmissionRepository submissions,
                           FeedbackResponseRepository responses, FeedbackAnswerRepository answers,
                           StudentRepository students, BatchRepository batches, SectionRepository sections,
                           SubjectOfferingRepository offerings, UserRepository users, EnrollmentRoster roster,
                           CurrentStudentResolver currentStudent, StudentNotifier notifier, AuditRecorder audit) {
        this.forms = forms;
        this.submissions = submissions;
        this.responses = responses;
        this.answers = answers;
        this.students = students;
        this.batches = batches;
        this.sections = sections;
        this.offerings = offerings;
        this.users = users;
        this.roster = roster;
        this.currentStudent = currentStudent;
        this.notifier = notifier;
        this.audit = audit;
    }

    // ---- HOD ----

    @Transactional
    public FeedbackFormResponse create(FeedbackFormRequest request) {
        FeedbackForm form = new FeedbackForm();
        form.setCreatedBy(users.getReferenceById(SecurityUtils.getCurrentUserId()));
        apply(form, request);
        request.questions().forEach(q -> addQuestion(form, q));
        FeedbackForm saved = forms.saveAndFlush(form);
        log("CREATE", saved, null);
        return FeedbackFormResponse.forHod(saved, 0);
    }

    /** DRAFT only; the question list is replaced. */
    @Transactional
    public FeedbackFormResponse update(Long id, FeedbackFormRequest request) {
        FeedbackForm form = require(id);
        if (form.getStatus() != FeedbackForm.Status.DRAFT) {
            throw new ConflictException("Only a DRAFT feedback form can be edited");
        }
        apply(form, request);
        // Delete the old questions before inserting new ones (positions are unique per form).
        form.getQuestions().clear();
        forms.flush();
        request.questions().forEach(q -> addQuestion(form, q));
        forms.saveAndFlush(form);
        log("UPDATE", form, FeedbackForm.Status.DRAFT);
        return FeedbackFormResponse.forHod(form, 0);
    }

    /** DRAFT -> OPEN; targeted students with a login are notified once. */
    @Transactional
    public FeedbackFormResponse open(Long id) {
        FeedbackForm form = require(id);
        if (form.getStatus() != FeedbackForm.Status.DRAFT) {
            throw new ConflictException("Only a DRAFT feedback form can be opened (it is " + form.getStatus() + ")");
        }
        form.setStatus(FeedbackForm.Status.OPEN);
        form.setOpenedAt(LocalDateTime.now());
        forms.saveAndFlush(form);
        int notified = notifier.notifyAll(targetedStudents(form), new StudentNotifier.Message(
                Notification.NotificationType.ACTION_REQUIRED, "Feedback requested: " + form.getTitle(),
                "Please fill in the feedback form \"" + form.getTitle() + "\"" + (form.isAnonymous() ? " (anonymous)." : "."),
                MODULE, form.getId(), "FEEDBACK_FORM", Notification.NotificationPriority.NORMAL));
        Map<String, Object> m = summary(form, FeedbackForm.Status.DRAFT);
        m.put("notified", notified);
        audit.record("OPEN", MODULE, ENTITY_TYPE, id, m);
        return FeedbackFormResponse.forHod(form, 0);
    }

    /** OPEN -> CLOSED: no further submissions. */
    @Transactional
    public FeedbackFormResponse close(Long id) {
        FeedbackForm form = require(id);
        if (form.getStatus() != FeedbackForm.Status.OPEN) {
            throw new ConflictException("Only an OPEN feedback form can be closed (it is " + form.getStatus() + ")");
        }
        form.setStatus(FeedbackForm.Status.CLOSED);
        form.setClosedAt(LocalDateTime.now());
        forms.saveAndFlush(form);
        log("CLOSE", form, FeedbackForm.Status.OPEN);
        return FeedbackFormResponse.forHod(form, submissions.countByFormId(id));
    }

    @Transactional(readOnly = true)
    public PagedResponse<FeedbackFormResponse> list(FeedbackForm.Status status, FeedbackForm.TargetType targetType, int page, int size) {
        Specification<FeedbackForm> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (status != null) p.add(cb.equal(root.get("status"), status));
            if (targetType != null) p.add(cb.equal(root.get("targetType"), targetType));
            return cb.and(p.toArray(Predicate[]::new));
        };
        Page<FeedbackForm> result = forms.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Order.desc("id"))));
        return PagedResponse.of(result.getContent().stream()
                .map(f -> FeedbackFormResponse.forHod(f, submissions.countByFormId(f.getId()))).toList(), result);
    }

    /** HOD: any form. STUDENT: forms targeted to them that are OPEN or that they submitted. */
    @Transactional(readOnly = true)
    public FeedbackFormResponse get(Long id) {
        FeedbackForm form = require(id);
        if (SecurityUtils.hasAuthority("ROLE_HOD")) {
            return FeedbackFormResponse.forHod(form, submissions.countByFormId(id));
        }
        Student student = currentStudent.require();
        boolean submitted = submissions.existsByFormIdAndStudentId(id, student.getId());
        if (form.getStatus() == FeedbackForm.Status.DRAFT || !isTargeted(form, student)) {
            throw new ResourceNotFoundException("FeedbackForm", "id", id);
        }
        if (form.getStatus() == FeedbackForm.Status.CLOSED && !submitted) {
            throw new ResourceNotFoundException("FeedbackForm", "id", id);
        }
        return FeedbackFormResponse.forStudent(form, submitted);
    }

    /** Aggregates only: average and distribution per rating question, text answers sorted alphabetically. */
    @Transactional(readOnly = true)
    public FeedbackAnalyticsResponse analytics(Long id) {
        FeedbackForm form = require(id);
        Map<Long, Map<Integer, Long>> distribution = new HashMap<>();
        for (FeedbackAnswerRepository.RatingCount c : answers.countRatings(id)) {
            distribution.computeIfAbsent(c.getQuestionId(), k -> new TreeMap<>()).put(c.getRating().intValue(), c.getTotal());
        }
        Map<Long, List<String>> texts = new HashMap<>();
        for (FeedbackAnswerRepository.TextAnswer t : answers.findTextAnswers(id)) {
            texts.computeIfAbsent(t.getQuestionId(), k -> new ArrayList<>()).add(t.getText());
        }
        List<FeedbackAnalyticsResponse.QuestionStats> stats = new ArrayList<>();
        for (FeedbackQuestion q : form.getQuestions()) {
            if (q.getQuestionType() == FeedbackQuestion.QuestionType.RATING) {
                Map<Integer, Long> dist = new TreeMap<>();
                for (int r = 1; r <= 5; r++) dist.put(r, 0L);
                dist.putAll(distribution.getOrDefault(q.getId(), Map.of()));
                long count = dist.values().stream().mapToLong(Long::longValue).sum();
                long sum = dist.entrySet().stream().mapToLong(e -> e.getKey() * e.getValue()).sum();
                BigDecimal avg = count == 0 ? null : BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
                stats.add(new FeedbackAnalyticsResponse.QuestionStats(q.getId(), q.getPosition(), q.getQuestionText(),
                        q.getQuestionType(), count, avg, dist, null));
            } else {
                List<String> list = texts.getOrDefault(q.getId(), List.of());
                stats.add(new FeedbackAnalyticsResponse.QuestionStats(q.getId(), q.getPosition(), q.getQuestionText(),
                        q.getQuestionType(), list.size(), null, null, list));
            }
        }
        long eligible = targetedStudents(form).size();
        long submitted = submissions.countByFormId(id);
        BigDecimal rate = eligible == 0 ? null
                : BigDecimal.valueOf(submitted * 100).divide(BigDecimal.valueOf(eligible), 2, RoundingMode.HALF_UP);
        return new FeedbackAnalyticsResponse(id, form.getTitle(), form.isAnonymous(), form.getStatus(), eligible,
                submitted, rate, stats);
    }

    /** Individual responses. Anonymous forms: no student, no time, shuffled order. */
    @Transactional(readOnly = true)
    public FeedbackResponsesResponse responses(Long id) {
        FeedbackForm form = require(id);
        List<FeedbackResponsesResponse.Item> items = new ArrayList<>();
        for (FeedbackResponse r : responses.findByFormId(id)) {
            List<FeedbackResponsesResponse.Answer> list = r.getAnswers().stream()
                    .sorted(Comparator.comparing(a -> a.getQuestion().getPosition()))
                    .map(a -> new FeedbackResponsesResponse.Answer(a.getQuestion().getId(),
                            a.getRating() == null ? null : a.getRating().intValue(), a.getTextAnswer()))
                    .toList();
            Student s = form.isAnonymous() ? null : r.getStudent();
            items.add(new FeedbackResponsesResponse.Item(s == null ? null : s.getId(), s == null ? null : s.getRegisterNumber(),
                    s == null ? null : s.getName(), form.isAnonymous() ? null : r.getSubmittedAt(), list));
        }
        if (form.isAnonymous()) {
            Collections.shuffle(items);
        } else {
            items.sort(Comparator.comparing(FeedbackResponsesResponse.Item::registerNumber, Comparator.nullsLast(Comparator.naturalOrder())));
        }
        return new FeedbackResponsesResponse(id, form.isAnonymous(), items);
    }

    // ---- STUDENT ----

    /** OPEN forms targeted to the student, plus closed ones they submitted; with their submitted flag. */
    @Transactional(readOnly = true)
    public List<FeedbackFormResponse> myForms() {
        Student student = currentStudent.require();
        Set<Long> submitted = submissions.findFormIdsByStudentId(student.getId());
        List<FeedbackFormResponse> result = new ArrayList<>();
        for (FeedbackForm form : forms.findByStatusOrderByOpenedAtDescIdDesc(FeedbackForm.Status.OPEN)) {
            if (isTargeted(form, student)) result.add(FeedbackFormResponse.forStudent(form, submitted.contains(form.getId())));
        }
        for (FeedbackForm form : forms.findByStatusOrderByOpenedAtDescIdDesc(FeedbackForm.Status.CLOSED)) {
            if (submitted.contains(form.getId())) result.add(FeedbackFormResponse.forStudent(form, true));
        }
        return result;
    }

    /** One submission per student per form, OPEN forms only. Returns the form with submitted = true (no answers echoed). */
    @Transactional
    public FeedbackFormResponse submit(Long id, SubmitFeedbackRequest request) {
        Student student = currentStudent.require();
        FeedbackForm form = require(id);
        if (form.getStatus() == FeedbackForm.Status.DRAFT || !isTargeted(form, student)) {
            throw new ForbiddenException("This feedback form is not available to you");
        }
        if (form.getStatus() != FeedbackForm.Status.OPEN) {
            throw new ConflictException("This feedback form is closed");
        }
        if (submissions.existsByFormIdAndStudentId(id, student.getId())) {
            throw new ConflictException("You have already submitted this feedback form");
        }

        Map<Long, FeedbackQuestion> questions = new LinkedHashMap<>();
        form.getQuestions().forEach(q -> questions.put(q.getId(), q));
        List<String> errors = new ArrayList<>();
        Map<Long, SubmitFeedbackRequest.Answer> given = new LinkedHashMap<>();
        for (int i = 0; i < request.answers().size(); i++) {
            SubmitFeedbackRequest.Answer a = request.answers().get(i);
            String row = "answers[" + i + "] (questionId " + a.questionId() + "): ";
            FeedbackQuestion q = questions.get(a.questionId());
            boolean hasText = a.textAnswer() != null && !a.textAnswer().isBlank();
            if (q == null) errors.add(row + "question does not belong to this form");
            else if (given.putIfAbsent(a.questionId(), a) != null) errors.add(row + "question is answered more than once");
            else if (q.getQuestionType() == FeedbackQuestion.QuestionType.RATING) {
                if (a.rating() == null || a.rating() < 1 || a.rating() > 5) errors.add(row + "rating must be between 1 and 5");
                if (a.textAnswer() != null) errors.add(row + "a rating question takes no text answer");
            } else {
                if (!hasText) errors.add(row + "text answer is required");
                if (a.rating() != null) errors.add(row + "a text question takes no rating");
            }
        }
        for (FeedbackQuestion q : questions.values()) {
            if (q.isRequired() && !given.containsKey(q.getId())) {
                errors.add("Question " + q.getPosition() + " is required");
            }
        }
        if (!errors.isEmpty()) {
            throw new BadRequestException("Feedback validation failed", errors);
        }

        submissions.save(new FeedbackSubmission(form, student));
        FeedbackResponse response = new FeedbackResponse(form, form.isAnonymous() ? null : student);
        for (SubmitFeedbackRequest.Answer a : given.values()) {
            FeedbackQuestion q = questions.get(a.questionId());
            response.getAnswers().add(q.getQuestionType() == FeedbackQuestion.QuestionType.RATING
                    ? new FeedbackAnswer(response, q, a.rating().shortValue(), null)
                    : new FeedbackAnswer(response, q, null, a.textAnswer().trim()));
        }
        responses.save(response);
        responses.flush();
        return FeedbackFormResponse.forStudent(form, true);
    }

    // ---- helpers ----

    private boolean isTargeted(FeedbackForm form, Student student) {
        return switch (form.getTargetType()) {
            case DEPARTMENT -> true;
            case BATCH -> student.getBatch().getId().equals(form.getTargetBatch().getId());
            case SECTION -> student.getSection().getId().equals(form.getTargetSection().getId());
            case SUBJECT_OFFERING -> roster.isEnrolled(student.getId(), form.getTargetSubjectOffering().getId(),
                    EnrollmentRoster.CURRENT_OR_COMPLETED);
        };
    }

    private Collection<Student> targetedStudents(FeedbackForm form) {
        return switch (form.getTargetType()) {
            case DEPARTMENT -> students.findByActiveTrueOrderByNameAsc();
            case BATCH -> students.findByBatchId(form.getTargetBatch().getId()).stream().filter(Student::isActive).toList();
            case SECTION -> students.findBySectionIdAndActiveTrueOrderByRegisterNumberAsc(form.getTargetSection().getId());
            case SUBJECT_OFFERING -> roster.students(form.getTargetSubjectOffering().getId(),
                    EnrollmentRoster.CURRENT_OR_COMPLETED).values();
        };
    }

    private void apply(FeedbackForm form, FeedbackFormRequest r) {
        form.setTitle(r.title().trim());
        form.setDescription(r.description() == null || r.description().isBlank() ? null : r.description().trim());
        form.setAnonymous(r.anonymous() == null || r.anonymous());
        form.setTargetType(r.targetType());
        form.setTargetBatch(null);
        form.setTargetSection(null);
        form.setTargetSubjectOffering(null);
        switch (r.targetType()) {
            case DEPARTMENT -> requireNoIds(r, false, false, false);
            case BATCH -> {
                requireNoIds(r, true, false, false);
                form.setTargetBatch(batches.findById(r.targetBatchId())
                        .orElseThrow(() -> new BadRequestException("Batch " + r.targetBatchId() + " does not exist")));
            }
            case SECTION -> {
                requireNoIds(r, false, true, false);
                form.setTargetSection(sections.findById(r.targetSectionId())
                        .orElseThrow(() -> new BadRequestException("Section " + r.targetSectionId() + " does not exist")));
            }
            case SUBJECT_OFFERING -> {
                requireNoIds(r, false, false, true);
                form.setTargetSubjectOffering(offerings.findWithDetailsById(r.targetSubjectOfferingId())
                        .orElseThrow(() -> new BadRequestException("Subject offering " + r.targetSubjectOfferingId() + " does not exist")));
            }
        }
    }

    /** Exactly the id that matches the target type must be given. */
    private static void requireNoIds(FeedbackFormRequest r, boolean batch, boolean section, boolean offering) {
        if ((r.targetBatchId() != null) != batch || (r.targetSectionId() != null) != section
                || (r.targetSubjectOfferingId() != null) != offering) {
            throw new BadRequestException("Target " + r.targetType() + " needs "
                    + (batch ? "targetBatchId" : section ? "targetSectionId" : offering ? "targetSubjectOfferingId" : "no target id")
                    + " and no other target ids");
        }
    }

    private static void addQuestion(FeedbackForm form, FeedbackFormRequest.Question q) {
        form.getQuestions().add(new FeedbackQuestion(form, form.getQuestions().size() + 1, q.questionText().trim(),
                q.questionType(), q.required() == null || q.required()));
    }

    private FeedbackForm require(Long id) {
        return forms.findWithDetailsById(id).orElseThrow(() -> new ResourceNotFoundException("FeedbackForm", "id", id));
    }

    private void log(String action, FeedbackForm form, FeedbackForm.Status oldStatus) {
        audit.record(action, MODULE, ENTITY_TYPE, form.getId(), summary(form, oldStatus));
    }

    private static Map<String, Object> summary(FeedbackForm form, FeedbackForm.Status oldStatus) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("formId", form.getId());
        m.put("targetType", form.getTargetType());
        m.put("anonymous", form.isAnonymous());
        m.put("questions", form.getQuestions().size());
        m.put("oldStatus", oldStatus);
        m.put("newStatus", form.getStatus());
        return m;
    }
}

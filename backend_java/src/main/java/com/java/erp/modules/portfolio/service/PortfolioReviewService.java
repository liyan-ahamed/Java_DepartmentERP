package com.java.erp.modules.portfolio.service;

import com.java.erp.common.response.PagedResponse;
import com.java.erp.common.util.SecurityUtils;
import com.java.erp.exception.BadRequestException;
import com.java.erp.exception.ConflictException;
import com.java.erp.exception.ResourceNotFoundException;
import com.java.erp.modules.auth.entity.User;
import com.java.erp.modules.auth.repository.UserRepository;
import com.java.erp.modules.notification.entity.Notification;
import com.java.erp.modules.notification.service.NotificationService;
import com.java.erp.modules.portfolio.dto.response.PortfolioEntryResponse;
import com.java.erp.modules.portfolio.dto.response.StudentPortfolioResponse;
import com.java.erp.modules.portfolio.entity.PortfolioEntry;
import com.java.erp.modules.portfolio.repository.PortfolioEntryRepository;
import com.java.erp.modules.system.entity.Student;
import com.java.erp.modules.system.repository.StudentRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Faculty side of the portfolio: review queue, verify/reject, and verified portfolios.
 * HOD reviews every student; STAFF only students of sections they currently teach
 * (see {@link PortfolioReviewScope}). Reviewers never edit the student's content.
 */
@Service
public class PortfolioReviewService {

    private static final Logger logger = LoggerFactory.getLogger(PortfolioReviewService.class);

    static final String NOTIFICATION_MODULE = "PORTFOLIO";
    static final String NOTIFICATION_REFERENCE_TYPE = "PORTFOLIO_ENTRY";
    static final int MIN_REJECTION_NOTE_LENGTH = 10;

    private final PortfolioEntryRepository entries;
    private final StudentRepository students;
    private final UserRepository users;
    private final PortfolioReviewScope reviewScope;
    private final NotificationService notifications;
    private final PortfolioAuditLogger audit;

    public PortfolioReviewService(PortfolioEntryRepository entries, StudentRepository students, UserRepository users,
                                  PortfolioReviewScope reviewScope, NotificationService notifications,
                                  PortfolioAuditLogger audit) {
        this.entries = entries;
        this.students = students;
        this.users = users;
        this.reviewScope = reviewScope;
        this.notifications = notifications;
        this.audit = audit;
    }

    /**
     * Entries of students in the reviewer's scope. status defaults to SUBMITTED (oldest submission
     * first); VERIFIED/REJECTED are listed by latest decision. Drafts are never listed.
     * search matches student name, register number or entry title.
     */
    @Transactional(readOnly = true)
    public PagedResponse<PortfolioEntryResponse> queue(PortfolioEntry.Status status, PortfolioEntry.ActivityType type,
                                                       PortfolioEntry.Level level, Long batchId, Long sectionId,
                                                       String search, int page, int size) {
        PortfolioEntry.Status wanted = status == null ? PortfolioEntry.Status.SUBMITTED : status;
        if (wanted == PortfolioEntry.Status.DRAFT) {
            throw new BadRequestException("Drafts are private to the student and cannot be reviewed");
        }
        PortfolioReviewScope.Scope scope = reviewScope.current();
        String term = search == null || search.isBlank() ? null : "%" + search.trim().toLowerCase(Locale.ROOT) + "%";

        Specification<PortfolioEntry> spec = (root, query, cb) -> {
            Join<PortfolioEntry, Student> s = root.join("student");
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("status"), wanted));
            if (!scope.allSections()) p.add(s.get("section").get("id").in(scope.queryIds()));
            if (type != null) p.add(cb.equal(root.get("activityType"), type));
            if (level != null) p.add(cb.equal(root.get("level"), level));
            if (batchId != null) p.add(cb.equal(s.get("batch").get("id"), batchId));
            if (sectionId != null) p.add(cb.equal(s.get("section").get("id"), sectionId));
            if (term != null) {
                p.add(cb.or(cb.like(cb.lower(s.get("name")), term),
                        cb.like(cb.lower(s.get("registerNumber")), term),
                        cb.like(cb.lower(root.get("title")), term)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        Sort sort = wanted == PortfolioEntry.Status.SUBMITTED
                ? Sort.by(Sort.Order.asc("submittedAt"), Sort.Order.asc("id"))
                : Sort.by(Sort.Order.desc("reviewedAt"), Sort.Order.desc("id"));
        Page<PortfolioEntry> result = entries.findAll(spec, PageRequest.of(page, size, sort));
        return PagedResponse.of(result.getContent().stream().map(PortfolioEntryResponse::from).toList(), result);
    }

    @Transactional
    public PortfolioEntryResponse verify(Long id, String note) {
        return decide(id, PortfolioEntry.Status.VERIFIED, trimToNull(note));
    }

    @Transactional
    public PortfolioEntryResponse reject(Long id, String note) {
        String reason = trimToNull(note);
        if (reason == null || reason.length() < MIN_REJECTION_NOTE_LENGTH) {
            throw new BadRequestException("A note of at least " + MIN_REJECTION_NOTE_LENGTH
                    + " characters explaining what to change is required to reject an entry");
        }
        return decide(id, PortfolioEntry.Status.REJECTED, reason);
    }

    /** Verified portfolio of one student (VERIFIED entries only). */
    @Transactional(readOnly = true)
    public StudentPortfolioResponse studentPortfolio(Long studentId) {
        Student student = students.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student", "id", studentId));
        reviewScope.require(reviewScope.current(), student);

        List<PortfolioEntryResponse> verified = entries
                .findByStudentIdAndStatusOrderByStartDateDescIdDesc(studentId, PortfolioEntry.Status.VERIFIED)
                .stream().map(PortfolioEntryResponse::from).toList();
        Map<PortfolioEntry.ActivityType, Long> byType = PortfolioSummaryService.zeroed(PortfolioEntry.ActivityType.class);
        verified.forEach(e -> byType.merge(e.activityType(), 1L, Long::sum));
        return new StudentPortfolioResponse(student.getId(), student.getName(), student.getRegisterNumber(),
                student.getBatch().getId(), student.getBatch().getBatchName(),
                student.getSection().getId(), student.getSection().getSectionName(),
                verified.size(), byType, verified);
    }

    /**
     * SUBMITTED -> VERIFIED/REJECTED. A repeated or concurrent decision gets 409 (state check
     * plus the entry's version), so the student is notified once per decision.
     */
    private PortfolioEntryResponse decide(Long id, PortfolioEntry.Status decision, String note) {
        PortfolioEntry entry = entries.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio entry", "id", id));
        if (entry.getStatus() == PortfolioEntry.Status.DRAFT) {
            // Drafts are private: same answer as for a missing entry
            throw new ResourceNotFoundException("Portfolio entry", "id", id);
        }
        reviewScope.require(reviewScope.current(), entry.getStudent());
        PortfolioEntry.Status oldStatus = entry.getStatus();
        if (oldStatus != PortfolioEntry.Status.SUBMITTED) {
            throw new ConflictException("Only SUBMITTED entries can be reviewed; this entry is " + oldStatus);
        }

        entry.decide(decision, users.getReferenceById(SecurityUtils.getCurrentUserId()), note);
        entry = entries.saveAndFlush(entry);
        notifyStudent(entry);
        audit.log(decision == PortfolioEntry.Status.VERIFIED ? "VERIFY" : "REJECT", entry, oldStatus, decision);
        return PortfolioEntryResponse.from(entry);
    }

    /**
     * In-app notification to the student's login: linked by students.user_id, else an active
     * STUDENT login with the student's email (same rule as StudentRepository.findLinkedStudent).
     */
    private void notifyStudent(PortfolioEntry entry) {
        Student student = entry.getStudent();
        Optional<Long> recipient;
        if (student.getUser() != null) {
            recipient = student.getUser().isActive() ? Optional.of(student.getUser().getId()) : Optional.empty();
        } else if (student.getEmail() != null && !student.getEmail().isBlank()) {
            recipient = users.findActiveStudentUsersByEmails(Set.of(student.getEmail().trim().toLowerCase(Locale.ROOT)))
                    .stream().map(User::getId).findFirst();
        } else {
            recipient = Optional.empty();
        }
        if (recipient.isEmpty()) {
            logger.info("Portfolio entry {} reviewed, but student {} has no active login to notify", entry.getId(), student.getId());
            return;
        }

        boolean verified = entry.getStatus() == PortfolioEntry.Status.VERIFIED;
        String label = entry.getActivityType().label();
        String title = verified ? "Portfolio entry verified" : "Portfolio entry needs changes";
        String message = "Your " + label + " entry \"" + entry.getTitle() + "\" "
                + (verified ? "has been verified." : "needs changes.");
        notifications.send(recipient.get(),
                verified ? Notification.NotificationType.INFO : Notification.NotificationType.ACTION_REQUIRED,
                title, message, NOTIFICATION_MODULE, entry.getId(), NOTIFICATION_REFERENCE_TYPE,
                Notification.NotificationPriority.NORMAL);
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

package com.java.erp.modules.portfolio.service;

import com.java.erp.common.response.PagedResponse;
import com.java.erp.common.util.SecurityUtils;
import com.java.erp.exception.BadRequestException;
import com.java.erp.exception.ConflictException;
import com.java.erp.exception.ForbiddenException;
import com.java.erp.exception.ResourceNotFoundException;
import com.java.erp.modules.gradebook.service.CurrentStudentResolver;
import com.java.erp.modules.portfolio.dto.request.PortfolioEntryRequest;
import com.java.erp.modules.portfolio.dto.response.MyPortfolioSummaryResponse;
import com.java.erp.modules.portfolio.dto.response.PortfolioEntryResponse;
import com.java.erp.modules.portfolio.entity.PortfolioEntry;
import com.java.erp.modules.portfolio.repository.PortfolioEntryRepository;
import com.java.erp.modules.system.entity.Student;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.util.*;

/**
 * The student side of the portfolio: create, edit, delete and submit own entries.
 * The student is always the one linked to the login; a request can never name another student.
 * Students may change only DRAFT and REJECTED entries; SUBMITTED and VERIFIED entries are locked.
 */
@Service
public class PortfolioService {

    static final int MIN_SUBMITTED_DESCRIPTION_LENGTH = 20;

    private final PortfolioEntryRepository entries;
    private final CurrentStudentResolver currentStudent;
    private final PortfolioReviewScope reviewScope;
    private final PortfolioAuditLogger audit;

    public PortfolioService(PortfolioEntryRepository entries, CurrentStudentResolver currentStudent,
                            PortfolioReviewScope reviewScope, PortfolioAuditLogger audit) {
        this.entries = entries;
        this.currentStudent = currentStudent;
        this.reviewScope = reviewScope;
        this.audit = audit;
    }

    @Transactional
    public PortfolioEntryResponse create(PortfolioEntryRequest request) {
        Student student = currentStudent.require();
        validateDetails(request);
        PortfolioEntry entry = new PortfolioEntry();
        entry.setStudent(student);
        apply(entry, request);
        entry = entries.saveAndFlush(entry);
        audit.log("CREATE", entry, null, entry.getStatus());
        return PortfolioEntryResponse.from(requireEntry(entry.getId()));
    }

    @Transactional
    public PortfolioEntryResponse update(Long id, PortfolioEntryRequest request) {
        PortfolioEntry entry = requireOwnEntry(id);
        requireEditable(entry, "edited");
        validateDetails(request);
        apply(entry, request);
        entry = entries.saveAndFlush(entry);
        audit.log("UPDATE", entry, entry.getStatus(), entry.getStatus());
        return PortfolioEntryResponse.from(entry);
    }

    @Transactional
    public void delete(Long id) {
        PortfolioEntry entry = requireOwnEntry(id);
        requireEditable(entry, "deleted");
        PortfolioEntry.Status oldStatus = entry.getStatus();
        entries.delete(entry);
        entries.flush();
        audit.log("DELETE", entry, oldStatus, null);
    }

    /** DRAFT/REJECTED -> SUBMITTED, once every field a reviewer needs is filled in. */
    @Transactional
    public PortfolioEntryResponse submit(Long id) {
        PortfolioEntry entry = requireOwnEntry(id);
        PortfolioEntry.Status oldStatus = entry.getStatus();
        if (!entry.isEditableByStudent()) {
            throw new ConflictException("This entry is already " + oldStatus + " and cannot be submitted again");
        }
        validateForSubmission(entry);
        entry.submit();
        entry = entries.saveAndFlush(entry);
        audit.log("SUBMIT", entry, oldStatus, entry.getStatus());
        return PortfolioEntryResponse.from(entry);
    }

    /**
     * STUDENT: own entries only (403 otherwise). STAFF/HOD: entries of students in their review
     * scope; drafts are private to the student and are reported as not found.
     */
    @Transactional(readOnly = true)
    public PortfolioEntryResponse get(Long id) {
        if (isStudent()) {
            return PortfolioEntryResponse.from(requireOwnEntry(id));
        }
        PortfolioEntry entry = requireEntry(id);
        if (entry.getStatus() == PortfolioEntry.Status.DRAFT) {
            throw new ResourceNotFoundException("Portfolio entry", "id", id);
        }
        reviewScope.require(reviewScope.current(), entry.getStudent());
        return PortfolioEntryResponse.from(entry);
    }

    /** Own entries in every status. year = calendar year of startDate. */
    @Transactional(readOnly = true)
    public PagedResponse<PortfolioEntryResponse> mine(PortfolioEntry.Status status, PortfolioEntry.ActivityType type,
                                                      Integer year, int page, int size) {
        Long studentId = currentStudent.require().getId();
        Specification<PortfolioEntry> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("student").get("id"), studentId));
            if (status != null) p.add(cb.equal(root.get("status"), status));
            if (type != null) p.add(cb.equal(root.get("activityType"), type));
            if (year != null) {
                p.add(cb.between(root.get("startDate"), LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        Page<PortfolioEntry> result = entries.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"))));
        return PagedResponse.of(result.getContent().stream().map(PortfolioEntryResponse::from).toList(), result);
    }

    @Transactional(readOnly = true)
    public MyPortfolioSummaryResponse mySummary() {
        Long studentId = currentStudent.require().getId();
        Map<PortfolioEntry.Status, Long> byStatus = new EnumMap<>(PortfolioEntry.Status.class);
        entries.countByStatusForStudent(studentId).forEach(c -> byStatus.put(c.getStatus(), c.getTotal()));
        Map<PortfolioEntry.ActivityType, Long> byType = PortfolioSummaryService.zeroed(PortfolioEntry.ActivityType.class);
        entries.countByActivityTypeForStudent(studentId).forEach(c -> byType.put(c.getActivityType(), c.getTotal()));
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        return new MyPortfolioSummaryResponse(total,
                byStatus.getOrDefault(PortfolioEntry.Status.DRAFT, 0L),
                byStatus.getOrDefault(PortfolioEntry.Status.SUBMITTED, 0L),
                byStatus.getOrDefault(PortfolioEntry.Status.VERIFIED, 0L),
                byStatus.getOrDefault(PortfolioEntry.Status.REJECTED, 0L),
                byType);
    }

    private PortfolioEntry requireEntry(Long id) {
        return entries.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio entry", "id", id));
    }

    private PortfolioEntry requireOwnEntry(Long id) {
        PortfolioEntry entry = requireEntry(id);
        if (!entry.getStudent().getId().equals(currentStudent.require().getId())) {
            throw new ForbiddenException("You can only access your own portfolio entries");
        }
        return entry;
    }

    private static void requireEditable(PortfolioEntry entry, String action) {
        if (!entry.isEditableByStudent()) {
            throw new ConflictException(entry.getStatus() + " entries cannot be " + action
                    + "; only DRAFT and REJECTED entries can");
        }
    }

    private static boolean isStudent() {
        return SecurityUtils.hasAuthority("ROLE_STUDENT")
                && !SecurityUtils.hasAuthority("ROLE_HOD") && !SecurityUtils.hasAuthority("ROLE_STAFF");
    }

    private static void apply(PortfolioEntry entry, PortfolioEntryRequest r) {
        entry.setActivityType(r.activityType());
        entry.setTitle(r.title().trim());
        entry.setOrganization(trimToNull(r.organization()));
        entry.setDescription(trimToNull(r.description()));
        entry.setLevel(r.level());
        entry.setRoleOrPosition(trimToNull(r.roleOrPosition()));
        entry.setAchievementResult(trimToNull(r.achievementResult()));
        entry.setStartDate(r.startDate());
        entry.setEndDate(r.endDate());
        entry.setEvidenceUrl(trimToNull(r.evidenceUrl()));
    }

    /** Checks that apply to drafts too: date order and evidence link format. */
    private static void validateDetails(PortfolioEntryRequest r) {
        List<String> errors = new ArrayList<>();
        if (r.startDate() != null && r.endDate() != null && r.endDate().isBefore(r.startDate())) {
            errors.add("endDate: must be on or after startDate");
        }
        if (r.endDate() != null && r.startDate() == null) {
            errors.add("startDate: is required when endDate is given");
        }
        String url = trimToNull(r.evidenceUrl());
        if (url != null && !isWebUrl(url)) {
            errors.add("evidenceUrl: must be a valid http:// or https:// link");
        }
        if (!errors.isEmpty()) {
            throw new BadRequestException("Validation failed", errors);
        }
    }

    /** Everything a reviewer needs to judge the entry. */
    private static void validateForSubmission(PortfolioEntry e) {
        List<String> errors = new ArrayList<>();
        if (e.getOrganization() == null) {
            errors.add("organization: is required to submit (organizer, issuer, company or venue)");
        }
        if (e.getDescription() == null || e.getDescription().length() < MIN_SUBMITTED_DESCRIPTION_LENGTH) {
            errors.add("description: at least " + MIN_SUBMITTED_DESCRIPTION_LENGTH + " characters are required to submit");
        }
        if (e.getLevel() == null) {
            errors.add("level: is required to submit");
        }
        if (e.getStartDate() == null) {
            errors.add("startDate: is required to submit");
        } else if (e.getStartDate().isAfter(LocalDate.now())) {
            errors.add("startDate: cannot be in the future when submitting");
        }
        if (!errors.isEmpty()) {
            throw new BadRequestException("This entry is incomplete and cannot be submitted yet", errors);
        }
    }

    static boolean isWebUrl(String value) {
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            return scheme != null
                    && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    && uri.getHost() != null && !uri.getHost().isBlank();
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

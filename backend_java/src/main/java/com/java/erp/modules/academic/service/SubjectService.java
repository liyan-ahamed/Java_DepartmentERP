package com.java.erp.modules.academic.service;

import com.java.erp.common.audit.Auditable;
import com.java.erp.common.response.PagedResponse;
import com.java.erp.exception.ConflictException;
import com.java.erp.exception.ResourceNotFoundException;
import com.java.erp.modules.academic.dto.request.SubjectRequest;
import com.java.erp.modules.academic.dto.response.SubjectResponse;
import com.java.erp.modules.academic.entity.Subject;
import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.academic.repository.SubjectOfferingRepository;
import com.java.erp.modules.academic.repository.SubjectRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;

@Service
public class SubjectService {

    private final SubjectRepository subjects;
    private final SubjectOfferingRepository offerings;

    public SubjectService(SubjectRepository subjects, SubjectOfferingRepository offerings) {
        this.subjects = subjects;
        this.offerings = offerings;
    }

    @Transactional
    @Auditable(action = "CREATE", module = "ACADEMIC", entityType = "Subject")
    public SubjectResponse create(SubjectRequest request) {
        String code = normalizeCode(request.code());
        if (subjects.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("Subject", "code", code);
        }
        Subject subject = new Subject(code, request.name().trim(), request.subjectType());
        subject.setCredits(request.credits());
        subject.setActive(request.active() == null || request.active());
        return response(subjects.saveAndFlush(subject));
    }

    @Transactional
    @Auditable(action = "UPDATE", module = "ACADEMIC", entityType = "Subject")
    public SubjectResponse update(Long id, SubjectRequest request) {
        Subject subject = subjects.findById(id).orElseThrow(() -> new ResourceNotFoundException("Subject", "id", id));
        String code = normalizeCode(request.code());
        if (subjects.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new ConflictException("Subject", "code", code);
        }
        // The subject type decides the internal-marks structure of every offering, so it is fixed once offered.
        // An unclassified subject may still receive its first type.
        if (subject.getSubjectType() != null && subject.getSubjectType() != request.subjectType()
                && offerings.existsBySubjectId(id)) {
            throw new ConflictException("The subject type cannot change once the subject has offerings");
        }
        subject.setCode(code);
        subject.setName(request.name().trim());
        subject.setSubjectType(request.subjectType());
        subject.setCredits(request.credits());
        if (request.active() != null) {
            subject.setActive(request.active());
        }
        return response(subjects.saveAndFlush(subject));
    }

    @Transactional(readOnly = true)
    public SubjectResponse get(Long id) {
        return response(subjects.findById(id).orElseThrow(() -> new ResourceNotFoundException("Subject", "id", id)));
    }

    @Transactional(readOnly = true)
    /**
     * semester / batchId (optional, combinable): only subjects with an ACTIVE offering in that semester / for that cohort.
     * An EXISTS subquery, so a subject offered several times is still one row and paging totals stay correct.
     */
    public PagedResponse<SubjectResponse> list(Subject.SubjectType type, Boolean active, String search, Integer semester,
                                               Long batchId, int page, int size) {
        Specification<Subject> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (type != null) predicates.add(cb.equal(root.get("subjectType"), type));
            if (active != null) predicates.add(cb.equal(root.get("active"), active));
            if (StringUtils.hasText(search)) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("code")), pattern), cb.like(cb.lower(root.get("name")), pattern)));
            }
            if (semester != null || batchId != null) {
                Subquery<Long> offered = query.subquery(Long.class);
                Root<SubjectOffering> o = offered.from(SubjectOffering.class);
                List<Predicate> match = new ArrayList<>();
                match.add(cb.equal(o.get("subject"), root));
                match.add(cb.isTrue(o.get("active")));
                if (semester != null) match.add(cb.equal(o.get("semester"), semester));
                if (batchId != null) match.add(cb.equal(o.get("batch").get("id"), batchId));
                offered.select(o.get("id")).where(match.toArray(Predicate[]::new));
                predicates.add(cb.exists(offered));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<Subject> result = subjects.findAll(spec, PageRequest.of(page, size, Sort.by("code")));
        return PagedResponse.of(responses(result.getContent()), result);
    }

    private SubjectResponse response(Subject subject) {
        return responses(List.of(subject)).get(0);
    }

    /** Responses with offeredIn, loaded for all given subjects in one query. */
    private List<SubjectResponse> responses(List<Subject> page) {
        if (page.isEmpty()) return List.of();
        Map<Long, List<SubjectResponse.OfferedIn>> terms = new HashMap<>();
        offerings.findActiveTermsBySubjectIds(page.stream().map(Subject::getId).toList()).forEach(t ->
                terms.computeIfAbsent(t.getSubjectId(), k -> new ArrayList<>())
                        .add(new SubjectResponse.OfferedIn(t.getBatchId(), t.getBatchName(), t.getSemester())));
        Comparator<SubjectResponse.OfferedIn> order = Comparator.comparing(SubjectResponse.OfferedIn::semester)
                .thenComparing(SubjectResponse.OfferedIn::batchName);
        return page.stream().map(s -> SubjectResponse.from(s,
                terms.getOrDefault(s.getId(), List.of()).stream().sorted(order).toList())).toList();
    }

    private static String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }
}

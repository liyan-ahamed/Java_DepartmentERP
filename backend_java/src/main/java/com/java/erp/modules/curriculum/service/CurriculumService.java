package com.java.erp.modules.curriculum.service;

import com.java.erp.common.util.SecurityUtils;
import com.java.erp.exception.BadRequestException;
import com.java.erp.exception.ConflictException;
import com.java.erp.exception.ResourceNotFoundException;
import com.java.erp.modules.academic.entity.Subject;
import com.java.erp.modules.academic.repository.SubjectOfferingRepository;
import com.java.erp.modules.academic.repository.SubjectRepository;
import com.java.erp.modules.audit.service.AuditRecorder;
import com.java.erp.modules.auth.repository.UserRepository;
import com.java.erp.modules.curriculum.dto.request.CurriculumRequest;
import com.java.erp.modules.curriculum.dto.request.CurriculumRequest.ElectiveGroupRequest;
import com.java.erp.modules.curriculum.dto.response.CurriculumResponse;
import com.java.erp.modules.curriculum.dto.response.CurriculumResponse.ElectiveGroupItem;
import com.java.erp.modules.curriculum.dto.response.CurriculumResponse.SubjectItem;
import com.java.erp.modules.curriculum.entity.CurriculumElectiveGroup;
import com.java.erp.modules.curriculum.entity.CurriculumRequirement;
import com.java.erp.modules.curriculum.entity.CurriculumSubject;
import com.java.erp.modules.curriculum.repository.CurriculumElectiveGroupRepository;
import com.java.erp.modules.curriculum.repository.CurriculumRequirementRepository;
import com.java.erp.modules.curriculum.repository.CurriculumSubjectRepository;
import com.java.erp.modules.system.entity.Batch;
import com.java.erp.modules.system.repository.BatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Curriculum requirements of a cohort (batch) + semester, saved by the HOD as one document.
 * A save is all-or-nothing: every rule is checked first and all problems are returned together (400); a stale
 * version is a conflict (409). Nothing is seeded: an unsaved cohort + semester has requirements unknown.
 */
@Service
public class CurriculumService {

    public static final int MAX_COMPULSORY = 30;
    public static final int MAX_GROUPS = 10;
    public static final int MAX_OPTIONS = 30;
    public static final int MAX_GROUP_NAME = 100;

    private final CurriculumRequirementRepository curricula;
    private final CurriculumElectiveGroupRepository groups;
    private final CurriculumSubjectRepository curriculumSubjects;
    private final BatchRepository batches;
    private final SubjectRepository subjects;
    private final SubjectOfferingRepository offerings;
    private final UserRepository users;
    private final AuditRecorder audit;

    public CurriculumService(CurriculumRequirementRepository curricula, CurriculumElectiveGroupRepository groups,
                             CurriculumSubjectRepository curriculumSubjects, BatchRepository batches,
                             SubjectRepository subjects, SubjectOfferingRepository offerings, UserRepository users,
                             AuditRecorder audit) {
        this.curricula = curricula;
        this.groups = groups;
        this.curriculumSubjects = curriculumSubjects;
        this.batches = batches;
        this.subjects = subjects;
        this.offerings = offerings;
        this.users = users;
        this.audit = audit;
    }

    /** A loaded curriculum: the rule input plus the stored rows (for responses and the registration detail). */
    public record Loaded(CurriculumRequirement curriculum, List<CurriculumElectiveGroup> groups,
                         List<CurriculumSubject> subjects, RegistrationEvaluator.Requirements requirements) {
    }

    @Transactional(readOnly = true)
    public CurriculumResponse get(Long batchId, Integer semester) {
        Batch batch = batch(batchId);
        requireSemester(semester);
        return load(batchId, semester).map(this::toResponse).orElseGet(() -> unconfigured(batch, semester));
    }

    /** The configured requirements of a cohort + semester, if any. */
    @Transactional(readOnly = true)
    public Optional<Loaded> load(Long batchId, Integer semester) {
        return curricula.findByBatchIdAndSemester(batchId, semester).map(c -> {
            List<CurriculumElectiveGroup> storedGroups = groups.findByCurriculumIdOrderByDisplayOrderAscIdAsc(c.getId());
            List<CurriculumSubject> rows = curriculumSubjects.findByCurriculumIdOrderByDisplayOrderAscIdAsc(c.getId());
            List<Long> compulsory = rows.stream().filter(r -> r.getElectiveGroup() == null)
                    .map(r -> r.getSubject().getId()).toList();
            List<RegistrationEvaluator.Group> ruleGroups = storedGroups.stream()
                    .map(g -> new RegistrationEvaluator.Group(g.getId(), g.getName(), g.getRequiredSelections(),
                            rows.stream().filter(r -> r.getElectiveGroup() != null && r.getElectiveGroup().getId().equals(g.getId()))
                                    .map(r -> r.getSubject().getId()).toList()))
                    .toList();
            return new Loaded(c, storedGroups, rows, new RegistrationEvaluator.Requirements(compulsory, ruleGroups));
        });
    }

    @Transactional
    public CurriculumResponse replace(Long batchId, Integer semester, CurriculumRequest request) {
        Batch batch = batch(batchId);
        requireSemester(semester);
        CurriculumRequirement existing = curricula.findByBatchIdAndSemester(batchId, semester).orElse(null);
        if (existing == null && request.version() != null) {
            throw new ConflictException("This curriculum no longer exists; reload it and try again");
        }
        if (existing != null && !existing.getVersion().equals(request.version())) {
            throw new ConflictException("This curriculum was changed by someone else (now version " + existing.getVersion()
                    + "); reload it and try again");
        }
        Map<Long, CurriculumElectiveGroup> storedGroups = existing == null ? Map.of()
                : groups.findByCurriculumIdOrderByDisplayOrderAscIdAsc(existing.getId()).stream()
                        .collect(Collectors.toMap(CurriculumElectiveGroup::getId, g -> g));
        List<Long> compulsory = request.compulsorySubjectIds() == null ? List.of() : request.compulsorySubjectIds();
        List<ElectiveGroupRequest> groupRequests = request.electiveGroups() == null ? List.of() : request.electiveGroups();
        Map<Long, Subject> subjectsById = validate(batch, semester, compulsory, groupRequests, storedGroups.keySet());

        CurriculumRequirement curriculum = existing != null ? existing
                : curricula.saveAndFlush(new CurriculumRequirement(batch, semester));
        // Old rows go first (flushed), so re-adding or moving a subject never meets its old row.
        curriculumSubjects.deleteAll(curriculumSubjects.findByCurriculumIdOrderByDisplayOrderAscIdAsc(curriculum.getId()));
        curriculumSubjects.flush();
        Set<Long> keptIds = groupRequests.stream().map(ElectiveGroupRequest::id).filter(Objects::nonNull).collect(Collectors.toSet());
        groups.deleteAll(storedGroups.values().stream().filter(g -> !keptIds.contains(g.getId())).toList());
        groups.flush();

        List<CurriculumSubject> rows = new ArrayList<>();
        for (int i = 0; i < compulsory.size(); i++) {
            rows.add(new CurriculumSubject(curriculum, subjectsById.get(compulsory.get(i)), null, i + 1));
        }
        for (int i = 0; i < groupRequests.size(); i++) {
            ElectiveGroupRequest g = groupRequests.get(i);
            CurriculumElectiveGroup group = g.id() != null ? storedGroups.get(g.id()) : new CurriculumElectiveGroup(curriculum);
            group.setName(g.name().trim());
            group.setRequiredSelections(g.requiredSelections());
            group.setDisplayOrder(i + 1);
            groups.save(group);
            for (int j = 0; j < g.subjectIds().size(); j++) {
                rows.add(new CurriculumSubject(curriculum, subjectsById.get(g.subjectIds().get(j)), group, j + 1));
            }
        }
        groups.flush();
        curriculumSubjects.saveAll(rows);
        curriculum.touch(users.getReferenceById(SecurityUtils.getCurrentUserId()));
        curricula.saveAndFlush(curriculum);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("batchId", batchId);
        summary.put("semester", semester);
        summary.put("version", curriculum.getVersion());
        summary.put("compulsorySubjectIds", compulsory);
        summary.put("electiveGroups", groupRequests.stream().map(g -> Map.of("name", g.name().trim(),
                "requiredSelections", g.requiredSelections(), "subjectIds", g.subjectIds())).toList());
        audit.record(existing == null ? "CURRICULUM_CREATED" : "CURRICULUM_UPDATED", "CURRICULUM", "CurriculumRequirement",
                curriculum.getId(), summary);
        return toResponse(load(batchId, semester).orElseThrow());
    }

    @Transactional
    public CurriculumResponse delete(Long batchId, Integer semester) {
        Batch batch = batch(batchId);
        requireSemester(semester);
        CurriculumRequirement existing = curricula.findByBatchIdAndSemester(batchId, semester)
                .orElseThrow(() -> new ResourceNotFoundException("Curriculum", "cohort and semester",
                        batch.getBatchName() + " semester " + semester));
        Long id = existing.getId();
        // Groups and subject rows go with it (ON DELETE CASCADE).
        curricula.delete(existing);
        curricula.flush();
        audit.record("CURRICULUM_DELETED", "CURRICULUM", "CurriculumRequirement", id,
                Map.of("batchId", batchId, "semester", semester));
        return unconfigured(batch, semester);
    }

    /** Checks the whole request; returns the subjects by id when everything is valid. */
    private Map<Long, Subject> validate(Batch batch, Integer semester, List<Long> compulsory,
                                        List<ElectiveGroupRequest> groupRequests, Set<Long> storedGroupIds) {
        List<String> errors = new ArrayList<>();
        if (compulsory.isEmpty() && groupRequests.isEmpty()) {
            errors.add("A curriculum needs at least one compulsory subject or one elective group (use DELETE to remove it)");
        }
        if (compulsory.size() > MAX_COMPULSORY) {
            errors.add("compulsorySubjectIds: at most " + MAX_COMPULSORY + " subjects");
        }
        if (groupRequests.size() > MAX_GROUPS) {
            errors.add("electiveGroups: at most " + MAX_GROUPS + " groups");
        }
        // Where each subject is placed, so a second placement can name the first.
        Map<Long, String> placedIn = new HashMap<>();
        Set<Long> allIds = new LinkedHashSet<>();
        for (Long id : compulsory) {
            if (id == null) {
                errors.add("compulsorySubjectIds: must not contain null");
            } else if (placedIn.putIfAbsent(id, "compulsorySubjectIds") != null) {
                errors.add("compulsorySubjectIds: subject " + id + " is listed more than once");
            } else {
                allIds.add(id);
            }
        }
        Set<String> names = new HashSet<>();
        Set<Long> usedGroupIds = new HashSet<>();
        for (int i = 0; i < groupRequests.size(); i++) {
            ElectiveGroupRequest g = groupRequests.get(i);
            String at = "electiveGroups[" + i + "]";
            if (g == null) {
                errors.add(at + ": must not be null");
                continue;
            }
            if (g.id() != null && !storedGroupIds.contains(g.id())) {
                errors.add(at + ".id: group " + g.id() + " does not belong to this curriculum (send null for a new group)");
            } else if (g.id() != null && !usedGroupIds.add(g.id())) {
                errors.add(at + ".id: group " + g.id() + " is listed more than once");
            }
            if (g.name() == null || g.name().isBlank()) {
                errors.add(at + ".name: is required");
            } else if (g.name().trim().length() > MAX_GROUP_NAME) {
                errors.add(at + ".name: must be at most " + MAX_GROUP_NAME + " characters");
            } else if (!names.add(g.name().trim().toLowerCase(Locale.ROOT))) {
                errors.add(at + ".name: \"" + g.name().trim() + "\" is used by another group");
            }
            List<Long> options = g.subjectIds() == null ? List.of() : g.subjectIds();
            if (options.size() < 2) {
                errors.add(at + ".subjectIds: an elective group needs at least 2 options (a single subject is compulsory)");
            } else if (options.size() > MAX_OPTIONS) {
                errors.add(at + ".subjectIds: at most " + MAX_OPTIONS + " options");
            }
            if (g.requiredSelections() == null || g.requiredSelections() < 1) {
                errors.add(at + ".requiredSelections: must be at least 1");
            } else if (g.requiredSelections() > options.size()) {
                errors.add(at + ".requiredSelections: cannot exceed the number of options (" + options.size() + ")");
            }
            for (Long id : options) {
                if (id == null) {
                    errors.add(at + ".subjectIds: must not contain null");
                    continue;
                }
                String first = placedIn.putIfAbsent(id, at);
                if (first == null) {
                    allIds.add(id);
                } else if (first.equals(at)) {
                    errors.add(at + ".subjectIds: subject " + id + " is listed more than once");
                } else {
                    errors.add(at + ".subjectIds: subject " + id + " is already in " + first
                            + " (a subject may appear in only one place)");
                }
            }
        }

        Map<Long, Subject> found = new HashMap<>();
        subjects.findAllById(allIds).forEach(s -> found.put(s.getId(), s));
        Set<Long> offered = allIds.isEmpty() ? Set.of()
                : new HashSet<>(offerings.findOfferedSubjectIds(batch.getId(), semester, allIds));
        for (Long id : allIds) {
            Subject s = found.get(id);
            String at = placedIn.get(id);
            if (s == null) {
                errors.add(at + ": subject " + id + " does not exist");
            } else if (!s.isActive()) {
                errors.add(at + ": subject " + s.getCode() + " is inactive");
            } else if (!offered.contains(id)) {
                errors.add(at + ": subject " + s.getCode() + " has no active offering for " + batch.getBatchName()
                        + " semester " + semester);
            }
        }
        if (!errors.isEmpty()) {
            throw new BadRequestException("Invalid curriculum", errors);
        }
        return found;
    }

    private CurriculumResponse toResponse(Loaded loaded) {
        CurriculumRequirement c = loaded.curriculum();
        List<SubjectItem> compulsory = loaded.subjects().stream().filter(r -> r.getElectiveGroup() == null)
                .map(r -> SubjectItem.from(r.getSubject())).toList();
        List<ElectiveGroupItem> groupItems = loaded.groups().stream().map(g -> new ElectiveGroupItem(g.getId(), g.getName(),
                g.getRequiredSelections(), g.getDisplayOrder(), loaded.subjects().stream()
                        .filter(r -> r.getElectiveGroup() != null && r.getElectiveGroup().getId().equals(g.getId()))
                        .map(r -> SubjectItem.from(r.getSubject())).toList())).toList();
        return new CurriculumResponse(c.getBatch().getId(), c.getBatch().getBatchName(), c.getSemester(), true, c.getVersion(),
                c.getUpdatedAt(), c.getUpdatedBy() == null ? null : c.getUpdatedBy().getName(), compulsory, groupItems);
    }

    private static CurriculumResponse unconfigured(Batch batch, Integer semester) {
        return new CurriculumResponse(batch.getId(), batch.getBatchName(), semester, false, null, null, null, List.of(), List.of());
    }

    private Batch batch(Long batchId) {
        return batches.findById(batchId).orElseThrow(() -> new ResourceNotFoundException("Batch", "id", batchId));
    }

    static void requireSemester(Integer semester) {
        if (semester == null || semester < 1 || semester > 8) {
            throw new BadRequestException("semester must be between 1 and 8");
        }
    }
}

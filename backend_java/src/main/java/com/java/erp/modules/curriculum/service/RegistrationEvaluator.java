package com.java.erp.modules.curriculum.service;

import com.java.erp.modules.enrollment.entity.CourseEnrollment;

import java.util.*;

/**
 * The one registration-completeness rule (pure, no database).
 *
 * A registration counts when the enrollment is ENROLLED or COMPLETED on an active offering of the cohort + semester
 * (callers pass the subjects of counted registrations only). Then:
 *   NOT_REGISTERED        no counted registration (whether or not requirements are known)
 *   REQUIREMENTS_UNKNOWN  registrations exist but no curriculum is configured; never reported as complete
 *   ALL_REGISTERED        every compulsory subject registered and every elective group has at least
 *                         requiredSelections registered options (each group judged on its own)
 *   INCOMPLETE            otherwise
 * Subjects outside the curriculum are optional extras: they count as registrations but never block completion.
 * The number of registrations alone never makes a student complete.
 */
public final class RegistrationEvaluator {

    public enum Status { ALL_REGISTERED, INCOMPLETE, NOT_REGISTERED, REQUIREMENTS_UNKNOWN }

    /** Enrollment statuses that count as a registration (on an active offering). */
    public static final Set<CourseEnrollment.Status> COUNTED = EnumSet.of(CourseEnrollment.Status.ENROLLED,
            CourseEnrollment.Status.COMPLETED);

    /** One elective group: choose at least requiredSelections of subjectIds. */
    public record Group(Long id, String name, int requiredSelections, List<Long> subjectIds) {
    }

    /** A configured curriculum; callers pass null when none is configured. */
    public record Requirements(List<Long> compulsorySubjectIds, List<Group> groups) {
    }

    public record GroupResult(Group group, List<Long> selectedSubjectIds, boolean satisfied) {
    }

    /** Compulsory / elective figures are null when requirements are unknown. */
    public record Evaluation(Status status, int registeredCount, Integer compulsoryRegistered, Integer compulsoryRequired,
                             Integer electiveGroupsSatisfied, Integer electiveGroupsRequired, List<GroupResult> groups) {
    }

    private RegistrationEvaluator() {
    }

    public static Evaluation evaluate(Set<Long> registeredSubjectIds, Requirements requirements) {
        int registered = registeredSubjectIds.size();
        if (requirements == null) {
            return new Evaluation(registered == 0 ? Status.NOT_REGISTERED : Status.REQUIREMENTS_UNKNOWN, registered,
                    null, null, null, null, List.of());
        }
        List<Long> compulsory = requirements.compulsorySubjectIds().stream().distinct().toList();
        int compulsoryRegistered = (int) compulsory.stream().filter(registeredSubjectIds::contains).count();
        List<GroupResult> groups = new ArrayList<>();
        for (Group g : requirements.groups()) {
            List<Long> selected = g.subjectIds().stream().distinct().filter(registeredSubjectIds::contains).toList();
            groups.add(new GroupResult(g, selected, selected.size() >= g.requiredSelections()));
        }
        int satisfied = (int) groups.stream().filter(GroupResult::satisfied).count();
        boolean complete = compulsoryRegistered == compulsory.size() && satisfied == groups.size();
        Status status = registered == 0 ? Status.NOT_REGISTERED : complete ? Status.ALL_REGISTERED : Status.INCOMPLETE;
        return new Evaluation(status, registered, compulsoryRegistered, compulsory.size(), satisfied, groups.size(), groups);
    }
}

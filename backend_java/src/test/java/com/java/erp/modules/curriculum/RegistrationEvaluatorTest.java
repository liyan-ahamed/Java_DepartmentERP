package com.java.erp.modules.curriculum;

import com.java.erp.modules.curriculum.service.RegistrationEvaluator;
import com.java.erp.modules.curriculum.service.RegistrationEvaluator.Evaluation;
import com.java.erp.modules.curriculum.service.RegistrationEvaluator.Group;
import com.java.erp.modules.curriculum.service.RegistrationEvaluator.Requirements;
import com.java.erp.modules.curriculum.service.RegistrationEvaluator.Status;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** The registration-completeness rule (pure, no database). */
class RegistrationEvaluatorTest {

    // Compulsory 1, 2, 3; Elective Group I: choose 1 of 11, 12, 13; Elective Group II: choose 2 of 21, 22, 23, 24.
    private static final Requirements SEM3 = new Requirements(List.of(1L, 2L, 3L), List.of(
            new Group(7L, "Elective Group I", 1, List.of(11L, 12L, 13L)),
            new Group(8L, "Elective Group II", 2, List.of(21L, 22L, 23L, 24L))));

    @Test
    void unknownRequirementsNeverCompleteAStudent() {
        Evaluation none = RegistrationEvaluator.evaluate(Set.of(), null);
        assertThat(none.status()).isEqualTo(Status.NOT_REGISTERED);

        Evaluation some = RegistrationEvaluator.evaluate(Set.of(1L, 2L, 3L, 11L, 21L, 22L), null);
        assertThat(some.status()).isEqualTo(Status.REQUIREMENTS_UNKNOWN);
        assertThat(some.registeredCount()).isEqualTo(6);
        assertThat(some.compulsoryRequired()).isNull();
        assertThat(some.electiveGroupsRequired()).isNull();
    }

    @Test
    void everyCompulsorySubjectAndEveryGroupMakesAllRegistered() {
        Evaluation e = RegistrationEvaluator.evaluate(Set.of(1L, 2L, 3L, 12L, 21L, 24L), SEM3);
        assertThat(e.status()).isEqualTo(Status.ALL_REGISTERED);
        assertThat(e.compulsoryRegistered()).isEqualTo(3);
        assertThat(e.compulsoryRequired()).isEqualTo(3);
        assertThat(e.electiveGroupsSatisfied()).isEqualTo(2);
        assertThat(e.electiveGroupsRequired()).isEqualTo(2);
    }

    @Test
    void extraOptionalSubjectsAndExtraElectivesNeverReduceCompleteness() {
        Evaluation e = RegistrationEvaluator.evaluate(Set.of(1L, 2L, 3L, 11L, 12L, 21L, 22L, 23L, 99L, 98L), SEM3);
        assertThat(e.status()).isEqualTo(Status.ALL_REGISTERED);
        assertThat(e.registeredCount()).isEqualTo(10);
    }

    @Test
    void aMissingCompulsorySubjectIsIncomplete() {
        Evaluation e = RegistrationEvaluator.evaluate(Set.of(1L, 3L, 11L, 21L, 22L), SEM3);
        assertThat(e.status()).isEqualTo(Status.INCOMPLETE);
        assertThat(e.compulsoryRegistered()).isEqualTo(2);
        assertThat(e.electiveGroupsSatisfied()).isEqualTo(2);
    }

    @Test
    void eachElectiveGroupIsJudgedOnItsOwn() {
        // Group I satisfied, Group II has only 1 of the 2 required.
        Evaluation e = RegistrationEvaluator.evaluate(Set.of(1L, 2L, 3L, 11L, 12L, 13L, 21L), SEM3);
        assertThat(e.status()).isEqualTo(Status.INCOMPLETE);
        assertThat(e.electiveGroupsSatisfied()).isEqualTo(1);
        assertThat(e.groups()).extracting(RegistrationEvaluator.GroupResult::satisfied).containsExactly(true, false);
        assertThat(e.groups().get(1).selectedSubjectIds()).containsExactly(21L);
    }

    @Test
    void theNumberOfRegistrationsAloneNeverCompletesAStudent() {
        // Nine registrations (as many as the curriculum needs), but on the wrong subjects.
        Evaluation e = RegistrationEvaluator.evaluate(Set.of(1L, 2L, 91L, 92L, 93L, 94L, 95L, 96L, 97L), SEM3);
        assertThat(e.status()).isEqualTo(Status.INCOMPLETE);
        assertThat(e.electiveGroupsSatisfied()).isZero();
    }

    @Test
    void noRegistrationIsNotRegisteredEvenWithACurriculum() {
        Evaluation e = RegistrationEvaluator.evaluate(Set.of(), SEM3);
        assertThat(e.status()).isEqualTo(Status.NOT_REGISTERED);
        assertThat(e.compulsoryRegistered()).isZero();
        assertThat(e.compulsoryRequired()).isEqualTo(3);
    }

    @Test
    void onlyExtrasIsIncompleteNotNotRegistered() {
        Evaluation e = RegistrationEvaluator.evaluate(Set.of(99L), SEM3);
        assertThat(e.status()).isEqualTo(Status.INCOMPLETE);
    }

    @Test
    void electivesOnlyCurriculumWorks() {
        Requirements electivesOnly = new Requirements(List.of(), List.of(new Group(1L, "G", 1, List.of(11L, 12L))));
        assertThat(RegistrationEvaluator.evaluate(Set.of(12L), electivesOnly).status()).isEqualTo(Status.ALL_REGISTERED);
        assertThat(RegistrationEvaluator.evaluate(Set.of(13L), electivesOnly).status()).isEqualTo(Status.INCOMPLETE);
    }
}

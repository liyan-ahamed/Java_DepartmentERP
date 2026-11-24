package com.java.erp.modules.leetcode;

import com.java.erp.modules.leetcode.client.LeetCodeClient;
import com.java.erp.modules.leetcode.client.LeetCodeLookupResult;
import com.java.erp.modules.system.entity.Student;
import com.java.erp.support.AcademicTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LeetCodeTopRefreshIntegrationTest extends AcademicTestBase {
    @MockBean private LeetCodeClient client;
    private static final String REFRESH = "/leetcode/top/refresh?year=FOURTH_YEAR";

    @BeforeEach void setupRefresh() {
        batch.setCurrentYear((short) 4);
        batchRepository.saveAndFlush(batch);
        when(client.fetchSolvedStatsAsync(anyString())).thenReturn(CompletableFuture.completedFuture(LeetCodeLookupResult.failure("Unavailable")));
    }

    private Student profile(String username, boolean otherClass) {
        Student student = student(otherClass ? otherSection : section);
        student.setLeetcodeUrl("https://leetcode.com/u/" + username + "/");
        student.setLeetcodeUsername(username);
        student.setLeetcodeTotalSolved(1);
        return studentRepository.saveAndFlush(student);
    }

    private void solved(String username, int total) {
        when(client.fetchSolvedStatsAsync(username)).thenReturn(CompletableFuture.completedFuture(
                LeetCodeLookupResult.success(total, total - 2, 1, 1)));
    }

    @Test void everyOpeningRefreshesAllSectionsAndReordersUsingNewResults() throws Exception {
        Student first = profile("refresh_first", false);
        Student second = profile("refresh_second", true);
        solved("refresh_first", 100); solved("refresh_second", 50);
        mvc.perform(post(REFRESH).header("Authorization", token(staff))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ranking.students[0].studentId").value(first.getId()))
                .andExpect(jsonPath("$.data.refreshedProfiles").value(2));
        solved("refresh_second", 300);
        mvc.perform(post(REFRESH).header("Authorization", token(hod))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ranking.students[0].studentId").value(second.getId()))
                .andExpect(jsonPath("$.data.ranking.students[0].totalSolved").value(300));
        verify(client, times(2)).fetchSolvedStatsAsync("refresh_first");
        verify(client, times(2)).fetchSolvedStatsAsync("refresh_second");
        assertThat(studentRepository.findById(second.getId()).orElseThrow().getLeetcodeLastSyncedAt()).isNotNull();
    }

    @Test void paginationReadsRefreshedRankingWithoutRequestingProfilesAgain() throws Exception {
        for (int i = 0; i < 13; i++) { profile("page_profile_" + i, i % 2 == 0); solved("page_profile_" + i, 100 + i); }
        mvc.perform(post(REFRESH).header("Authorization", token(staff))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ranking.students.length()").value(10))
                .andExpect(jsonPath("$.data.ranking.hasMore").value(true));
        clearInvocations(client);
        mvc.perform(get("/leetcode/top?year=FOURTH_YEAR&page=1").header("Authorization", token(staff)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.students.length()").value(3))
                .andExpect(jsonPath("$.data.students[0].rank").value(11));
        verifyNoInteractions(client);
    }

    @Test void failedProfilesKeepTheirPreviousValuesAndReportFailure() throws Exception {
        Student failed = profile("offline_profile", false);
        LocalDateTime oldSync = LocalDateTime.of(2026, 1, 1, 0, 0);
        failed.setLeetcodeTotalSolved(99); failed.setLeetcodeLastSyncedAt(oldSync);
        studentRepository.saveAndFlush(failed);
        profile("online_profile", true); solved("online_profile", 500);
        Student inactive = profile("inactive_profile", false); inactive.setActive(false); studentRepository.saveAndFlush(inactive);
        mvc.perform(post(REFRESH).header("Authorization", token(staff))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.failedProfiles").value(1))
                .andExpect(jsonPath("$.data.refreshedProfiles").value(1));
        assertThat(failed.getLeetcodeTotalSolved()).isEqualTo(99);
        assertThat(failed.getLeetcodeLastSyncedAt()).isEqualTo(oldSync);
        verify(client, never()).fetchSolvedStatsAsync("inactive_profile");
    }

    @Test void studentsAndAnonymousUsersCannotTriggerYearRefreshAndInvalidYearIsRejected() throws Exception {
        var studentUser = createUser("ROLE_STUDENT", true);
        mvc.perform(post(REFRESH)).andExpect(status().isUnauthorized());
        mvc.perform(post(REFRESH).header("Authorization", token(studentUser))).andExpect(status().isForbidden());
        mvc.perform(post("/leetcode/top/refresh?year=BAD_YEAR").header("Authorization", token(staff)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(client);
    }
}

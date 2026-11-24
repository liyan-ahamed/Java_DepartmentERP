package com.java.erp.modules.leetcode.service;

import com.java.erp.exception.BadRequestException;
import com.java.erp.exception.ResourceNotFoundException;
import com.java.erp.modules.leetcode.client.LeetCodeClient;
import com.java.erp.modules.leetcode.client.LeetCodeLookupResult;
import com.java.erp.modules.leetcode.client.LeetCodeProfileUrls;
import com.java.erp.modules.leetcode.dto.request.UpdateLeetCodeUrlsRequest;
import com.java.erp.modules.leetcode.dto.response.LeetCodeFiltersResponse;
import com.java.erp.modules.leetcode.dto.response.LeetCodeSectionStatsResponse;
import com.java.erp.modules.leetcode.dto.response.LeetCodeStudentStatsResponse;
import com.java.erp.modules.leetcode.dto.response.LeetCodeTopResponse;
import com.java.erp.modules.leetcode.dto.response.LeetCodeTopRefreshResponse;
import com.java.erp.modules.leetcode.entity.AcademicYear;
import com.java.erp.modules.system.entity.Section;
import com.java.erp.modules.system.entity.Student;
import com.java.erp.modules.system.repository.SectionRepository;
import com.java.erp.modules.system.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Reads LeetCode profiles from the students table and refreshes their statistics
 * from LeetCode, one selected year/section at a time.
 *
 * LeetCode is only queried for students with a usable profile URL; students
 * without a URL are returned as URL NOT PROVIDED. Statistics are written only
 * after a successful lookup, so a failed sync never overwrites stored values.
 * No database transaction is held open while LeetCode requests are in flight.
 */
@Service
public class LeetCodeService {

    /** The Top Count ranking always returns exactly this many students per request. */
    private static final int TOP_PAGE_SIZE = 10;

    private final StudentRepository students;
    private final SectionRepository sections;
    private final LeetCodeClient client;
    private final TransactionTemplate transactionTemplate;
    private final int maxProfilesPerRequest;

    public LeetCodeService(StudentRepository students,
                           SectionRepository sections,
                           LeetCodeClient client,
                           TransactionTemplate transactionTemplate,
                           @Value("${app.leetcode.max-profiles-per-request:80}") int maxProfilesPerRequest) {
        this.students = students;
        this.sections = sections;
        this.client = client;
        this.transactionTemplate = transactionTemplate;
        this.maxProfilesPerRequest = maxProfilesPerRequest;
    }

    @Transactional(readOnly = true)
    public LeetCodeFiltersResponse getFilters() {
        Map<Short, List<String>> sectionsByYear = new TreeMap<>();
        for (Section section : sections.findAllForActiveBatches()) {
            sectionsByYear.computeIfAbsent(section.getBatch().getCurrentYear(), y -> new ArrayList<>())
                    .add(section.getSectionName());
        }
        List<LeetCodeFiltersResponse.YearOption> years = Arrays.stream(AcademicYear.values())
                .filter(year -> sectionsByYear.containsKey(year.getYearNumber()))
                .map(year -> new LeetCodeFiltersResponse.YearOption(
                        year.name(), year.getLabel(), sectionsByYear.get(year.getYearNumber())))
                .toList();
        return new LeetCodeFiltersResponse(years);
    }

    /**
     * Fetches the latest LeetCode statistics for the students of exactly one year/section.
     */
    public LeetCodeSectionStatsResponse fetchSectionStats(String yearParam, String sectionParam) {
        AcademicYear year = AcademicYear.parse(yearParam);
        Section section = requireSection(year, sectionParam);

        List<Student> sectionStudents = students.findBySectionIdAndActiveTrueOrderByNameAsc(section.getId());
        long queryable = sectionStudents.stream().filter(s -> s.getLeetcodeUsername() != null).count();
        if (queryable > maxProfilesPerRequest) {
            throw new BadRequestException("Section has " + queryable + " LeetCode profiles; at most "
                    + maxProfilesPerRequest + " can be fetched in one request");
        }

        Map<Long, LeetCodeLookupResult> results = syncFromLeetCode(sectionStudents);
        List<LeetCodeStudentStatsResponse> rows = sectionStudents.stream()
                .map(s -> toResponse(s, results.get(s.getId()), true))
                .toList();

        return new LeetCodeSectionStatsResponse(
                year.name(),
                section.getSectionName(),
                rows.size(),
                results.size(),
                countStatus(rows, "SYNCED"),
                countStatus(rows, "FAILED"),
                countStatus(rows, "URL_NOT_PROVIDED"),
                rows);
    }

    /** Refresh the entire selected year before ranking; pagination reads the saved refresh results. */
    public LeetCodeTopRefreshResponse refreshTopByYear(String yearParam) {
        AcademicYear year = AcademicYear.parse(yearParam);
        List<Student> targets = students.findActiveInActiveBatchOfYear(year.getYearNumber());
        int refreshed = 0;
        int failed = 0;
        // Keep the existing bounded client pool and avoid holding a DB transaction during network calls.
        int chunkSize = Math.max(1, maxProfilesPerRequest);
        for (int offset = 0; offset < targets.size(); offset += chunkSize) {
            var results = syncFromLeetCode(targets.subList(offset, Math.min(offset + chunkSize, targets.size())));
            for (var result : results.values()) {
                if (result.success()) refreshed++;
                else failed++;
            }
        }
        return new LeetCodeTopRefreshResponse(getTopByYear(year.name(), 0), refreshed, failed);
    }

    /**
     * Ranks a whole year by stored statistics, TOP_PAGE_SIZE students per page.
     * Reads the ERP database only; LeetCode is not queried.
     */
    @Transactional(readOnly = true)
    public LeetCodeTopResponse getTopByYear(String yearParam, int page) {
        AcademicYear year = AcademicYear.parse(yearParam);
        if (page < 0) {
            throw new BadRequestException("Page must be 0 or greater");
        }
        Page<Student> result = students.findTopByYear(year.getYearNumber(), PageRequest.of(page, TOP_PAGE_SIZE));
        int firstRank = page * TOP_PAGE_SIZE + 1;
        List<LeetCodeTopResponse.RankedStudent> ranked = new ArrayList<>();
        for (int i = 0; i < result.getContent().size(); i++) {
            Student s = result.getContent().get(i);
            ranked.add(new LeetCodeTopResponse.RankedStudent(
                    firstRank + i,
                    s.getId(),
                    s.getName(),
                    s.getSection().getSectionName(),
                    s.getLeetcodeTotalSolved(),
                    s.getLeetcodeEasySolved(),
                    s.getLeetcodeMediumSolved(),
                    s.getLeetcodeHardSolved(),
                    s.getLeetcodeLastSyncedAt()));
        }
        return new LeetCodeTopResponse(year.name(), page, result.getTotalElements(), result.hasNext(), ranked);
    }

    /**
     * Stores edited profile URLs for students of one year/section. Students outside
     * the section are rejected; unchanged URLs are left alone. A non-blank URL must be
     * a recognisable LeetCode profile URL. Returns the number of students updated.
     */
    @Transactional
    public int updateProfileUrls(UpdateLeetCodeUrlsRequest request) {
        AcademicYear year = AcademicYear.parse(request.year());
        Section section = requireSection(year, request.section());

        Map<Long, Student> sectionStudents = new HashMap<>();
        students.findBySectionIdAndActiveTrueOrderByNameAsc(section.getId())
                .forEach(s -> sectionStudents.put(s.getId(), s));

        List<Student> changed = new ArrayList<>();
        for (UpdateLeetCodeUrlsRequest.StudentUrl entry : request.students()) {
            Student student = sectionStudents.get(entry.studentId());
            if (student == null) {
                throw new BadRequestException("Student " + entry.studentId() + " is not in "
                        + year.getLabel() + " Section " + section.getSectionName());
            }
            String url = LeetCodeProfileUrls.isMissing(entry.profileUrl()) ? null : entry.profileUrl().trim();
            if (url != null && LeetCodeProfileUrls.extractUsername(url) == null) {
                throw new BadRequestException("Invalid LeetCode profile URL for " + student.getName()
                        + ": use a link like https://leetcode.com/u/username/");
            }
            if (!Objects.equals(url, student.getLeetcodeUrl())) {
                LeetCodeRosterService.applyLeetCodeUrl(student, url);
                changed.add(student);
            }
        }
        students.saveAll(changed);
        return changed.size();
    }

    /** The logged-in student's stored LeetCode profile and statistics (no LeetCode request). */
    @Transactional(readOnly = true)
    public LeetCodeStudentStatsResponse getMyProfile(Long userId, String email) {
        return toResponse(requireStudentRecord(userId, email), null, false);
    }

    /** Refreshes the logged-in student's own statistics from LeetCode. */
    public LeetCodeStudentStatsResponse fetchMyStats(Long userId, String email) {
        Student student = requireStudentRecord(userId, email);
        Map<Long, LeetCodeLookupResult> results = syncFromLeetCode(List.of(student));
        return toResponse(student, results.get(student.getId()), true);
    }

    private Section requireSection(AcademicYear year, String sectionParam) {
        if (sectionParam == null || sectionParam.isBlank()) {
            throw new BadRequestException("Section is required");
        }
        return sections.findByYearAndSectionName(year.getYearNumber(), sectionParam.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Section", "name", year.getLabel() + " " + sectionParam.trim()));
    }

    private Student requireStudentRecord(Long userId, String email) {
        return students.findLinkedStudent(userId, email)
                .orElseThrow(() -> new BadRequestException("This Student account is not linked to a student record"));
    }

    /**
     * Queries LeetCode for students with a usable username (one request per profile)
     * and stores successful results. Updates the given entities in memory so the
     * response reflects what was persisted.
     */
    private Map<Long, LeetCodeLookupResult> syncFromLeetCode(List<Student> targets) {
        Map<Long, CompletableFuture<LeetCodeLookupResult>> pending = new LinkedHashMap<>();
        for (Student student : targets) {
            if (student.getLeetcodeUsername() != null) {
                pending.put(student.getId(), client.fetchSolvedStatsAsync(student.getLeetcodeUsername()));
            }
        }
        Map<Long, LeetCodeLookupResult> results = new HashMap<>();
        pending.forEach((id, future) -> results.put(id, future.join()));

        LocalDateTime syncedAt = LocalDateTime.now();
        List<Student> succeeded = targets.stream()
                .filter(s -> results.containsKey(s.getId()) && results.get(s.getId()).success())
                .toList();
        if (!succeeded.isEmpty()) {
            transactionTemplate.executeWithoutResult(status -> succeeded.forEach(s -> {
                LeetCodeLookupResult r = results.get(s.getId());
                students.updateLeetCodeStats(s.getId(), r.totalSolved(), r.easySolved(),
                        r.mediumSolved(), r.hardSolved(), syncedAt);
            }));
            succeeded.forEach(s -> {
                LeetCodeLookupResult r = results.get(s.getId());
                s.setLeetcodeTotalSolved(r.totalSolved());
                s.setLeetcodeEasySolved(r.easySolved());
                s.setLeetcodeMediumSolved(r.mediumSolved());
                s.setLeetcodeHardSolved(r.hardSolved());
                s.setLeetcodeLastSyncedAt(syncedAt);
            });
        }
        return results;
    }

    private LeetCodeStudentStatsResponse toResponse(Student s, LeetCodeLookupResult result, boolean syncAttempted) {
        String status;
        String message = null;
        if (LeetCodeProfileUrls.isMissing(s.getLeetcodeUrl())) {
            status = "URL_NOT_PROVIDED";
        } else if (s.getLeetcodeUsername() == null) {
            status = "INVALID_URL";
            message = "Not a recognisable LeetCode profile URL";
        } else if (result == null) {
            status = syncAttempted ? "FAILED" : "NOT_SYNCED";
        } else if (result.success()) {
            status = "SYNCED";
        } else {
            status = "FAILED";
            message = result.error();
        }
        return new LeetCodeStudentStatsResponse(
                s.getId(),
                s.getName(),
                s.getRegisterNumber(),
                LeetCodeProfileUrls.isMissing(s.getLeetcodeUrl()) ? LeetCodeProfileUrls.URL_NOT_PROVIDED : s.getLeetcodeUrl(),
                s.getLeetcodeUsername(),
                s.getLeetcodeTotalSolved(),
                s.getLeetcodeEasySolved(),
                s.getLeetcodeMediumSolved(),
                s.getLeetcodeHardSolved(),
                s.getLeetcodeLastSyncedAt(),
                status,
                message);
    }

    private static int countStatus(List<LeetCodeStudentStatsResponse> rows, String status) {
        return (int) rows.stream().filter(r -> status.equals(r.status())).count();
    }
}

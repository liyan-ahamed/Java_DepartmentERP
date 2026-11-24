package com.java.erp.modules.leetcode.service;

import com.java.erp.modules.leetcode.client.LeetCodeProfileUrls;
import com.java.erp.modules.leetcode.entity.AcademicYear;
import com.java.erp.modules.system.entity.Batch;
import com.java.erp.modules.system.entity.Section;
import com.java.erp.modules.system.entity.Student;
import com.java.erp.modules.system.repository.BatchRepository;
import com.java.erp.modules.system.repository.SectionRepository;
import com.java.erp.modules.system.repository.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Applies the provided Second-Year roster (ordered names + LeetCode URLs) to the
 * existing students table.
 *
 * - Rows are split strictly by list order: 1-63 -> A, 64-126 -> B, 127-189 -> C, 190-252 -> D.
 * - Existing Second-Year student records are reused when the name matches exactly,
 *   so no duplicate student records are created; otherwise a new record is added.
 * - Second-Year records not present in the roster are deactivated, never deleted.
 * - Re-running with the same roster is a no-op.
 */
@Service
public class LeetCodeRosterService {

    public static final int SECOND_YEAR_STUDENTS = 252;
    public static final List<String> SECOND_YEAR_SECTIONS = List.of("A", "B", "C", "D");
    public static final int STUDENTS_PER_SECTION = SECOND_YEAR_STUDENTS / SECOND_YEAR_SECTIONS.size();

    private static final Logger logger = LoggerFactory.getLogger(LeetCodeRosterService.class);

    private final StudentRepository students;
    private final SectionRepository sections;
    private final BatchRepository batches;

    public LeetCodeRosterService(StudentRepository students, SectionRepository sections, BatchRepository batches) {
        this.students = students;
        this.sections = sections;
        this.batches = batches;
    }

    public record RosterEntry(String name, String leetcodeUrl) {}

    @Transactional
    public void applySecondYearRoster(List<RosterEntry> roster) {
        if (roster.size() != SECOND_YEAR_STUDENTS) {
            throw new IllegalArgumentException("Second-Year roster must contain exactly " + SECOND_YEAR_STUDENTS
                    + " students, found " + roster.size());
        }
        for (int i = 0; i < roster.size(); i++) {
            if (roster.get(i).name() == null || roster.get(i).name().isBlank()) {
                throw new IllegalArgumentException("Roster row " + (i + 1) + " has no student name");
            }
        }

        List<Batch> secondYear = batches.findByActiveTrueAndCurrentYear(AcademicYear.SECOND_YEAR.getYearNumber());
        if (secondYear.size() != 1) {
            throw new IllegalStateException("Expected exactly one active Second-Year batch, found " + secondYear.size());
        }
        Batch batch = secondYear.get(0);
        Map<String, Section> sectionByName = ensureSections(batch);

        // Existing records in the batch, keyed by exact name (several students may share a name).
        Map<String, Deque<Student>> existingByName = new HashMap<>();
        for (Student s : students.findByBatchId(batch.getId())) {
            existingByName.computeIfAbsent(s.getName(), n -> new ArrayDeque<>()).add(s);
        }

        Set<String> usedRegisterNumbers = new HashSet<>();
        int created = 0, reused = 0;
        List<Student> toSave = new ArrayList<>();
        for (int i = 0; i < roster.size(); i++) {
            RosterEntry entry = roster.get(i);
            String sectionName = SECOND_YEAR_SECTIONS.get(i / STUDENTS_PER_SECTION);
            Section section = sectionByName.get(sectionName);

            Deque<Student> matches = existingByName.get(entry.name());
            Student student = matches == null ? null : matches.poll();
            if (student == null) {
                student = new Student(nextRegisterNumber(batch, sectionName, usedRegisterNumbers), entry.name(), null);
                student.setBatch(batch);
                created++;
            } else {
                reused++;
            }
            student.setSection(section);
            student.setActive(true);
            applyLeetCodeUrl(student, entry.leetcodeUrl());
            toSave.add(student);
        }

        int deactivated = 0;
        for (Deque<Student> leftovers : existingByName.values()) {
            for (Student s : leftovers) {
                if (s.isActive()) {
                    s.setActive(false);
                    toSave.add(s);
                    deactivated++;
                }
            }
        }

        students.saveAll(toSave);
        logger.info("Second-Year LeetCode roster applied: {} students ({} reused, {} created), {} deactivated",
                roster.size(), reused, created, deactivated);
    }

    private Map<String, Section> ensureSections(Batch batch) {
        Map<String, Section> byName = new HashMap<>();
        for (Section s : sections.findByBatchIdOrderBySectionNameAsc(batch.getId())) {
            byName.put(s.getSectionName().toUpperCase(), s);
        }
        for (String name : SECOND_YEAR_SECTIONS) {
            byName.computeIfAbsent(name, n -> {
                logger.info("Creating Second-Year section {}", n);
                return sections.save(new Section(batch, n));
            });
        }
        return byName;
    }

    /** Stores the URL as provided; a changed URL clears statistics that belonged to the old profile. */
    static void applyLeetCodeUrl(Student student, String rawUrl) {
        String url = LeetCodeProfileUrls.isMissing(rawUrl) ? null : rawUrl.trim();
        if (!Objects.equals(url, student.getLeetcodeUrl())) {
            student.setLeetcodeTotalSolved(null);
            student.setLeetcodeEasySolved(null);
            student.setLeetcodeMediumSolved(null);
            student.setLeetcodeHardSolved(null);
            student.setLeetcodeLastSyncedAt(null);
        }
        student.setLeetcodeUrl(url);
        student.setLeetcodeUsername(LeetCodeProfileUrls.extractUsername(url));
    }

    /** Follows the existing seed format: REG + admission year + section + sequence. */
    private String nextRegisterNumber(Batch batch, String section, Set<String> used) {
        for (int seq = 1; ; seq++) {
            String candidate = "REG" + batch.getAdmissionYear() + section + String.format("%03d", seq);
            if (!used.contains(candidate) && !students.existsByRegisterNumber(candidate)) {
                used.add(candidate);
                return candidate;
            }
        }
    }
}

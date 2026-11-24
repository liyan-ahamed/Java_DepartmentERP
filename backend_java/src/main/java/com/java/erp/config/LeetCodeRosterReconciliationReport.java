package com.java.erp.config;

import com.java.erp.modules.system.entity.Batch;
import com.java.erp.modules.system.entity.Student;
import com.java.erp.modules.system.repository.BatchRepository;
import com.java.erp.modules.system.repository.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * READ-ONLY report (dev profile, logged at startup) of how the LeetCode Second-Year roster CSV maps
 * to student records. It never writes anything: not the CSV, not the students table.
 *
 * The CSV has only name + LeetCode URL (no register number), so matching is by exact name against the
 * active students of the Second-Year batch, the same rule LeetCodeRosterService uses.
 */
@Component
@Profile("dev")
@Order(4)
public class LeetCodeRosterReconciliationReport implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(LeetCodeRosterReconciliationReport.class);
    private static final String DEFAULT_ROSTER = "file:data/second-year-leetcode.csv";

    private final StudentRepository students;
    private final BatchRepository batches;
    private final ResourceLoader resourceLoader;
    private final String rosterLocation;

    public LeetCodeRosterReconciliationReport(StudentRepository students, BatchRepository batches, ResourceLoader resourceLoader,
                                              @Value("${app.leetcode.second-year-roster:}") String rosterLocation) {
        this.students = students;
        this.batches = batches;
        this.resourceLoader = resourceLoader;
        this.rosterLocation = rosterLocation == null || rosterLocation.isBlank() ? DEFAULT_ROSTER : rosterLocation.trim();
    }

    @Override
    @Transactional(readOnly = true)
    public void run(String... args) {
        try {
            report();
        } catch (Exception ex) {
            logger.warn("LeetCode roster reconciliation report skipped: {}", ex.getMessage());
        }
    }

    void report() throws Exception {
        long activeStudents = students.countActiveInActiveBatches();
        List<Batch> secondYear = batches.findByActiveTrueAndCurrentYear((short) 2);
        if (secondYear.size() != 1) {
            logger.warn("[Roster reconciliation] expected one active Second-Year batch, found {}", secondYear.size());
            return;
        }
        List<Student> batchStudents = students.findByBatchId(secondYear.get(0).getId()).stream().filter(Student::isActive).toList();
        Resource csv = resourceLoader.getResource(rosterLocation);
        if (!csv.exists()) {
            logger.warn("[Roster reconciliation] roster CSV not found at {}", rosterLocation);
            return;
        }
        List<String> names = readNames(csv);

        Map<String, Long> csvCounts = names.stream().collect(Collectors.groupingBy(n -> n, TreeMap::new, Collectors.counting()));
        Map<String, Long> dbCounts = batchStudents.stream().collect(Collectors.groupingBy(Student::getName, TreeMap::new, Collectors.counting()));
        long linked = 0;
        List<String> unmatchedCsv = new ArrayList<>();
        for (Map.Entry<String, Long> e : csvCounts.entrySet()) {
            long inDb = dbCounts.getOrDefault(e.getKey(), 0L);
            linked += Math.min(inDb, e.getValue());
            if (inDb < e.getValue()) unmatchedCsv.add(e.getKey() + " (missing " + (e.getValue() - inDb) + ")");
        }
        List<String> extraDb = dbCounts.entrySet().stream()
                .filter(e -> e.getValue() > csvCounts.getOrDefault(e.getKey(), 0L))
                .map(Map.Entry::getKey).toList();
        List<String> duplicateNames = csvCounts.entrySet().stream().filter(e -> e.getValue() > 1).map(Map.Entry::getKey).toList();
        long withUrl = batchStudents.stream().filter(s -> s.getLeetcodeUrl() != null).count();

        logger.info("[Roster reconciliation] active students (active batches): {}; Second-Year active: {}; CSV rows: {}; "
                        + "linked to roster: {}; with LeetCode URL: {}; unmatched CSV rows: {}; Second-Year students not in CSV: {}; "
                        + "duplicate names in CSV: {}",
                activeStudents, batchStudents.size(), names.size(), linked, withUrl, unmatchedCsv.size(), extraDb.size(),
                duplicateNames);
        if (!unmatchedCsv.isEmpty()) {
            logger.warn("[Roster reconciliation] CSV names without a student record (first 20): {}",
                    unmatchedCsv.subList(0, Math.min(20, unmatchedCsv.size())));
        }
    }

    private static List<String> readNames(Resource resource) throws Exception {
        List<String> names = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (first && line.startsWith("﻿")) line = line.substring(1);
                if (line.isBlank()) continue;
                String name = firstCell(line).trim();
                if (first && name.equalsIgnoreCase("name")) {
                    first = false;
                    continue;
                }
                first = false;
                names.add(name);
            }
        }
        return names;
    }

    /** First CSV cell, honouring quotes. */
    private static String firstCell(String line) {
        if (!line.startsWith("\"")) {
            int comma = line.indexOf(',');
            return comma < 0 ? line : line.substring(0, comma);
        }
        StringBuilder cell = new StringBuilder();
        for (int i = 1; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                cell.append('"');
                i++;
            } else if (c == '"') {
                break;
            } else {
                cell.append(c);
            }
        }
        return cell.toString();
    }
}

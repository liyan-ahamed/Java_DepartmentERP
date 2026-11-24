package com.java.erp.modules.leetcode.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads the Second-Year student list (name, LeetCode URL) on startup when
 * app.leetcode.second-year-roster points to a CSV file, e.g.
 *   LEETCODE_SECOND_YEAR_ROSTER=file:C:/data/second-year-leetcode.csv
 *
 * CSV format (header row optional, order = section order):
 *   name,leetcode_url
 *   "Student Name",https://leetcode.com/u/username/
 *   "Another Student",URL NOT PROVIDED
 *
 * Does nothing when the property is empty. An invalid file is logged and skipped.
 */
@Component
public class LeetCodeRosterImporter implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(LeetCodeRosterImporter.class);

    private final LeetCodeRosterService rosterService;
    private final ResourceLoader resourceLoader;
    private final String rosterLocation;

    public LeetCodeRosterImporter(LeetCodeRosterService rosterService,
                                  ResourceLoader resourceLoader,
                                  @Value("${app.leetcode.second-year-roster:}") String rosterLocation) {
        this.rosterService = rosterService;
        this.resourceLoader = resourceLoader;
        this.rosterLocation = rosterLocation;
    }

    @Override
    public void run(String... args) {
        if (rosterLocation == null || rosterLocation.isBlank()) {
            return;
        }
        try {
            Resource resource = resourceLoader.getResource(rosterLocation.trim());
            if (!resource.exists()) {
                logger.error("Second-Year LeetCode roster not found at '{}'; skipping import", rosterLocation);
                return;
            }
            rosterService.applySecondYearRoster(readRoster(resource));
        } catch (Exception ex) {
            logger.error("Second-Year LeetCode roster import failed; no student data was changed: {}", ex.getMessage());
        }
    }

    static List<LeetCodeRosterService.RosterEntry> readRoster(Resource resource) throws Exception {
        List<LeetCodeRosterService.RosterEntry> entries = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (first && line.startsWith("\uFEFF")) {
                    line = line.substring(1);
                }
                if (line.isBlank()) {
                    first = false;
                    continue;
                }
                List<String> cells = parseCsvLine(line);
                if (first && cells.get(0).trim().equalsIgnoreCase("name")) {
                    first = false;
                    continue;
                }
                first = false;
                String name = cells.get(0).trim();
                String url = cells.size() > 1 ? cells.get(1).trim() : "";
                entries.add(new LeetCodeRosterService.RosterEntry(name, url));
            }
        }
        return entries;
    }

    /** Minimal RFC 4180 line parser: supports quoted cells with commas and "" escapes. */
    static List<String> parseCsvLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    cell.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                cells.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        cells.add(cell.toString());
        return cells;
    }
}

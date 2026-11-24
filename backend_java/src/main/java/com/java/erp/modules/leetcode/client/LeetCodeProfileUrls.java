package com.java.erp.modules.leetcode.client;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Extracts a LeetCode username from a profile URL exactly as provided.
 * Never guesses: anything that is not a recognisable leetcode.com profile URL yields null.
 */
public final class LeetCodeProfileUrls {

    public static final String URL_NOT_PROVIDED = "URL NOT PROVIDED";

    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_.-]{1,100}");

    /** First path segments on leetcode.com that are site pages, not usernames. */
    private static final Set<String> RESERVED = Set.of(
            "problems", "problemset", "contest", "discuss", "explore", "study-plan", "studyplan",
            "submissions", "profile", "accounts", "assessment", "interview", "store", "subscribe",
            "company", "tag", "list", "playground", "support", "jobs", "graphql", "api");

    private LeetCodeProfileUrls() {
    }

    /** True when the roster value means no URL was supplied. */
    public static boolean isMissing(String url) {
        return url == null || url.isBlank() || URL_NOT_PROVIDED.equalsIgnoreCase(url.trim());
    }

    /**
     * Returns the username for URLs such as https://leetcode.com/u/{name}/ or
     * https://leetcode.com/{name}/, or null when the URL is not a usable profile URL.
     */
    public static String extractUsername(String url) {
        if (isMissing(url)) {
            return null;
        }
        String value = url.trim();
        if (!value.matches("(?i)^https?://.*")) {
            value = "https://" + value;
        }
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        if (!host.equals("leetcode.com") && !host.equals("www.leetcode.com")) {
            return null;
        }
        List<String> segments = Arrays.stream(uri.getPath() == null ? new String[0] : uri.getPath().split("/"))
                .filter(s -> !s.isBlank())
                .toList();
        if (segments.isEmpty()) {
            return null;
        }
        String candidate = segments.get(0).equalsIgnoreCase("u") && segments.size() > 1
                ? segments.get(1)
                : segments.get(0);
        if (RESERVED.contains(candidate.toLowerCase()) || candidate.equalsIgnoreCase("u")) {
            return null;
        }
        return USERNAME.matcher(candidate).matches() ? candidate : null;
    }
}

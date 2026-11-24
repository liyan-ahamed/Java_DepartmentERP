package com.java.erp.modules.leetcode.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Server-side client for LeetCode's public GraphQL endpoint.
 * Each profile is looked up with its own request; lookups run on a small
 * bounded pool so a section is fetched without flooding LeetCode.
 */
@Component
public class LeetCodeClient {

    private static final Logger logger = LoggerFactory.getLogger(LeetCodeClient.class);

    private static final String SOLVED_QUERY = """
            query userProblemsSolved($username: String!) {
              matchedUser(username: $username) {
                submitStatsGlobal { acSubmissionNum { difficulty count } }
              }
            }""";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor;

    public LeetCodeClient(ObjectMapper objectMapper,
                          @Value("${app.leetcode.graphql-url:https://leetcode.com/graphql}") String graphqlUrl,
                          @Value("${app.leetcode.timeout-ms:10000}") int timeoutMs,
                          @Value("${app.leetcode.concurrency:5}") int concurrency) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.restClient = RestClient.builder()
                .baseUrl(graphqlUrl)
                .requestFactory(requestFactory)
                .defaultHeader("Referer", "https://leetcode.com")
                .defaultHeader("User-Agent", "Mozilla/5.0 (compatible; DeptERP-LeetCodeTracker/1.0)")
                .build();
        this.objectMapper = objectMapper;
        this.executor = Executors.newFixedThreadPool(Math.max(1, concurrency));
    }

    /** Asynchronously fetches solved counts for one username. Never completes exceptionally. */
    public CompletableFuture<LeetCodeLookupResult> fetchSolvedStatsAsync(String username) {
        return CompletableFuture.supplyAsync(() -> fetchSolvedStats(username), executor);
    }

    public LeetCodeLookupResult fetchSolvedStats(String username) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("query", SOLVED_QUERY);
            body.put("variables", Map.of("username", username));

            String raw = restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(raw == null ? "{}" : raw);
            JsonNode user = root.path("data").path("matchedUser");
            if (user.isMissingNode() || user.isNull()) {
                return LeetCodeLookupResult.failure("LeetCode profile not found");
            }
            JsonNode counts = user.path("submitStatsGlobal").path("acSubmissionNum");
            if (!counts.isArray()) {
                return LeetCodeLookupResult.failure("LeetCode returned no statistics");
            }

            Integer total = null, easy = null, medium = null, hard = null;
            for (JsonNode entry : counts) {
                int count = entry.path("count").asInt();
                switch (entry.path("difficulty").asText()) {
                    case "All" -> total = count;
                    case "Easy" -> easy = count;
                    case "Medium" -> medium = count;
                    case "Hard" -> hard = count;
                    default -> { }
                }
            }
            if (total == null || easy == null || medium == null || hard == null) {
                return LeetCodeLookupResult.failure("LeetCode returned incomplete statistics");
            }
            return LeetCodeLookupResult.success(total, easy, medium, hard);
        } catch (Exception ex) {
            logger.warn("LeetCode lookup failed for '{}': {}", username, ex.getMessage());
            return LeetCodeLookupResult.failure("LeetCode is unavailable for this profile right now");
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}

package com.java.erp.modules.audit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Central redaction for audit payloads. Every value written to (or read from)
 * audit_logs passes through here, so passwords, password hashes, JWTs, refresh
 * tokens, authorization headers and secrets are never stored or returned.
 */
public final class AuditSanitizer {

    public static final String REDACTED = "[REDACTED]";

    private static final Pattern SENSITIVE_KEY =
            Pattern.compile("(?i).*(password|passwd|secret|token|authorization|jwt|credential).*");
    private static final Pattern JWT_VALUE =
            Pattern.compile("^[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}$");
    private static final Pattern BCRYPT_VALUE = Pattern.compile("^\\$2[aby]?\\$.*");

    private AuditSanitizer() {
    }

    /**
     * Serialize an object to JSON with all sensitive fields redacted.
     * Returns null if the value is null or cannot be serialized.
     */
    public static String toSafeJson(ObjectMapper objectMapper, Object value) {
        if (value == null) {
            return null;
        }
        try {
            JsonNode node = objectMapper.valueToTree(value);
            return redact(node).toString();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * Redact an already-stored JSON string (used when returning older audit rows).
     */
    public static String sanitizeStored(ObjectMapper objectMapper, String json) {
        if (json == null) {
            return null;
        }
        try {
            return redact(objectMapper.readTree(json)).toString();
        } catch (Exception ex) {
            return isSensitiveValue(json) ? REDACTED : json;
        }
    }

    private static JsonNode redact(JsonNode node) {
        if (node instanceof ObjectNode object) {
            List<String> names = new ArrayList<>();
            object.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                JsonNode child = object.get(name);
                if (SENSITIVE_KEY.matcher(name).matches() || isSensitiveValue(child)) {
                    object.put(name, REDACTED);
                } else {
                    redact(child);
                }
            }
        } else if (node instanceof ArrayNode array) {
            for (int i = 0; i < array.size(); i++) {
                if (isSensitiveValue(array.get(i))) {
                    array.set(i, REDACTED);
                } else {
                    redact(array.get(i));
                }
            }
        }
        return node;
    }

    private static boolean isSensitiveValue(JsonNode node) {
        return node != null && node.isTextual() && isSensitiveValue(node.asText());
    }

    private static boolean isSensitiveValue(String value) {
        String trimmed = value.trim();
        return JWT_VALUE.matcher(trimmed).matches()
                || BCRYPT_VALUE.matcher(trimmed).matches()
                || trimmed.regionMatches(true, 0, "Bearer ", 0, 7);
    }
}

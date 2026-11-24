package com.java.erp.modules.audit.service;

import com.java.erp.common.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

/**
 * Writes an audit row with a short, explicit summary (ids, counts, status changes) for the acting
 * user. Used where the method result would be too large or too sensitive to store (bulk saves,
 * money, marks), instead of {@code @Auditable}.
 */
@Component
public class AuditRecorder {

    private final AuditService auditService;

    public AuditRecorder(AuditService auditService) {
        this.auditService = auditService;
    }

    public void record(String action, String module, String entityType, Long entityId, Map<String, ?> summary) {
        Long userId = SecurityUtils.getCurrentUserId();
        String username = userId != null ? SecurityUtils.getCurrentUserEmail() : "system";
        auditService.log(userId, username, action, module, entityType, entityId, summary, currentRequest());
    }

    private static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest() : null;
    }
}

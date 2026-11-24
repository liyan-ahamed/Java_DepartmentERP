package com.java.erp.modules.portfolio.service;

import com.java.erp.common.util.SecurityUtils;
import com.java.erp.modules.audit.service.AuditService;
import com.java.erp.modules.portfolio.entity.PortfolioEntry;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Audit rows for portfolio changes. Stores a short summary (ids, type, status change, reviewer),
 * never the description, evidence link or review note.
 */
@Component
public class PortfolioAuditLogger {

    static final String MODULE = "PORTFOLIO";
    static final String ENTITY_TYPE = "PortfolioEntry";

    private final AuditService auditService;

    public PortfolioAuditLogger(AuditService auditService) {
        this.auditService = auditService;
    }

    /** newStatus null = the entry was deleted. */
    public void log(String action, PortfolioEntry entry, PortfolioEntry.Status oldStatus, PortfolioEntry.Status newStatus) {
        Long userId = SecurityUtils.getCurrentUserId();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("entryId", entry.getId());
        summary.put("studentId", entry.getStudent().getId());
        summary.put("activityType", entry.getActivityType());
        summary.put("oldStatus", oldStatus);
        summary.put("newStatus", newStatus);
        if (newStatus == PortfolioEntry.Status.VERIFIED || newStatus == PortfolioEntry.Status.REJECTED) {
            summary.put("reviewerId", userId);
        }
        auditService.log(userId, SecurityUtils.getCurrentUserEmail(), action, MODULE, ENTITY_TYPE,
                entry.getId(), summary, currentRequest());
    }

    private static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest() : null;
    }
}

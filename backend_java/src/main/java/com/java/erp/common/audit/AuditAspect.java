package com.java.erp.common.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.erp.common.util.SecurityUtils;
import com.java.erp.modules.audit.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * AOP Aspect to intercept methods annotated with @Auditable
 * and automatically record an audit log entry after a successful call.
 *
 * The method's return value is stored as the new value (redacted by AuditService).
 * Request payloads are not stored: they may contain credentials and do not
 * describe the entity's previous state.
 */
@Aspect
@Component
public class AuditAspect {

    private static final Logger logger = LoggerFactory.getLogger(AuditAspect.class);

    private final AuditService auditService;
    private final HttpServletRequest request;
    private final ObjectMapper objectMapper;

    public AuditAspect(AuditService auditService, HttpServletRequest request, ObjectMapper objectMapper) {
        this.auditService = auditService;
        this.request = request;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(auditable)")
    public Object auditMethod(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        Object result = joinPoint.proceed();

        try {
            Long userId = SecurityUtils.getCurrentUserId();
            String username = userId != null ? SecurityUtils.getCurrentUserEmail() : "system";

            auditService.log(
                    userId,
                    username,
                    auditable.action(),
                    auditable.module(),
                    auditable.entityType(),
                    resolveEntityId(joinPoint, auditable, result, userId),
                    result,
                    request
            );
        } catch (Exception e) {
            logger.error("Failed to record audit log for {}", ((MethodSignature) joinPoint.getSignature()).getMethod(), e);
        }

        return result;
    }

    /**
     * The affected entity's id: the first Long argument (e.g. updateUser(id, ...)),
     * else the result's "id" or "user.id", else the acting user for User actions
     * such as logout.
     */
    private Long resolveEntityId(ProceedingJoinPoint joinPoint, Auditable auditable, Object result, Long userId) {
        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof Long id) {
                return id;
            }
        }
        if (result != null) {
            try {
                JsonNode node = objectMapper.valueToTree(result);
                if (node.path("id").isIntegralNumber()) {
                    return node.get("id").asLong();
                }
                if (node.path("user").path("id").isIntegralNumber()) {
                    return node.get("user").get("id").asLong();
                }
            } catch (IllegalArgumentException ignored) {
                // Result is not serializable; fall through
            }
        }
        return "User".equals(auditable.entityType()) ? userId : null;
    }
}

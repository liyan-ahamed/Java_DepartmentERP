-- =============================================
-- V22__clear_sensitive_audit_payloads.sql
-- Audit entries written before payload redaction existed stored raw request and
-- response bodies, including login passwords and issued JWTs. Permanently clear
-- the payload columns of any row that may contain such data. The rest of each
-- row (who, what, when, where) is kept and no rows are deleted.
-- =============================================

UPDATE audit_logs
SET old_value = NULL,
    new_value = NULL
WHERE old_value ILIKE '%password%'
   OR old_value ILIKE '%token%'
   OR new_value ILIKE '%password%'
   OR new_value ILIKE '%token%';

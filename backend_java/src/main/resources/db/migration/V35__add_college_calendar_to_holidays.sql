-- =============================================
-- V35__add_college_calendar_to_holidays.sql
-- The college's own holiday calendar (scope COLLEGE) is authoritative for the ERP.
-- When a year has COLLEGE rows, only those are effective; NATIONAL / TAMIL_NADU rows stay
-- stored for provenance. category is what the frontend displays.
-- =============================================

ALTER TABLE holidays ADD COLUMN category VARCHAR(30) NOT NULL DEFAULT 'PUBLIC_HOLIDAY';
ALTER TABLE holidays ADD CONSTRAINT ck_holidays_category CHECK (category IN ('GENERAL_HOLIDAY', 'PUBLIC_HOLIDAY'));

ALTER TABLE holidays DROP CONSTRAINT ck_holidays_scope;
ALTER TABLE holidays ADD CONSTRAINT ck_holidays_scope CHECK (scope IN ('NATIONAL', 'TAMIL_NADU', 'COLLEGE'));

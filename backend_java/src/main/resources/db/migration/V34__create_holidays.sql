-- =============================================
-- V34__create_holidays.sql
-- Cached public holidays (India central government + Tamil Nadu state).
-- Filled and refreshed by the holiday sync (idempotent upsert, never bulk-deleted).
-- Holidays are calendar exceptions; they never change the recurring timetable.
-- =============================================

CREATE TABLE holidays (
    id              BIGSERIAL     PRIMARY KEY,
    holiday_date    DATE          NOT NULL,
    name            VARCHAR(150)  NOT NULL,
    scope           VARCHAR(20)   NOT NULL,
    holiday_year    INTEGER       NOT NULL,
    source          VARCHAR(150)  NOT NULL,
    last_synced_at  TIMESTAMP     NOT NULL,
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP     NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_holidays_date_scope_name UNIQUE (holiday_date, scope, name),
    CONSTRAINT ck_holidays_scope CHECK (scope IN ('NATIONAL', 'TAMIL_NADU')),
    CONSTRAINT ck_holidays_name_not_blank CHECK (BTRIM(name) <> ''),
    CONSTRAINT ck_holidays_year CHECK (holiday_year = EXTRACT(YEAR FROM holiday_date))
);

CREATE INDEX idx_holidays_year_scope ON holidays(holiday_year, scope);
CREATE INDEX idx_holidays_date       ON holidays(holiday_date);

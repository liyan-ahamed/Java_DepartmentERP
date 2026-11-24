-- =============================================
-- V40__add_holiday_calendar_management.sql
-- HOD-managed college holiday calendar with publication state.
--
--   holiday_calendars  one row per CALENDAR year (Jan-Dec): DRAFT or PUBLISHED.
--                      No row = NOT_CREATED. Only PUBLISHED college holidays affect the
--                      timetable, attendance and dashboards; government lists never do.
--   holidays           + end_date (inclusive, same year, at most 60 days), description,
--                      finer categories; last_synced_at is NULL for holidays entered in the ERP.
--   holiday_batches    cohorts a holiday applies to; no rows = the whole institution.
--
-- Existing data is kept: every year that already has college holidays (2026) gets a
-- PUBLISHED calendar, so what users see today does not change. No category is remapped.
-- =============================================

CREATE TABLE holiday_calendars (
    id             BIGSERIAL    PRIMARY KEY,
    calendar_year  INTEGER      NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    published_at   TIMESTAMP,
    published_by   BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_holiday_calendars_year UNIQUE (calendar_year),
    CONSTRAINT ck_holiday_calendars_year CHECK (calendar_year BETWEEN 2000 AND 2100),
    CONSTRAINT ck_holiday_calendars_status CHECK (status IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT ck_holiday_calendars_published CHECK ((status = 'PUBLISHED') = (published_at IS NOT NULL))
);

ALTER TABLE holidays ADD COLUMN end_date DATE;
ALTER TABLE holidays ADD COLUMN description VARCHAR(500);
ALTER TABLE holidays ALTER COLUMN last_synced_at DROP NOT NULL;

ALTER TABLE holidays ADD CONSTRAINT ck_holidays_end_date CHECK (
    end_date IS NULL
    OR (end_date >= holiday_date
        AND EXTRACT(YEAR FROM end_date) = holiday_year
        AND end_date - holiday_date < 60));

ALTER TABLE holidays DROP CONSTRAINT ck_holidays_category;
ALTER TABLE holidays ADD CONSTRAINT ck_holidays_category CHECK (category IN
    ('GENERAL_HOLIDAY', 'PUBLIC_HOLIDAY', 'NATIONAL', 'STATE', 'INSTITUTIONAL', 'ACADEMIC_BREAK', 'OTHER'));
-- PUBLIC_HOLIDAY marks the government reference lists only; college holidays use the other categories.
ALTER TABLE holidays ADD CONSTRAINT ck_holidays_public_category CHECK ((category = 'PUBLIC_HOLIDAY') = (scope <> 'COLLEGE'));

-- One college holiday per name per year (case-insensitive).
CREATE UNIQUE INDEX uq_holidays_college_year_name ON holidays (holiday_year, LOWER(name)) WHERE scope = 'COLLEGE';

CREATE TABLE holiday_batches (
    holiday_id  BIGINT  NOT NULL REFERENCES holidays(id) ON DELETE CASCADE,
    batch_id    BIGINT  NOT NULL REFERENCES batches(id),

    PRIMARY KEY (holiday_id, batch_id)
);

CREATE INDEX idx_holiday_batches_batch ON holiday_batches(batch_id);

INSERT INTO holiday_calendars (calendar_year, status, published_at, created_at, updated_at)
SELECT DISTINCT holiday_year, 'PUBLISHED', NOW(), NOW(), NOW()
FROM holidays
WHERE scope = 'COLLEGE';

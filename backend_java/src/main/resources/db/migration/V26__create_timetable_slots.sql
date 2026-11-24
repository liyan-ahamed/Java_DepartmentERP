-- =============================================
-- V26__create_timetable_slots.sql
-- Recurring weekly academic timetable: when a subject offering is taught.
-- (Not related to the retired HOD -> Staff `schedules` tables.)
--
-- Overlap rules (same section or same staff member, same day, overlapping times,
-- both slots active) are checked in the service, because section and staff live
-- on the subject offering.
-- =============================================

CREATE TABLE timetable_slots (
    id                   BIGSERIAL    PRIMARY KEY,
    subject_offering_id  BIGINT       NOT NULL REFERENCES subject_offerings(id),
    day_of_week          VARCHAR(10)  NOT NULL,
    period_number        INTEGER,
    start_time           TIME         NOT NULL,
    end_time             TIME         NOT NULL,
    room                 VARCHAR(50),
    slot_type            VARCHAR(20)  NOT NULL DEFAULT 'LECTURE',
    is_active            BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_timetable_slots_day CHECK (day_of_week IN (
        'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')),
    CONSTRAINT ck_timetable_slots_period CHECK (period_number IS NULL OR period_number > 0),
    CONSTRAINT ck_timetable_slots_times CHECK (start_time < end_time),
    CONSTRAINT ck_timetable_slots_type CHECK (slot_type IN ('LECTURE', 'LAB', 'TUTORIAL', 'OTHER'))
);

CREATE INDEX idx_timetable_slots_offering ON timetable_slots(subject_offering_id);
CREATE INDEX idx_timetable_slots_day      ON timetable_slots(day_of_week, start_time) WHERE is_active;

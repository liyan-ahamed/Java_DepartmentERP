-- =============================================
-- V28__create_lesson_plans.sql
-- Lesson Plan: which topic should be taught when, per subject offering.
-- (The timetable says when a class meets; the lesson plan says what is taught.)
-- =============================================

CREATE TABLE lesson_plans (
    id                   BIGSERIAL      PRIMARY KEY,
    subject_offering_id  BIGINT         NOT NULL REFERENCES subject_offerings(id),
    unit_number          INTEGER,
    topic                VARCHAR(255)   NOT NULL,
    description          VARCHAR(2000),
    planned_date         DATE           NOT NULL,
    completed_date       DATE,
    status               VARCHAR(20)    NOT NULL DEFAULT 'PLANNED',
    notes                VARCHAR(1000),
    created_by           BIGINT         NOT NULL REFERENCES users(id),
    created_at           TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP      NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_lesson_plans_topic_not_blank CHECK (BTRIM(topic) <> ''),
    CONSTRAINT ck_lesson_plans_unit CHECK (unit_number IS NULL OR unit_number > 0),
    CONSTRAINT ck_lesson_plans_status CHECK (status IN ('PLANNED', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_lesson_plans_completed_date CHECK ((status = 'COMPLETED') = (completed_date IS NOT NULL))
);

CREATE INDEX idx_lesson_plans_offering ON lesson_plans(subject_offering_id, planned_date);
CREATE INDEX idx_lesson_plans_status   ON lesson_plans(status);

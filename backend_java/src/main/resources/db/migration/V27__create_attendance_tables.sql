-- =============================================
-- V27__create_attendance_tables.sql
-- Student academic attendance.
--
--   attendance_sessions -> one class meeting of a subject offering (DRAFT -> FINALIZED)
--   student_attendance  -> one mark per enrolled student per session
--
-- Only FINALIZED sessions count towards a student's attendance percentage.
-- Students without a row in a session are "not marked"; a session cannot be
-- finalized while any enrolled student is unmarked.
-- =============================================

CREATE TABLE attendance_sessions (
    id                   BIGSERIAL     PRIMARY KEY,
    subject_offering_id  BIGINT        NOT NULL REFERENCES subject_offerings(id),
    attendance_date      DATE          NOT NULL,
    timetable_slot_id    BIGINT        REFERENCES timetable_slots(id),
    period_number        INTEGER,
    topic                VARCHAR(255),
    status               VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    created_by           BIGINT        NOT NULL REFERENCES users(id),
    created_at           TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP     NOT NULL DEFAULT NOW(),
    finalized_at         TIMESTAMP,

    CONSTRAINT ck_attendance_sessions_period CHECK (period_number IS NULL OR period_number > 0),
    CONSTRAINT ck_attendance_sessions_status CHECK (status IN ('DRAFT', 'FINALIZED')),
    CONSTRAINT ck_attendance_sessions_finalized_at CHECK ((status = 'FINALIZED') = (finalized_at IS NOT NULL))
);

-- One session per offering per date per period (sessions without a period are not restricted).
CREATE UNIQUE INDEX uq_attendance_sessions_offering_date_period
    ON attendance_sessions (subject_offering_id, attendance_date, period_number)
    WHERE period_number IS NOT NULL;

CREATE INDEX idx_attendance_sessions_offering ON attendance_sessions(subject_offering_id, attendance_date);
CREATE INDEX idx_attendance_sessions_status   ON attendance_sessions(status);

CREATE TABLE student_attendance (
    id                     BIGSERIAL     PRIMARY KEY,
    attendance_session_id  BIGINT        NOT NULL REFERENCES attendance_sessions(id) ON DELETE CASCADE,
    student_id             BIGINT        NOT NULL REFERENCES students(id),
    attendance_status      VARCHAR(20)   NOT NULL,
    remarks                VARCHAR(255),
    created_at             TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMP     NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_student_attendance_session_student UNIQUE (attendance_session_id, student_id),
    CONSTRAINT ck_student_attendance_status CHECK (attendance_status IN ('PRESENT', 'ABSENT', 'ON_DUTY', 'EXCUSED'))
);

CREATE INDEX idx_student_attendance_student ON student_attendance(student_id);

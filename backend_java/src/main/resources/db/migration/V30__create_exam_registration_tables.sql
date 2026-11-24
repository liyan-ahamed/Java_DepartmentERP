-- =============================================
-- V30__create_exam_registration_tables.sql
-- Exam periods and student exam registration.
--
--   exam_periods                -> DRAFT -> OPEN -> CLOSED, with a registration window
--   exam_registrations          -> one per student per period: DRAFT -> SUBMITTED -> APPROVED | REJECTED
--                                  (a REJECTED registration can be edited and submitted again)
--   exam_registration_subjects  -> the subject offerings registered for; the student must be
--                                  enrolled in each (checked in the service)
-- =============================================

CREATE TABLE exam_periods (
    id                  BIGSERIAL     PRIMARY KEY,
    name                VARCHAR(150)  NOT NULL,
    academic_year       VARCHAR(9)    NOT NULL,
    semester            INTEGER       NOT NULL,
    exam_type           VARCHAR(20)   NOT NULL,
    registration_start  DATE          NOT NULL,
    registration_end    DATE          NOT NULL,
    status              VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    created_by          BIGINT        REFERENCES users(id),
    created_at          TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP     NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_exam_periods_name_not_blank CHECK (BTRIM(name) <> ''),
    CONSTRAINT ck_exam_periods_academic_year CHECK (academic_year ~ '^[0-9]{4}-[0-9]{4}$'),
    CONSTRAINT ck_exam_periods_semester CHECK (semester BETWEEN 1 AND 8),
    CONSTRAINT ck_exam_periods_type CHECK (exam_type IN ('INTERNAL', 'END_SEMESTER', 'SUPPLEMENTARY')),
    CONSTRAINT ck_exam_periods_window CHECK (registration_end >= registration_start),
    CONSTRAINT ck_exam_periods_status CHECK (status IN ('DRAFT', 'OPEN', 'CLOSED'))
);

CREATE INDEX idx_exam_periods_term   ON exam_periods(academic_year, semester);
CREATE INDEX idx_exam_periods_status ON exam_periods(status);

CREATE TABLE exam_registrations (
    id              BIGSERIAL      PRIMARY KEY,
    exam_period_id  BIGINT         NOT NULL REFERENCES exam_periods(id),
    student_id      BIGINT         NOT NULL REFERENCES students(id),
    status          VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',
    submitted_at    TIMESTAMP,
    reviewed_by     BIGINT         REFERENCES users(id),
    reviewed_at     TIMESTAMP,
    review_note     VARCHAR(500),
    version         BIGINT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP      NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_exam_registrations_period_student UNIQUE (exam_period_id, student_id),
    CONSTRAINT ck_exam_registrations_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_exam_registrations_submitted CHECK (status = 'DRAFT' OR submitted_at IS NOT NULL),
    CONSTRAINT ck_exam_registrations_decision CHECK (
        status NOT IN ('APPROVED', 'REJECTED') OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)),
    CONSTRAINT ck_exam_registrations_rejection_note CHECK (status <> 'REJECTED' OR review_note IS NOT NULL)
);

CREATE INDEX idx_exam_registrations_student ON exam_registrations(student_id);
CREATE INDEX idx_exam_registrations_status  ON exam_registrations(exam_period_id, status);

CREATE TABLE exam_registration_subjects (
    id                    BIGSERIAL  PRIMARY KEY,
    exam_registration_id  BIGINT     NOT NULL REFERENCES exam_registrations(id) ON DELETE CASCADE,
    subject_offering_id   BIGINT     NOT NULL REFERENCES subject_offerings(id),
    created_at            TIMESTAMP  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_exam_registration_subjects UNIQUE (exam_registration_id, subject_offering_id)
);

CREATE INDEX idx_exam_registration_subjects_offering ON exam_registration_subjects(subject_offering_id);

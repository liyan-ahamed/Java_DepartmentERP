-- =============================================
-- V32__create_feedback_tables.sql
-- Academic feedback forms (HOD creates; targeted students answer once).
--
--   feedback_forms        -> DRAFT -> OPEN -> CLOSED; target = department / batch / section / subject offering
--   feedback_questions    -> RATING (1-5) or TEXT
--   feedback_submissions  -> WHO has submitted (duplicate prevention + "submitted" state). No answers here.
--   feedback_responses    -> the answers set. For anonymous forms student_id is NULL and there is no
--                            timestamp; ids are random UUIDs, so a response cannot be joined or ordered
--                            back to a submission.
--   feedback_answers      -> one answer per question per response (UUID ids for the same reason)
-- =============================================

CREATE TABLE feedback_forms (
    id                          BIGSERIAL      PRIMARY KEY,
    title                       VARCHAR(200)   NOT NULL,
    description                 VARCHAR(1000),
    target_type                 VARCHAR(20)    NOT NULL,
    target_batch_id             BIGINT         REFERENCES batches(id),
    target_section_id           BIGINT         REFERENCES sections(id),
    target_subject_offering_id  BIGINT         REFERENCES subject_offerings(id),
    anonymous                   BOOLEAN        NOT NULL DEFAULT TRUE,
    status                      VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',
    created_by                  BIGINT         NOT NULL REFERENCES users(id),
    opened_at                   TIMESTAMP,
    closed_at                   TIMESTAMP,
    created_at                  TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMP      NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_feedback_forms_title_not_blank CHECK (BTRIM(title) <> ''),
    CONSTRAINT ck_feedback_forms_status CHECK (status IN ('DRAFT', 'OPEN', 'CLOSED')),
    CONSTRAINT ck_feedback_forms_target CHECK (
        (target_type = 'DEPARTMENT' AND target_batch_id IS NULL AND target_section_id IS NULL AND target_subject_offering_id IS NULL)
        OR (target_type = 'BATCH' AND target_batch_id IS NOT NULL AND target_section_id IS NULL AND target_subject_offering_id IS NULL)
        OR (target_type = 'SECTION' AND target_section_id IS NOT NULL AND target_batch_id IS NULL AND target_subject_offering_id IS NULL)
        OR (target_type = 'SUBJECT_OFFERING' AND target_subject_offering_id IS NOT NULL AND target_batch_id IS NULL AND target_section_id IS NULL)),
    CONSTRAINT ck_feedback_forms_opened CHECK (status = 'DRAFT' OR opened_at IS NOT NULL),
    CONSTRAINT ck_feedback_forms_closed CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL))
);

CREATE INDEX idx_feedback_forms_status ON feedback_forms(status);

CREATE TABLE feedback_questions (
    id                BIGSERIAL     PRIMARY KEY,
    feedback_form_id  BIGINT        NOT NULL REFERENCES feedback_forms(id) ON DELETE CASCADE,
    position          INTEGER       NOT NULL,
    question_text     VARCHAR(500)  NOT NULL,
    question_type     VARCHAR(10)   NOT NULL,
    required          BOOLEAN       NOT NULL DEFAULT TRUE,

    CONSTRAINT uq_feedback_questions_position UNIQUE (feedback_form_id, position),
    CONSTRAINT ck_feedback_questions_position CHECK (position > 0),
    CONSTRAINT ck_feedback_questions_text_not_blank CHECK (BTRIM(question_text) <> ''),
    CONSTRAINT ck_feedback_questions_type CHECK (question_type IN ('RATING', 'TEXT'))
);

CREATE TABLE feedback_submissions (
    id                BIGSERIAL  PRIMARY KEY,
    feedback_form_id  BIGINT     NOT NULL REFERENCES feedback_forms(id),
    student_id        BIGINT     NOT NULL REFERENCES students(id),
    submitted_at      TIMESTAMP  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_feedback_submissions_form_student UNIQUE (feedback_form_id, student_id)
);

CREATE INDEX idx_feedback_submissions_student ON feedback_submissions(student_id);

CREATE TABLE feedback_responses (
    id                UUID       PRIMARY KEY,
    feedback_form_id  BIGINT     NOT NULL REFERENCES feedback_forms(id),
    -- Identified forms only. Always NULL for anonymous forms (enforced in the service).
    student_id        BIGINT     REFERENCES students(id),
    -- Identified forms only.
    submitted_at      TIMESTAMP,

    CONSTRAINT ck_feedback_responses_identity CHECK ((student_id IS NULL) = (submitted_at IS NULL))
);

CREATE INDEX idx_feedback_responses_form ON feedback_responses(feedback_form_id);

CREATE TABLE feedback_answers (
    id                    UUID           PRIMARY KEY,
    feedback_response_id  UUID           NOT NULL REFERENCES feedback_responses(id) ON DELETE CASCADE,
    feedback_question_id  BIGINT         NOT NULL REFERENCES feedback_questions(id),
    rating                SMALLINT,
    text_answer           VARCHAR(2000),

    CONSTRAINT uq_feedback_answers_response_question UNIQUE (feedback_response_id, feedback_question_id),
    CONSTRAINT ck_feedback_answers_rating CHECK (rating IS NULL OR rating BETWEEN 1 AND 5),
    CONSTRAINT ck_feedback_answers_one_value CHECK ((rating IS NULL) <> (text_answer IS NULL))
);

CREATE INDEX idx_feedback_answers_question ON feedback_answers(feedback_question_id);

-- =============================================
-- V31__create_exam_results.sql
-- Formal examination results (separate from the Gradebook, which holds internal /
-- continuous assessment marks). One row per student per subject offering per exam period.
--
-- Staff enter results while unpublished; HOD publishes; students see published rows only.
-- Published rows cannot be changed. No GPA/CGPA is stored or calculated.
-- =============================================

CREATE TABLE exam_results (
    id                   BIGSERIAL     PRIMARY KEY,
    student_id           BIGINT        NOT NULL REFERENCES students(id),
    subject_offering_id  BIGINT        NOT NULL REFERENCES subject_offerings(id),
    exam_period_id       BIGINT        NOT NULL REFERENCES exam_periods(id),
    marks_obtained       NUMERIC(6,2),
    max_marks            NUMERIC(6,2)  NOT NULL,
    grade                VARCHAR(5),
    result_status        VARCHAR(20)   NOT NULL,
    remarks              VARCHAR(255),
    published            BOOLEAN       NOT NULL DEFAULT FALSE,
    published_at         TIMESTAMP,
    published_by         BIGINT        REFERENCES users(id),
    entered_by           BIGINT        NOT NULL REFERENCES users(id),
    created_at           TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP     NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_exam_results_student_offering_period UNIQUE (student_id, subject_offering_id, exam_period_id),
    CONSTRAINT ck_exam_results_status CHECK (result_status IN ('PASS', 'FAIL', 'ABSENT', 'WITHHELD')),
    CONSTRAINT ck_exam_results_max_marks CHECK (max_marks > 0),
    CONSTRAINT ck_exam_results_marks CHECK (marks_obtained IS NULL OR (marks_obtained >= 0 AND marks_obtained <= max_marks)),
    -- PASS / FAIL need marks; ABSENT has neither marks nor grade; WITHHELD may keep marks.
    CONSTRAINT ck_exam_results_marks_status CHECK (
        (result_status IN ('PASS', 'FAIL') AND marks_obtained IS NOT NULL)
        OR (result_status = 'ABSENT' AND marks_obtained IS NULL AND grade IS NULL)
        OR result_status = 'WITHHELD'),
    CONSTRAINT ck_exam_results_published CHECK (published = (published_at IS NOT NULL))
);

CREATE INDEX idx_exam_results_offering_period ON exam_results(subject_offering_id, exam_period_id);
CREATE INDEX idx_exam_results_student         ON exam_results(student_id, published);

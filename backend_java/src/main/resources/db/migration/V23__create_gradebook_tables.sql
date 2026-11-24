-- =============================================
-- V23__create_gradebook_tables.sql
-- Gradebook + Internal Assessment foundation.
--
--   subjects                -> what is taught (THEORY / THEORY_CUM_PRACTICAL)
--   subject_offerings       -> subject + batch + section + staff + academic year + semester
--   assessments             -> offline assessments of an offering (DRAFT -> FINALIZED)
--   student_grades          -> raw marks per student per assessment (the only place raw marks live)
--   student_internal_marks  -> future calculated internal totals per student per offering.
--                              No formulas exist yet, so totals stay NULL and the status stays
--                              PENDING_CALCULATION_CONFIGURATION.
-- =============================================

CREATE TABLE subjects (
    id            BIGSERIAL    PRIMARY KEY,
    code          VARCHAR(20)  NOT NULL,
    name          VARCHAR(150) NOT NULL,
    subject_type  VARCHAR(30)  NOT NULL,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_subjects_code UNIQUE (code),
    CONSTRAINT ck_subjects_code_not_blank CHECK (BTRIM(code) <> ''),
    CONSTRAINT ck_subjects_name_not_blank CHECK (BTRIM(name) <> ''),
    CONSTRAINT ck_subjects_type CHECK (subject_type IN ('THEORY', 'THEORY_CUM_PRACTICAL'))
);

CREATE TABLE subject_offerings (
    id             BIGSERIAL   PRIMARY KEY,
    subject_id     BIGINT      NOT NULL REFERENCES subjects(id),
    batch_id       BIGINT      NOT NULL REFERENCES batches(id),
    section_id     BIGINT      NOT NULL REFERENCES sections(id),
    staff_id       BIGINT      NOT NULL REFERENCES users(id),
    academic_year  VARCHAR(9)  NOT NULL,
    semester       INTEGER     NOT NULL,
    is_active      BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP   NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_subject_offerings_academic_year CHECK (academic_year ~ '^[0-9]{4}-[0-9]{4}$'),
    CONSTRAINT ck_subject_offerings_semester CHECK (semester BETWEEN 1 AND 8)
);

-- Only one active offering of a subject per section per term.
CREATE UNIQUE INDEX uq_subject_offerings_active
    ON subject_offerings (subject_id, section_id, academic_year, semester)
    WHERE is_active;

CREATE INDEX idx_subject_offerings_staff   ON subject_offerings(staff_id);
CREATE INDEX idx_subject_offerings_section ON subject_offerings(section_id);
CREATE INDEX idx_subject_offerings_subject ON subject_offerings(subject_id);
CREATE INDEX idx_subject_offerings_term    ON subject_offerings(academic_year, semester);

CREATE TABLE assessments (
    id                   BIGSERIAL     PRIMARY KEY,
    subject_offering_id  BIGINT        NOT NULL REFERENCES subject_offerings(id),
    title                VARCHAR(150)  NOT NULL,
    assessment_type      VARCHAR(30)   NOT NULL,
    max_marks            NUMERIC(6,2)  NOT NULL,
    pass_marks           NUMERIC(6,2),
    weightage            NUMERIC(5,2),
    assessment_date      DATE          NOT NULL,
    status               VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    created_by           BIGINT        NOT NULL REFERENCES users(id),
    created_at           TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP     NOT NULL DEFAULT NOW(),
    finalized_at         TIMESTAMP,

    CONSTRAINT ck_assessments_title_not_blank CHECK (BTRIM(title) <> ''),
    CONSTRAINT ck_assessments_type CHECK (assessment_type IN (
        'CAT_1', 'CAT_2', 'ASSIGNMENT', 'QUIZ', 'LAB', 'PRACTICAL', 'RECORD',
        'GROUP_PRESENTATION', 'SEMINAR', 'PROJECT', 'OTHER')),
    CONSTRAINT ck_assessments_max_marks CHECK (max_marks > 0),
    CONSTRAINT ck_assessments_pass_marks CHECK (pass_marks IS NULL OR (pass_marks >= 0 AND pass_marks <= max_marks)),
    CONSTRAINT ck_assessments_weightage CHECK (weightage IS NULL OR (weightage > 0 AND weightage <= 100)),
    CONSTRAINT ck_assessments_status CHECK (status IN ('DRAFT', 'FINALIZED')),
    CONSTRAINT ck_assessments_finalized_at CHECK ((status = 'FINALIZED') = (finalized_at IS NOT NULL))
);

CREATE INDEX idx_assessments_offering ON assessments(subject_offering_id);
CREATE INDEX idx_assessments_status   ON assessments(status);

CREATE TABLE student_grades (
    id             BIGSERIAL     PRIMARY KEY,
    assessment_id  BIGINT        NOT NULL REFERENCES assessments(id) ON DELETE CASCADE,
    student_id     BIGINT        NOT NULL REFERENCES students(id),
    marks          NUMERIC(6,2),
    grade_status   VARCHAR(20)   NOT NULL,
    remarks        VARCHAR(255),
    created_at     TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP     NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_student_grades_assessment_student UNIQUE (assessment_id, student_id),
    CONSTRAINT ck_student_grades_status CHECK (grade_status IN ('GRADED', 'ABSENT', 'EXEMPTED')),
    CONSTRAINT ck_student_grades_marks CHECK (marks IS NULL OR marks >= 0),
    -- GRADED needs marks; ABSENT and EXEMPTED must not have marks.
    CONSTRAINT ck_student_grades_marks_status CHECK ((grade_status = 'GRADED') = (marks IS NOT NULL))
);

CREATE INDEX idx_student_grades_student ON student_grades(student_id);

CREATE TABLE student_internal_marks (
    id                         BIGSERIAL     PRIMARY KEY,
    subject_offering_id        BIGINT        NOT NULL REFERENCES subject_offerings(id),
    student_id                 BIGINT        NOT NULL REFERENCES students(id),
    -- Calculated results only. Raw CAT / assignment / practical / record marks stay in student_grades.
    theory_component_total     NUMERIC(6,2),
    practical_component_total  NUMERIC(6,2),
    total_internal_marks       NUMERIC(6,2),
    calculation_status         VARCHAR(40)   NOT NULL DEFAULT 'PENDING_CALCULATION_CONFIGURATION',
    calculation_version        VARCHAR(30),
    calculated_at              TIMESTAMP,
    created_at                 TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at                 TIMESTAMP     NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_student_internal_marks_offering_student UNIQUE (subject_offering_id, student_id),
    CONSTRAINT ck_student_internal_marks_status CHECK (
        calculation_status IN ('PENDING_CALCULATION_CONFIGURATION', 'CALCULATED')),
    CONSTRAINT ck_student_internal_marks_non_negative CHECK (
        (theory_component_total IS NULL OR theory_component_total >= 0)
        AND (practical_component_total IS NULL OR practical_component_total >= 0)
        AND (total_internal_marks IS NULL OR total_internal_marks >= 0)),
    -- A pending row never carries totals; a calculated row records when it was calculated.
    CONSTRAINT ck_student_internal_marks_pending_has_no_totals CHECK (
        calculation_status <> 'PENDING_CALCULATION_CONFIGURATION'
        OR (theory_component_total IS NULL AND practical_component_total IS NULL
            AND total_internal_marks IS NULL AND calculated_at IS NULL)),
    CONSTRAINT ck_student_internal_marks_calculated_at CHECK (
        calculation_status <> 'CALCULATED' OR calculated_at IS NOT NULL)
);

CREATE INDEX idx_student_internal_marks_student ON student_internal_marks(student_id);

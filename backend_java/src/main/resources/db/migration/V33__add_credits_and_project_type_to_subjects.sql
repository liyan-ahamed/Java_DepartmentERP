-- =============================================
-- V33__add_credits_and_project_type_to_subjects.sql
-- Curriculum support for subjects (additive):
--   * credits: whole-number credits of a subject; zero-credit subjects are allowed.
--     NULL = not recorded (existing subjects keep NULL until the HOD sets a value).
--   * subject_type may now be NULL = "not yet classified". New curriculum subjects
--     without an authoritative classification are stored unclassified instead of guessed.
--   * new subject type PROJECT_BASED (course codes ending in "R").
-- =============================================

ALTER TABLE subjects ADD COLUMN credits INTEGER;
ALTER TABLE subjects ADD CONSTRAINT ck_subjects_credits CHECK (credits IS NULL OR credits >= 0);

ALTER TABLE subjects ALTER COLUMN subject_type DROP NOT NULL;
ALTER TABLE subjects DROP CONSTRAINT ck_subjects_type;
ALTER TABLE subjects ADD CONSTRAINT ck_subjects_type
    CHECK (subject_type IS NULL OR subject_type IN ('THEORY', 'THEORY_CUM_PRACTICAL', 'PROJECT_BASED'));

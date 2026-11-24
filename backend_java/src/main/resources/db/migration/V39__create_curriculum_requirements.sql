-- =============================================
-- V39__create_curriculum_requirements.sql
-- Curriculum requirements per cohort (batch) + semester, configured by the HOD.
--
--   curriculum_requirements     one row per batch + semester (absent = requirements unknown)
--   curriculum_elective_groups  "choose required_selections of these options"
--   curriculum_subjects         one row per subject of a curriculum:
--                                 elective_group_id NULL = compulsory, else an option of that group
--
-- A subject can appear only once per curriculum (compulsory OR one elective group), enforced by
-- uq_curriculum_subjects_subject. The composite FK keeps an option inside a group of the same curriculum.
-- Nothing is seeded: existing subjects are not classified without authoritative information.
-- =============================================

CREATE TABLE curriculum_requirements (
    id          BIGSERIAL  PRIMARY KEY,
    batch_id    BIGINT     NOT NULL REFERENCES batches(id),
    semester    INTEGER    NOT NULL,
    version     BIGINT     NOT NULL DEFAULT 0,
    updated_by  BIGINT     REFERENCES users(id) ON DELETE SET NULL,
    created_at  TIMESTAMP  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_curriculum_requirements_batch_semester UNIQUE (batch_id, semester),
    CONSTRAINT ck_curriculum_requirements_semester CHECK (semester BETWEEN 1 AND 8)
);

CREATE TABLE curriculum_elective_groups (
    id                   BIGSERIAL     PRIMARY KEY,
    curriculum_id        BIGINT        NOT NULL REFERENCES curriculum_requirements(id) ON DELETE CASCADE,
    name                 VARCHAR(100)  NOT NULL,
    required_selections  INTEGER       NOT NULL,
    display_order        INTEGER       NOT NULL,
    created_at           TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP     NOT NULL DEFAULT NOW(),

    -- Target of the composite FK from curriculum_subjects.
    CONSTRAINT uq_curriculum_elective_groups_id_curriculum UNIQUE (id, curriculum_id),
    -- Deferred so a whole-curriculum save may rename / reorder groups in any statement order.
    CONSTRAINT uq_curriculum_elective_groups_name UNIQUE (curriculum_id, name) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_curriculum_elective_groups_name_not_blank CHECK (BTRIM(name) <> ''),
    CONSTRAINT ck_curriculum_elective_groups_required CHECK (required_selections >= 1),
    CONSTRAINT ck_curriculum_elective_groups_order CHECK (display_order >= 1)
);

CREATE TABLE curriculum_subjects (
    id                 BIGSERIAL  PRIMARY KEY,
    curriculum_id      BIGINT     NOT NULL REFERENCES curriculum_requirements(id) ON DELETE CASCADE,
    subject_id         BIGINT     NOT NULL REFERENCES subjects(id),
    elective_group_id  BIGINT,
    display_order      INTEGER    NOT NULL,
    created_at         TIMESTAMP  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_curriculum_subjects_subject UNIQUE (curriculum_id, subject_id),
    CONSTRAINT fk_curriculum_subjects_group FOREIGN KEY (elective_group_id, curriculum_id)
        REFERENCES curriculum_elective_groups (id, curriculum_id) ON DELETE CASCADE,
    CONSTRAINT ck_curriculum_subjects_order CHECK (display_order >= 1)
);

CREATE INDEX idx_curriculum_elective_groups_curriculum ON curriculum_elective_groups(curriculum_id);
CREATE INDEX idx_curriculum_subjects_group             ON curriculum_subjects(elective_group_id);
CREATE INDEX idx_curriculum_subjects_subject           ON curriculum_subjects(subject_id);

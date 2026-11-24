-- =============================================
-- V24__create_portfolio_entries.sql
-- Student Achievement & Activity Portfolio.
--
-- One row per achievement/activity of a student (hackathon, certification, internship, ...).
-- Workflow (enforced in the service layer): DRAFT -> SUBMITTED -> VERIFIED | REJECTED,
-- and REJECTED -> (edit) -> SUBMITTED again.
--
-- reviewed_by / reviewed_at / review_note hold the latest review decision (verify or reject).
-- They are kept when a rejected entry is resubmitted, so the reviewer sees why it was rejected.
-- Drafts may be incomplete; the service checks the full set of required fields on submission.
-- =============================================

CREATE TABLE portfolio_entries (
    id                  BIGSERIAL     PRIMARY KEY,
    student_id          BIGINT        NOT NULL REFERENCES students(id),
    activity_type       VARCHAR(30)   NOT NULL,
    title               VARCHAR(200)  NOT NULL,
    organization        VARCHAR(200),
    description         VARCHAR(2000),
    level               VARCHAR(20),
    role_or_position    VARCHAR(100),
    achievement_result  VARCHAR(150),
    start_date          DATE,
    end_date            DATE,
    evidence_url        VARCHAR(500),
    status              VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    submitted_at        TIMESTAMP,
    reviewed_by         BIGINT        REFERENCES users(id),
    reviewed_at         TIMESTAMP,
    review_note         VARCHAR(1000),
    version             BIGINT        NOT NULL DEFAULT 0,
    created_at          TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP     NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_portfolio_entries_title_not_blank CHECK (BTRIM(title) <> ''),
    CONSTRAINT ck_portfolio_entries_activity_type CHECK (activity_type IN (
        'HACKATHON', 'CERTIFICATION', 'COMPETITION', 'WORKSHOP', 'INTERNSHIP',
        'RESEARCH_PUBLICATION', 'CONFERENCE', 'TECHNICAL_EVENT', 'PROJECT_SHOWCASE',
        'CLUB_ACTIVITY', 'OTHER')),
    CONSTRAINT ck_portfolio_entries_level CHECK (level IS NULL OR level IN (
        'COLLEGE', 'INTER_COLLEGE', 'STATE', 'NATIONAL', 'INTERNATIONAL', 'ONLINE', 'OTHER')),
    CONSTRAINT ck_portfolio_entries_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'VERIFIED', 'REJECTED')),
    CONSTRAINT ck_portfolio_entries_dates CHECK (end_date IS NULL OR start_date IS NULL OR end_date >= start_date),
    -- Only never-submitted drafts lack a submission time.
    CONSTRAINT ck_portfolio_entries_submitted_at CHECK ((status = 'DRAFT') = (submitted_at IS NULL)),
    -- A decision always records who decided and when; a rejection always has a reason.
    CONSTRAINT ck_portfolio_entries_decision CHECK (
        status NOT IN ('VERIFIED', 'REJECTED') OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)),
    CONSTRAINT ck_portfolio_entries_rejection_note CHECK (status <> 'REJECTED' OR review_note IS NOT NULL),
    CONSTRAINT ck_portfolio_entries_draft_unreviewed CHECK (
        status <> 'DRAFT' OR (reviewed_by IS NULL AND reviewed_at IS NULL AND review_note IS NULL))
);

CREATE INDEX idx_portfolio_entries_student       ON portfolio_entries(student_id);
CREATE INDEX idx_portfolio_entries_status        ON portfolio_entries(status);
CREATE INDEX idx_portfolio_entries_activity_type ON portfolio_entries(activity_type);
CREATE INDEX idx_portfolio_entries_level         ON portfolio_entries(level);
CREATE INDEX idx_portfolio_entries_submitted_at  ON portfolio_entries(submitted_at);
CREATE INDEX idx_portfolio_entries_reviewed_at   ON portfolio_entries(reviewed_at);
CREATE INDEX idx_portfolio_entries_reviewed_by   ON portfolio_entries(reviewed_by);

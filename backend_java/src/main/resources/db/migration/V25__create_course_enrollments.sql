-- =============================================
-- V25__create_course_enrollments.sql
-- Course Enrollment: which students take which subject offering.
--
-- One row per student per offering. Status changes in place
-- (ENROLLED -> DROPPED -> ENROLLED again, ENROLLED -> COMPLETED), so a student can
-- never hold two rows (active or not) for the same offering.
-- The enrolled students of an offering are the class list used by attendance,
-- exam registration and results.
-- =============================================

CREATE TABLE course_enrollments (
    id                   BIGSERIAL    PRIMARY KEY,
    student_id           BIGINT       NOT NULL REFERENCES students(id),
    subject_offering_id  BIGINT       NOT NULL REFERENCES subject_offerings(id),
    enrollment_status    VARCHAR(20)  NOT NULL DEFAULT 'ENROLLED',
    enrolled_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    enrolled_by          BIGINT       REFERENCES users(id),
    created_at           TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_course_enrollments_student_offering UNIQUE (student_id, subject_offering_id),
    CONSTRAINT ck_course_enrollments_status CHECK (enrollment_status IN ('ENROLLED', 'DROPPED', 'COMPLETED'))
);

CREATE INDEX idx_course_enrollments_offering_status ON course_enrollments(subject_offering_id, enrollment_status);
CREATE INDEX idx_course_enrollments_student         ON course_enrollments(student_id);

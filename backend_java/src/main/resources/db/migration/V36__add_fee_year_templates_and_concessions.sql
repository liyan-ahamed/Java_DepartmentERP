-- =============================================
-- V36__add_fee_year_templates_and_concessions.sql
-- Year-based fee templates and per-charge concessions (additive).
--
--   fee_structures.student_year: 1-4 = a charge of the "Year N" template, applied to whichever cohort is in
--     that year (not tied to a batch). Templates have no academic year; term charges keep theirs.
--   student_fee_accounts.concession_amount: 0 <= concession <= amount (actual).
--     payable = amount - concession; paid can never exceed payable; status follows payable.
-- Existing rows: student_year NULL, concession 0, so their meaning does not change.
-- =============================================

ALTER TABLE fee_structures ADD COLUMN student_year SMALLINT;
ALTER TABLE fee_structures ADD CONSTRAINT ck_fee_structures_student_year
    CHECK (student_year IS NULL OR student_year BETWEEN 1 AND 4);
ALTER TABLE fee_structures ALTER COLUMN academic_year DROP NOT NULL;
ALTER TABLE fee_structures ADD CONSTRAINT ck_fee_structures_year_or_term
    CHECK (academic_year IS NOT NULL OR student_year IS NOT NULL);

-- One active charge per particular in each year template.
CREATE UNIQUE INDEX uq_fee_structures_year_template
    ON fee_structures (student_year, LOWER(name))
    WHERE student_year IS NOT NULL AND academic_year IS NULL AND is_active;

ALTER TABLE student_fee_accounts ADD COLUMN concession_amount NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE student_fee_accounts ADD CONSTRAINT ck_student_fee_accounts_concession
    CHECK (concession_amount >= 0 AND concession_amount <= amount);

ALTER TABLE student_fee_accounts DROP CONSTRAINT ck_student_fee_accounts_paid;
ALTER TABLE student_fee_accounts ADD CONSTRAINT ck_student_fee_accounts_paid
    CHECK (paid_amount >= 0 AND paid_amount <= amount - concession_amount);

ALTER TABLE student_fee_accounts DROP CONSTRAINT ck_student_fee_accounts_status_paid;
ALTER TABLE student_fee_accounts ADD CONSTRAINT ck_student_fee_accounts_status_paid CHECK (
    (status = 'UNPAID' AND paid_amount = 0 AND amount - concession_amount > 0)
    OR (status = 'PARTIALLY_PAID' AND paid_amount > 0 AND paid_amount < amount - concession_amount)
    OR (status = 'PAID' AND paid_amount = amount - concession_amount)
    OR (status = 'WAIVED' AND paid_amount < amount - concession_amount));

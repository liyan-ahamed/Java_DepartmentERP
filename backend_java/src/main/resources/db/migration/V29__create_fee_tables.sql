-- =============================================
-- V29__create_fee_tables.sql
-- Fee recordkeeping (no payment gateway). All money is NUMERIC(12,2).
--
--   fee_structures        -> a charge definition (e.g. "Tuition fee 2026-2027 Sem 3")
--   student_fee_accounts  -> one charge assigned to one student (amount, paid, status)
--   fee_payments          -> recorded payments against a charge; each one is a receipt
--
-- Balance = amount - paid_amount (0 when WAIVED). Status is kept consistent with
-- the paid amount by CHECK constraints.
-- =============================================

CREATE TABLE fee_structures (
    id             BIGSERIAL      PRIMARY KEY,
    name           VARCHAR(150)   NOT NULL,
    fee_type       VARCHAR(20)    NOT NULL,
    academic_year  VARCHAR(9)     NOT NULL,
    semester       INTEGER,
    batch_id       BIGINT         REFERENCES batches(id),
    amount         NUMERIC(12,2)  NOT NULL,
    due_date       DATE,
    is_active      BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP      NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_fee_structures_name_not_blank CHECK (BTRIM(name) <> ''),
    CONSTRAINT ck_fee_structures_type CHECK (fee_type IN ('TUITION', 'EXAM', 'LAB', 'DEPARTMENT', 'OTHER')),
    CONSTRAINT ck_fee_structures_academic_year CHECK (academic_year ~ '^[0-9]{4}-[0-9]{4}$'),
    CONSTRAINT ck_fee_structures_semester CHECK (semester IS NULL OR semester BETWEEN 1 AND 8),
    CONSTRAINT ck_fee_structures_amount CHECK (amount > 0)
);

CREATE INDEX idx_fee_structures_batch ON fee_structures(batch_id);
CREATE INDEX idx_fee_structures_term  ON fee_structures(academic_year, semester);

CREATE TABLE student_fee_accounts (
    id                BIGSERIAL      PRIMARY KEY,
    student_id        BIGINT         NOT NULL REFERENCES students(id),
    fee_structure_id  BIGINT         NOT NULL REFERENCES fee_structures(id),
    amount            NUMERIC(12,2)  NOT NULL,
    paid_amount       NUMERIC(12,2)  NOT NULL DEFAULT 0,
    due_date          DATE,
    status            VARCHAR(20)    NOT NULL DEFAULT 'UNPAID',
    waiver_reason     VARCHAR(500),
    version           BIGINT         NOT NULL DEFAULT 0,
    created_at        TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP      NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_student_fee_accounts_student_structure UNIQUE (student_id, fee_structure_id),
    CONSTRAINT ck_student_fee_accounts_amount CHECK (amount > 0),
    CONSTRAINT ck_student_fee_accounts_paid CHECK (paid_amount >= 0 AND paid_amount <= amount),
    CONSTRAINT ck_student_fee_accounts_status CHECK (status IN ('UNPAID', 'PARTIALLY_PAID', 'PAID', 'WAIVED')),
    CONSTRAINT ck_student_fee_accounts_status_paid CHECK (
        (status = 'UNPAID' AND paid_amount = 0)
        OR (status = 'PARTIALLY_PAID' AND paid_amount > 0 AND paid_amount < amount)
        OR (status = 'PAID' AND paid_amount = amount)
        OR (status = 'WAIVED' AND paid_amount < amount)),
    CONSTRAINT ck_student_fee_accounts_waiver CHECK ((status = 'WAIVED') = (waiver_reason IS NOT NULL))
);

CREATE INDEX idx_student_fee_accounts_student ON student_fee_accounts(student_id);
CREATE INDEX idx_student_fee_accounts_status  ON student_fee_accounts(status);

-- Receipt numbers: RCPT-<year>-<6-digit sequence>, never reused.
CREATE SEQUENCE fee_receipt_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE fee_payments (
    id                      BIGSERIAL      PRIMARY KEY,
    student_fee_account_id  BIGINT         NOT NULL REFERENCES student_fee_accounts(id),
    receipt_number          VARCHAR(30)    NOT NULL,
    amount                  NUMERIC(12,2)  NOT NULL,
    payment_date            DATE           NOT NULL,
    payment_method          VARCHAR(20)    NOT NULL,
    reference               VARCHAR(100),
    remarks                 VARCHAR(255),
    recorded_by             BIGINT         REFERENCES users(id),
    created_at              TIMESTAMP      NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_fee_payments_receipt_number UNIQUE (receipt_number),
    CONSTRAINT ck_fee_payments_amount CHECK (amount > 0),
    CONSTRAINT ck_fee_payments_method CHECK (payment_method IN ('CASH', 'CARD', 'BANK_TRANSFER', 'UPI', 'OTHER'))
);

CREATE INDEX idx_fee_payments_account ON fee_payments(student_fee_account_id);
CREATE INDEX idx_fee_payments_date    ON fee_payments(payment_date);

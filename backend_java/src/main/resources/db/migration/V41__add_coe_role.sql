-- =============================================
-- V41__add_coe_role.sql
-- New application role COE (Controller of Examinations): enters END_SEMESTER and SUPPLEMENTARY
-- exam results. STAFF can no longer change those results (enforced in the service layer).
-- Additive only: no user gets the role here; the HOD assigns it through POST/PUT /users.
-- No permissions are granted (role checks use the role itself).
-- =============================================

-- users.role mirrors the single role (V38); the mirror is a PostgreSQL enum.
ALTER TYPE dept_role ADD VALUE IF NOT EXISTS 'COE';

INSERT INTO roles (name, description, created_at, updated_at)
VALUES ('ROLE_COE', 'Controller of Examinations', NOW(), NOW())
ON CONFLICT (name) DO NOTHING;

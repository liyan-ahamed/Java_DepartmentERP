-- =============================================
-- V38__enforce_single_role_per_user.sql
-- Every user has exactly ONE application role. user_roles stays the source of truth (one row per
-- user from now on) and users.role stays its mirror.
--
-- Normalization before the constraint (every change is reported with RAISE NOTICE):
--   * no user_roles row       -> restored from users.role
--   * several roles:
--       linked to a Student   -> ROLE_STUDENT (must already be one of its roles)
--       else has ROLE_HOD     -> ROLE_HOD
--       else has ROLE_STAFF   -> ROLE_STAFF
--   * ambiguous (linked to a Student but holding ROLE_HOD, or linked without ROLE_STUDENT) -> the
--     migration FAILS so a person decides; nothing is guessed.
-- =============================================

DO $$
DECLARE
    r    RECORD;
    keep TEXT;
BEGIN
    FOR r IN SELECT u.id, u.email, u.role::text AS mirror FROM users u
             WHERE NOT EXISTS (SELECT 1 FROM user_roles ur WHERE ur.user_id = u.id) LOOP
        IF r.mirror NOT IN ('HOD', 'STAFF', 'STUDENT') THEN
            RAISE EXCEPTION 'User % (%) has no role and an unknown users.role %', r.id, r.email, r.mirror;
        END IF;
        INSERT INTO user_roles (user_id, role_id) SELECT r.id, id FROM roles WHERE name = 'ROLE_' || r.mirror;
        RAISE NOTICE 'Single-role: user % (%) had no role row; restored ROLE_% from users.role', r.id, r.email, r.mirror;
    END LOOP;

    FOR r IN SELECT u.id, u.email, ARRAY_AGG(ro.name ORDER BY ro.name) AS names,
                    EXISTS (SELECT 1 FROM students s WHERE s.user_id = u.id) AS linked
             FROM users u
             JOIN user_roles ur ON ur.user_id = u.id
             JOIN roles ro ON ro.id = ur.role_id
             GROUP BY u.id, u.email
             HAVING COUNT(*) > 1 LOOP
        IF r.linked AND ('ROLE_HOD' = ANY (r.names) OR NOT 'ROLE_STUDENT' = ANY (r.names)) THEN
            RAISE EXCEPTION 'Single-role: user % (%) is ambiguous (roles %, linked to a student); fix it manually first',
                r.id, r.email, r.names;
        END IF;
        keep := CASE
                    WHEN r.linked THEN 'ROLE_STUDENT'
                    WHEN 'ROLE_HOD' = ANY (r.names) THEN 'ROLE_HOD'
                    WHEN 'ROLE_STAFF' = ANY (r.names) THEN 'ROLE_STAFF'
                END;
        IF keep IS NULL THEN
            RAISE EXCEPTION 'Single-role: user % (%) has roles % and no rule applies', r.id, r.email, r.names;
        END IF;
        DELETE FROM user_roles WHERE user_id = r.id AND role_id <> (SELECT id FROM roles WHERE name = keep);
        RAISE NOTICE 'Single-role: user % (%) normalized from % to %', r.id, r.email, r.names, keep;
    END LOOP;

    -- Keep the users.role mirror consistent with the single remaining role.
    UPDATE users u
       SET role = SUBSTRING(ro.name FROM 6)::dept_role
      FROM user_roles ur JOIN roles ro ON ro.id = ur.role_id
     WHERE ur.user_id = u.id AND u.role::text <> SUBSTRING(ro.name FROM 6);
END $$;

ALTER TABLE user_roles ADD CONSTRAINT uq_user_roles_user UNIQUE (user_id);

-- =============================================
-- V21__add_token_version_to_users.sql
-- token_version is embedded in every JWT. Incrementing it (logout,
-- deactivation) revokes all of that user's access and refresh tokens.
-- Tokens issued before this migration carry no version and are rejected,
-- so every user signs in once more after deployment.
-- =============================================

ALTER TABLE users ADD COLUMN IF NOT EXISTS token_version INTEGER NOT NULL DEFAULT 0;

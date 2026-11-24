-- =============================================
-- V20__add_leetcode_fields_to_students.sql
-- Stores each student's LeetCode profile and latest synced statistics
-- on the existing students table (no separate student records).
-- A NULL leetcode_url means "URL NOT PROVIDED".
-- =============================================

ALTER TABLE students
    ADD COLUMN leetcode_url            VARCHAR(255),
    ADD COLUMN leetcode_username       VARCHAR(100),
    ADD COLUMN leetcode_total_solved   INT,
    ADD COLUMN leetcode_easy_solved    INT,
    ADD COLUMN leetcode_medium_solved  INT,
    ADD COLUMN leetcode_hard_solved    INT,
    ADD COLUMN leetcode_last_synced_at TIMESTAMP;

-- The Second-Year roster supplies only names and LeetCode URLs; students
-- created from it have no date of birth on record.
ALTER TABLE students ALTER COLUMN dob DROP NOT NULL;

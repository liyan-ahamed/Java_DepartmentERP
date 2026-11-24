-- =============================================
-- V37__add_marks_entry_deadline_to_assessments.sql
-- Optional deadline (set by the HOD) by which the assigned staff member must finish entering marks.
-- Timezone-aware so a deadline means the same instant everywhere.
-- =============================================

ALTER TABLE assessments ADD COLUMN marks_entry_due_at TIMESTAMPTZ;
CREATE INDEX idx_assessments_marks_entry_due ON assessments(marks_entry_due_at) WHERE marks_entry_due_at IS NOT NULL;

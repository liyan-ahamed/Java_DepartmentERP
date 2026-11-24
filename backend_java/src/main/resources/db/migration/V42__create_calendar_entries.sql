-- Department-wide entries are separate from government reference holidays and
-- the legacy college calendar's draft/publication workflow.
CREATE TABLE calendar_entries (
    id BIGSERIAL PRIMARY KEY,
    event_date DATE NOT NULL,
    title VARCHAR(255) NOT NULL CHECK (LENGTH(TRIM(title)) > 0),
    is_holiday BOOLEAN NOT NULL DEFAULT FALSE,
    created_by BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_calendar_entries_date ON calendar_entries(event_date, id);

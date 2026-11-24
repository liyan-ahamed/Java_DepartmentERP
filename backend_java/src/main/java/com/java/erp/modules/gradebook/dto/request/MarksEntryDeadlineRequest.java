package com.java.erp.modules.gradebook.dto.request;

import java.time.OffsetDateTime;

/** marksEntryDueAt: ISO-8601 with offset, e.g. 2026-10-20T17:00:00+05:30; null clears the deadline. */
public record MarksEntryDeadlineRequest(OffsetDateTime marksEntryDueAt) {
}

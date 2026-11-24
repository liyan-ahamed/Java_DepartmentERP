package com.java.erp.modules.system.dto.response;

import com.java.erp.modules.system.entity.Batch;
import com.java.erp.modules.system.entity.Section;

/**
 * Sections have no active flag of their own; a section is active while its batch is.
 */
public record SectionResponse(
        Long id,
        String name,
        Long batchId,
        String batchName,
        Short currentYear,
        boolean active
) {
    public static SectionResponse from(Section s) {
        Batch b = s.getBatch();
        return new SectionResponse(s.getId(), s.getSectionName(), b.getId(), b.getBatchName(), b.getCurrentYear(),
                b.isActive());
    }
}

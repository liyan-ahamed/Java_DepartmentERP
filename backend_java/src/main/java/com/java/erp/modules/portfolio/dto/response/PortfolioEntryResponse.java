package com.java.erp.modules.portfolio.dto.response;

import com.java.erp.modules.auth.entity.User;
import com.java.erp.modules.portfolio.entity.PortfolioEntry;
import com.java.erp.modules.system.entity.Student;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * reviewedBy/reviewedAt/reviewNote describe the latest decision. On a SUBMITTED entry that was
 * rejected before, they still show that rejection until the entry is reviewed again.
 */
public record PortfolioEntryResponse(
        Long id,
        Long studentId,
        String studentName,
        String registerNumber,
        Long batchId,
        String batchName,
        Long sectionId,
        String sectionName,
        PortfolioEntry.ActivityType activityType,
        String title,
        String organization,
        String description,
        PortfolioEntry.Level level,
        String roleOrPosition,
        String achievementResult,
        LocalDate startDate,
        LocalDate endDate,
        String evidenceUrl,
        PortfolioEntry.Status status,
        LocalDateTime submittedAt,
        Long reviewedById,
        String reviewedByName,
        LocalDateTime reviewedAt,
        String reviewNote,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static PortfolioEntryResponse from(PortfolioEntry e) {
        Student s = e.getStudent();
        User reviewer = e.getReviewedBy();
        return new PortfolioEntryResponse(e.getId(), s.getId(), s.getName(), s.getRegisterNumber(),
                s.getBatch().getId(), s.getBatch().getBatchName(), s.getSection().getId(), s.getSection().getSectionName(),
                e.getActivityType(), e.getTitle(), e.getOrganization(), e.getDescription(), e.getLevel(),
                e.getRoleOrPosition(), e.getAchievementResult(), e.getStartDate(), e.getEndDate(), e.getEvidenceUrl(),
                e.getStatus(), e.getSubmittedAt(),
                reviewer == null ? null : reviewer.getId(), reviewer == null ? null : reviewer.getName(),
                e.getReviewedAt(), e.getReviewNote(), e.getCreatedAt(), e.getUpdatedAt());
    }
}

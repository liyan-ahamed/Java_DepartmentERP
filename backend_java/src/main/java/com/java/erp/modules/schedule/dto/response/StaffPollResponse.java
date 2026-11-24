package com.java.erp.modules.schedule.dto.response;

import java.time.LocalDateTime;

public record StaffPollResponse(Long studentId, String studentName, String registerNumber, String targetClass,
                                int selectedOptionIndex, String selectedOption, LocalDateTime respondedAt) {}

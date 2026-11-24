package com.java.erp.modules.schedule.dto.response;

import java.util.List;

public record StaffScheduleHistoryItem(StudentScheduleResponse schedule, List<String> targetClasses,
                                       List<String> targetStudents, long assignedCount, long respondedCount) {}

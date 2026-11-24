package com.java.erp.modules.leetcode.entity;

import com.java.erp.exception.BadRequestException;

/**
 * Academic year selector used by the LeetCode module.
 * Maps to Batch.currentYear (1-4) of the active batches.
 */
public enum AcademicYear {
    FIRST_YEAR(1, "First Year"),
    SECOND_YEAR(2, "Second Year"),
    THIRD_YEAR(3, "Third Year"),
    FOURTH_YEAR(4, "Fourth Year");

    private final short yearNumber;
    private final String label;

    AcademicYear(int yearNumber, String label) {
        this.yearNumber = (short) yearNumber;
        this.label = label;
    }

    public short getYearNumber() {
        return yearNumber;
    }

    public String getLabel() {
        return label;
    }

    public static AcademicYear parse(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Year is required");
        }
        try {
            return AcademicYear.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Year must be one of FIRST_YEAR, SECOND_YEAR, THIRD_YEAR, FOURTH_YEAR");
        }
    }
}

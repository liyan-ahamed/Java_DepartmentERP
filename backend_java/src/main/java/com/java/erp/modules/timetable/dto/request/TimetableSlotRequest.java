package com.java.erp.modules.timetable.dto.request;

import com.java.erp.modules.timetable.entity.TimetableSlot;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * A slot is one official teaching period (1-8, see GET /timetable/periods).
 * Send periodNumber; startTime/endTime ("HH:mm") are optional and, when sent, must equal that period's
 * times. Older clients may send only startTime/endTime: they must match one teaching period exactly.
 * slotType defaults to LECTURE.
 */
public record TimetableSlotRequest(
        @NotNull Long subjectOfferingId,
        @NotNull DayOfWeek dayOfWeek,
        Integer periodNumber,
        LocalTime startTime,
        LocalTime endTime,
        @Size(max = 50) String room,
        TimetableSlot.SlotType slotType
) {
}

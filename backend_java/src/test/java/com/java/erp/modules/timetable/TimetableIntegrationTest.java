package com.java.erp.modules.timetable;

import com.fasterxml.jackson.databind.JsonNode;
import com.java.erp.modules.academic.entity.Subject;
import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.support.ErpModulesTestBase;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TimetableIntegrationTest extends ErpModulesTestBase {

    private static Map<String, Object> slot(Long offeringId, String day, String start, String end) {
        Map<String, Object> m = new HashMap<>();
        m.put("subjectOfferingId", offeringId);
        m.put("dayOfWeek", day);
        m.put("startTime", start);
        m.put("endTime", end);
        m.put("room", "R-1");
        return m;
    }

    private long create(SubjectOffering o, String day, String start, String end) throws Exception {
        return data(postAs(hod, "/timetable", slot(o.getId(), day, start, end)).andExpect(status().isCreated())).get("id").asLong();
    }

    @Test
    void hodCreatesUpdatesAndDeletesSlots() throws Exception {
        SubjectOffering offering = theoryOffering();
        long id = create(offering, "MONDAY", "08:15", "09:05");

        putAs(hod, "/timetable/" + id, slot(offering.getId(), "TUESDAY", "10:10", "11:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dayOfWeek").value("TUESDAY"))
                .andExpect(jsonPath("$.data.slotType").value("LECTURE"));
        deleteAs(hod, "/timetable/" + id).andExpect(status().isOk());
        getAs(hod, "/timetable?subjectOfferingId=" + offering.getId()).andExpect(jsonPath("$.data.length()").value(0));
        getAs(hod, "/timetable?subjectOfferingId=" + offering.getId() + "&active=false").andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void overlappingSlotsOfTheSameSectionAreRejected() throws Exception {
        SubjectOffering a = theoryOffering();
        SubjectOffering b = offering(subject(Subject.SubjectType.THEORY), section, otherStaff);
        create(a, "MONDAY", "08:15", "09:05");
        postAs(hod, "/timetable", slot(b.getId(), "MONDAY", "08:15", "09:05")).andExpect(status().isConflict());
    }

    @Test
    void overlappingSlotsOfTheSameStaffMemberAreRejected() throws Exception {
        SubjectOffering a = theoryOffering();
        SubjectOffering b = offering(subject(Subject.SubjectType.THEORY), otherSection, staff);
        create(a, "WEDNESDAY", "11:00", "11:50");
        postAs(hod, "/timetable", slot(b.getId(), "WEDNESDAY", "11:00", "11:50")).andExpect(status().isConflict());
    }

    @Test
    void nonOverlappingAndTouchingSlotsAreAllowed() throws Exception {
        SubjectOffering a = theoryOffering();
        SubjectOffering b = offering(subject(Subject.SubjectType.THEORY), section, otherStaff);
        create(a, "MONDAY", "08:15", "09:05");
        create(b, "MONDAY", "09:05", "09:55");      // touches, same section
        create(b, "TUESDAY", "08:15", "09:05");     // other day
        long id = create(a, "FRIDAY", "08:15", "09:05");
        putAs(hod, "/timetable/" + id, slot(a.getId(), "FRIDAY", "09:05", "09:55")).andExpect(status().isOk()); // overlaps only itself
    }

    @Test
    void invalidTimesAndInactiveOfferingsAreRejected() throws Exception {
        SubjectOffering offering = theoryOffering();
        postAs(hod, "/timetable", slot(offering.getId(), "MONDAY", "10:00", "09:00")).andExpect(status().isBadRequest());
        Map<String, Object> badPeriod = slot(offering.getId(), "MONDAY", "08:15", "09:05");
        badPeriod.put("periodNumber", 0);
        postAs(hod, "/timetable", badPeriod).andExpect(status().isBadRequest());
        postAs(hod, "/timetable", slot(offering.getId(), "FUNDAY", "08:15", "09:05")).andExpect(status().isBadRequest());
        offering.setActive(false);
        offeringRepository.saveAndFlush(offering);
        postAs(hod, "/timetable", slot(offering.getId(), "MONDAY", "08:15", "09:05")).andExpect(status().isBadRequest());
    }

    @Test
    void staffSeeOwnTimetableAndCannotManage() throws Exception {
        SubjectOffering own = theoryOffering();
        SubjectOffering foreign = offering(subject(Subject.SubjectType.THEORY), otherSection, otherStaff);
        create(own, "MONDAY", "08:15", "09:05");
        create(foreign, "MONDAY", "08:15", "09:05");

        JsonNode mine = data(getAs(staff, "/timetable/me").andExpect(status().isOk()));
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).get("subjectOfferingId").asLong()).isEqualTo(own.getId());
        getAs(staff, "/timetable").andExpect(jsonPath("$.data.length()").value(1));
        postAs(staff, "/timetable", slot(own.getId(), "TUESDAY", "08:15", "09:05")).andExpect(status().isForbidden());
    }

    @Test
    void studentSeesSlotsOfEnrolledOfferingsOnly() throws Exception {
        SubjectOffering enrolled = theoryOffering();
        SubjectOffering notEnrolled = offering(subject(Subject.SubjectType.THEORY), section, otherStaff);
        create(enrolled, "MONDAY", "08:15", "09:05");
        create(notEnrolled, "MONDAY", "10:10", "11:00");
        StudentLogin me = studentLogin(section);
        enroll(enrolled, me.student());

        getAs(me.user(), "/timetable/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].subjectOfferingId").value(enrolled.getId()));
        getAs(me.user(), "/timetable").andExpect(status().isForbidden());
        postAs(me.user(), "/timetable", slot(enrolled.getId(), "TUESDAY", "08:15", "09:05")).andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mvc.perform(get("/timetable/me")).andExpect(status().isUnauthorized());
    }
}

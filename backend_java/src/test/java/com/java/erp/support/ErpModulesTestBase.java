package com.java.erp.support;

import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.auth.entity.User;
import com.java.erp.modules.enrollment.entity.CourseEnrollment;
import com.java.erp.modules.enrollment.repository.CourseEnrollmentRepository;
import com.java.erp.modules.holiday.entity.HolidayCalendar;
import com.java.erp.modules.holiday.repository.HolidayCalendarRepository;
import com.java.erp.modules.system.entity.Section;
import com.java.erp.modules.system.entity.Student;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Fixtures and HTTP helpers for the enrollment / timetable / attendance / ... module tests. */
public abstract class ErpModulesTestBase extends AcademicTestBase {

    @Autowired protected CourseEnrollmentRepository enrollmentRepository;
    @Autowired protected HolidayCalendarRepository holidayCalendarRepository;

    /** Only PUBLISHED college calendars affect timetables, attendance and dashboards. */
    protected void publishHolidayCalendar(int year) {
        HolidayCalendar calendar = holidayCalendarRepository.findByYear(year).orElseGet(() -> new HolidayCalendar(year));
        if (!calendar.isPublished()) {
            calendar.publish(null, LocalDateTime.now());
            holidayCalendarRepository.saveAndFlush(calendar);
        }
    }

    protected List<Student> students(Section target, int count) {
        List<Student> list = new ArrayList<>();
        for (int i = 0; i < count; i++) list.add(student(target));
        return list;
    }

    protected CourseEnrollment enroll(SubjectOffering offering, Student student) {
        return enrollmentRepository.saveAndFlush(new CourseEnrollment(student, offering, hod));
    }

    protected void enrollAll(SubjectOffering offering, List<Student> list) {
        list.forEach(s -> enroll(offering, s));
    }

    protected ResultActions getAs(User user, String path) throws Exception {
        return mvc.perform(get(path).header("Authorization", token(user)));
    }

    protected ResultActions postAs(User user, String path, Object body) throws Exception {
        var request = post(path).header("Authorization", token(user));
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json(body));
        return mvc.perform(request);
    }

    protected ResultActions putAs(User user, String path, Object body) throws Exception {
        return mvc.perform(put(path).header("Authorization", token(user))
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    protected ResultActions deleteAs(User user, String path) throws Exception {
        return mvc.perform(delete(path).header("Authorization", token(user)));
    }
}

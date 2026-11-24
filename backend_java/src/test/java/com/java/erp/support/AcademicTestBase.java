package com.java.erp.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.java.erp.modules.academic.entity.Subject;
import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.academic.repository.SubjectOfferingRepository;
import com.java.erp.modules.academic.repository.SubjectRepository;
import com.java.erp.modules.auth.entity.User;
import com.java.erp.modules.system.entity.Batch;
import com.java.erp.modules.system.entity.Section;
import com.java.erp.modules.system.entity.Student;
import com.java.erp.modules.system.repository.BatchRepository;
import com.java.erp.modules.system.repository.SectionRepository;
import com.java.erp.modules.system.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Academic-structure fixtures: a fresh batch with two sections, a STAFF member, another STAFF
 * member and an HOD. Everything is rolled back after each test.
 */
public abstract class AcademicTestBase extends IntegrationTestBase {

    protected static final String ACADEMIC_YEAR = "2026-2027";

    @Autowired protected BatchRepository batchRepository;
    @Autowired protected SectionRepository sectionRepository;
    @Autowired protected StudentRepository studentRepository;
    @Autowired protected SubjectRepository subjectRepository;
    @Autowired protected SubjectOfferingRepository offeringRepository;

    protected User staff;
    protected User otherStaff;
    protected User hod;
    protected Batch batch;
    protected Section section;
    protected Section otherSection;

    private final Map<Long, String> tokens = new HashMap<>();

    public record StudentLogin(User user, Student student) {
    }

    @BeforeEach
    void setUpAcademicFixture() {
        staff = createUser("ROLE_STAFF", true);
        otherStaff = createUser("ROLE_STAFF", true);
        hod = createUser("ROLE_HOD", true);
        batch = batchRepository.save(new Batch("GB-" + unique(), 2025, 2029, (short) 2));
        section = sectionRepository.save(new Section(batch, "A"));
        otherSection = sectionRepository.save(new Section(batch, "B"));
    }

    protected static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /** One login per user per test (BCrypt makes logins slow). */
    protected String token(User user) throws Exception {
        String token = tokens.get(user.getId());
        if (token == null) {
            token = bearer(user);
            tokens.put(user.getId(), token);
        }
        return token;
    }

    protected Subject subject(Subject.SubjectType type) {
        return subjectRepository.saveAndFlush(new Subject("T" + unique().toUpperCase(Locale.ROOT), "Subject " + unique(), type));
    }

    protected SubjectOffering offering(Subject subject, Section target, User owner) {
        SubjectOffering offering = new SubjectOffering();
        offering.setSubject(subject);
        offering.setBatch(target.getBatch());
        offering.setSection(target);
        offering.setStaff(owner);
        offering.setAcademicYear(ACADEMIC_YEAR);
        offering.setSemester(3);
        return offeringRepository.saveAndFlush(offering);
    }

    protected SubjectOffering theoryOffering() {
        return offering(subject(Subject.SubjectType.THEORY), section, staff);
    }

    protected Student student(Section target) {
        Student student = new Student("GB" + unique(), "Student " + unique(), null);
        student.setBatch(target.getBatch());
        student.setSection(target);
        return studentRepository.saveAndFlush(student);
    }

    protected StudentLogin studentLogin(Section target) {
        User user = createUser("ROLE_STUDENT", true);
        Student student = student(target);
        student.setEmail(user.getEmail());
        student.setUser(user);
        return new StudentLogin(user, studentRepository.saveAndFlush(student));
    }

    protected JsonNode data(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("data");
    }
}

package com.java.erp.config;

import com.java.erp.modules.academic.entity.Subject;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The authoritative B.Tech curriculum for Semesters 1-3 as supplied by the department (dev seed data,
 * read by {@link CurriculumDevSeeder}). Codes such as COUN or LIB recur in several semesters: they are
 * one Subject each; the semester lives on the subject offering.
 *
 * Subject type (department rule): course code suffix T = THEORY, J = THEORY_CUM_PRACTICAL,
 * R = PROJECT_BASED. Every other subject (suffix L or B, and the code-less items such as COUN) has no
 * authoritative type and stays unclassified (null) until the HOD sets one.
 */
public final class DevCurriculum {

    public record Course(String code, String name, int credits) {
        public Subject.SubjectType type() {
            return typeFromCode(code);
        }
    }

    private static final Pattern COURSE_CODE = Pattern.compile("^\\d{4}[A-Z]{3}\\d{3}([A-Z])$");

    public static final Map<Integer, List<Course>> SEMESTERS = Map.of(
            1, List.of(
                    new Course("2321CSS101J", "Problem Solving Using C", 4),
                    new Course("2321CSS103J", "Web Essentials", 4),
                    new Course("2321CYB102T", "Environmental Chemistry", 2),
                    new Course("2321GEH101T", "Heritage of Tamils / தமிழர் மரபு", 1),
                    new Course("2321LEH101J", "Technical English", 3),
                    new Course("2321MAB102T", "Matrices and Calculus", 4),
                    new Course("2321GEM101B", "Induction Program", 0),
                    new Course("2321SDA101L", "Career Skill Development I", 1),
                    new Course("ATT", "Aptitude Training Test", 0),
                    new Course("COUN", "Counseling", 0),
                    new Course("LC", "Leet Code", 0),
                    new Course("LIB", "Library", 0)),
            2, List.of(
                    new Course("2321ADC201J", "Introduction to Artificial Intelligence", 3),
                    new Course("2321CSS204J", "Data Structures Using C", 4),
                    new Course("2321CSS206J", "Digital Principles and Computer Organization", 4),
                    new Course("2321CSS207J", "Foundations of Data Science", 4),
                    new Course("2321GEH201T", "Tamils and Technology / தமிழரும் தொழில்நுட்பமும்", 1),
                    new Course("2321MAB201T", "Linear Algebra", 4),
                    new Course("2321PYB202J", "Semiconductors and Quantum Computing", 4),
                    new Course("2321SDA201L", "Career Skill Development II", 1),
                    new Course("AT", "Aptitude Test", 0),
                    new Course("COUN", "Counseling", 0),
                    new Course("LEET", "Leet Code Practice", 0),
                    new Course("TT", "Technical Test", 0),
                    new Course("SS", "Soft Skills", 0),
                    new Course("LIB", "Library", 0)),
            3, List.of(
                    new Course("2321ALC301J", "Machine Learning Techniques", 3),
                    new Course("2321CSC301T", "Computer Networks", 3),
                    new Course("2321CSC302J", "Advanced Data Structures and Algorithms", 4),
                    new Course("2321CSC304R", "Object Oriented Programming Using Java", 4),
                    new Course("2321CSS301J", "Embedded System Design", 4),
                    new Course("2321MAB301T", "Discrete Mathematics", 4),
                    new Course("2321SDA301L", "Career Skill Development III", 1),
                    new Course("AT", "Aptitude Test", 0),
                    new Course("COUN", "Counseling", 0),
                    new Course("NPTEL", "NPTEL", 0),
                    new Course("TT", "Technical Test", 0),
                    new Course("LC", "Leet Code", 0)));

    private DevCurriculum() {
    }

    /** T / J / R suffix of a full course code; null (unclassified) for everything else. */
    public static Subject.SubjectType typeFromCode(String code) {
        Matcher m = COURSE_CODE.matcher(code);
        if (!m.matches()) {
            return null;
        }
        return switch (m.group(1)) {
            case "T" -> Subject.SubjectType.THEORY;
            case "J" -> Subject.SubjectType.THEORY_CUM_PRACTICAL;
            case "R" -> Subject.SubjectType.PROJECT_BASED;
            default -> null;
        };
    }

    /** Year of study a semester belongs to: 1-2 -> 1, 3-4 -> 2, ... */
    public static int yearOfSemester(int semester) {
        return (semester + 1) / 2;
    }
}

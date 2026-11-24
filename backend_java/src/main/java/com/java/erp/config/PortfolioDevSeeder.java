package com.java.erp.config;

import com.java.erp.modules.auth.entity.User;
import com.java.erp.modules.auth.repository.UserRepository;
import com.java.erp.modules.portfolio.entity.PortfolioEntry;
import com.java.erp.modules.portfolio.repository.PortfolioEntryRepository;
import com.java.erp.modules.system.entity.Section;
import com.java.erp.modules.system.entity.Student;
import com.java.erp.modules.system.repository.SectionRepository;
import com.java.erp.modules.system.repository.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Small, predictable portfolio demo data for local development (dev profile only; never in the
 * default/production profile, not in tests). Runs after {@link DataSeeder}, and only while the
 * portfolio_entries table is empty.
 *
 * Demo student (aarav.sharma1@student.erp.com): VERIFIED Hackathon, REJECTED Workshop, DRAFT Internship.
 * Two classmates: VERIFIED Certification, SUBMITTED Competition.
 * One student of a neighbouring section: SUBMITTED Project showcase (for review-scope checks).
 * Decisions are attributed to staff@department.com (or the HOD if that login is missing).
 */
@Component
@Profile("dev")
@Order(3)
public class PortfolioDevSeeder implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(PortfolioDevSeeder.class);

    private final PortfolioEntryRepository entries;
    private final StudentRepository students;
    private final SectionRepository sections;
    private final UserRepository users;

    public PortfolioDevSeeder(PortfolioEntryRepository entries, StudentRepository students,
                              SectionRepository sections, UserRepository users) {
        this.entries = entries;
        this.students = students;
        this.sections = sections;
        this.users = users;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (entries.count() > 0) {
            logger.info("Portfolio data already present, skipping demo portfolio seed");
            return;
        }
        Optional<Student> demo = users.findByEmail("aarav.sharma1@student.erp.com")
                .flatMap(u -> students.findLinkedStudent(u.getId(), u.getEmail()));
        Optional<User> reviewer = users.findByEmail("staff@department.com").or(() -> users.findByEmail("hod@department.com"));
        if (demo.isEmpty() || reviewer.isEmpty()) {
            logger.warn("Demo student or reviewer login not found; portfolio demo data not seeded");
            return;
        }
        Student student = demo.get();
        Section section = student.getSection();
        List<Student> classmates = students.findBySectionIdAndActiveTrueOrderByRegisterNumberAsc(section.getId()).stream()
                .filter(s -> !s.getId().equals(student.getId()))
                .limit(2)
                .toList();
        LocalDate today = LocalDate.now();

        PortfolioEntry hackathon = entry(student, PortfolioEntry.ActivityType.HACKATHON, "Smart India Hackathon 2026",
                "Ministry of Education, Government of India",
                "Built a crowd-sourced flood alert app with a five-member team during the 36-hour grand finale.",
                PortfolioEntry.Level.NATIONAL, "Team lead", "Winner", today.minusMonths(3), today.minusMonths(3).plusDays(1),
                "https://www.sih.gov.in/");
        decide(hackathon, PortfolioEntry.Status.VERIFIED, reviewer.get(), "Certificate checked.", today.minusMonths(2));

        PortfolioEntry workshop = entry(student, PortfolioEntry.ActivityType.WORKSHOP, "Docker and Kubernetes Workshop",
                "Department Coding Club", "Two-day hands-on workshop on containers and orchestration.",
                PortfolioEntry.Level.COLLEGE, "Participant", null, today.minusMonths(1), today.minusMonths(1).plusDays(1), null);
        decide(workshop, PortfolioEntry.Status.REJECTED, reviewer.get(),
                "Please add the participation certificate link as evidence.", today.minusDays(20));

        entries.save(entry(student, PortfolioEntry.ActivityType.INTERNSHIP, "Backend Developer Intern",
                "Zoho Corporation", null, null, "Intern", null, today.minusDays(10), null, null));

        if (!classmates.isEmpty()) {
            PortfolioEntry cert = entry(classmates.get(0), PortfolioEntry.ActivityType.CERTIFICATION,
                    "AWS Certified Cloud Practitioner", "Amazon Web Services",
                    "Foundational AWS certification covering cloud concepts, security, billing and core services.",
                    PortfolioEntry.Level.ONLINE, null, "Passed (score 812/1000)", today.minusMonths(2), null,
                    "https://www.credly.com/badges/example-aws-ccp");
            decide(cert, PortfolioEntry.Status.VERIFIED, reviewer.get(), null, today.minusMonths(1));
        }
        if (classmates.size() > 1) {
            submitted(entry(classmates.get(1), PortfolioEntry.ActivityType.COMPETITION, "National Level Coding Contest",
                    "PSG College of Technology", "Inter-college competitive programming contest with 400+ participants.",
                    PortfolioEntry.Level.INTER_COLLEGE, "Participant", "2nd place", today.minusDays(15), today.minusDays(15),
                    null), today.minusDays(5));
        }
        sections.findByBatchIdOrderBySectionNameAsc(section.getBatch().getId()).stream()
                .filter(s -> !s.getId().equals(section.getId()))
                .findFirst()
                .flatMap(other -> students.findBySectionIdAndActiveTrueOrderByRegisterNumberAsc(other.getId()).stream().findFirst())
                .ifPresent(other -> submitted(entry(other, PortfolioEntry.ActivityType.PROJECT_SHOWCASE,
                        "Smart Campus Energy Monitor", "State Science and Technology Expo",
                        "IoT dashboard that tracks classroom energy use, shown at the state-level project expo.",
                        PortfolioEntry.Level.STATE, "Developer", "Best project (IoT track)", today.minusDays(25), null,
                        "https://github.com/example/smart-campus-energy"), today.minusDays(3)));

        logger.info("Seeded portfolio demo data ({} entries)", entries.count());
    }

    private static PortfolioEntry entry(Student student, PortfolioEntry.ActivityType type, String title, String organization,
                                        String description, PortfolioEntry.Level level, String role, String result,
                                        LocalDate start, LocalDate end, String evidenceUrl) {
        PortfolioEntry e = new PortfolioEntry();
        e.setStudent(student);
        e.setActivityType(type);
        e.setTitle(title);
        e.setOrganization(organization);
        e.setDescription(description);
        e.setLevel(level);
        e.setRoleOrPosition(role);
        e.setAchievementResult(result);
        e.setStartDate(start);
        e.setEndDate(end);
        e.setEvidenceUrl(evidenceUrl);
        return e;
    }

    private void submitted(PortfolioEntry e, LocalDate submittedOn) {
        e.setStatus(PortfolioEntry.Status.SUBMITTED);
        e.setSubmittedAt(submittedOn.atTime(9, 30));
        entries.save(e);
    }

    private void decide(PortfolioEntry e, PortfolioEntry.Status decision, User reviewer, String note, LocalDate decidedOn) {
        e.setStatus(decision);
        e.setSubmittedAt(decidedOn.minusDays(2).atTime(9, 30));
        e.setReviewedBy(reviewer);
        e.setReviewedAt(decidedOn.atTime(15, 0));
        e.setReviewNote(note);
        entries.save(e);
    }
}

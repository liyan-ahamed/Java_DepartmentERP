package com.java.erp.config;

import com.java.erp.modules.fees.entity.FeeStructure;
import com.java.erp.modules.fees.entity.StudentFeeAccount;
import com.java.erp.modules.fees.repository.FeeStructureRepository;
import com.java.erp.modules.fees.repository.StudentFeeAccountRepository;
import com.java.erp.modules.system.entity.Student;
import com.java.erp.modules.system.repository.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * Dev profile only: the department's authoritative fee templates (amounts exactly as supplied).
 *   Year 1: Tuition Fee 130,000 + Placement Value Added Course 25,000 + Book Fee 25,000 + e-learning 25,000 = 205,000
 *   Year 2: Tuition Fee 130,000 + e-learning 25,000 + Culturals and Events Fee 1,525 = 156,525
 * Year 3 / Year 4: no values supplied, so nothing is created ("not configured").
 * Templates are year-based (no batch, no academic year) and are charged to every active student of the cohort
 * currently in that year, with concession 0. Idempotent: existing template charges and student charges are reused.
 */
@Component
@Profile("dev")
@Order(7)
public class FeeTemplateDevSeeder implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(FeeTemplateDevSeeder.class);

    record Item(String particular, FeeStructure.FeeType type, String amount) {
    }

    static final Map<Short, List<Item>> TEMPLATES = Map.of(
            (short) 1, List.of(
                    new Item("Tuition Fee", FeeStructure.FeeType.TUITION, "130000.00"),
                    new Item("Placement Value Added Course", FeeStructure.FeeType.OTHER, "25000.00"),
                    new Item("Book Fee", FeeStructure.FeeType.OTHER, "25000.00"),
                    new Item("e-learning", FeeStructure.FeeType.OTHER, "25000.00")),
            (short) 2, List.of(
                    new Item("Tuition Fee", FeeStructure.FeeType.TUITION, "130000.00"),
                    new Item("e-learning", FeeStructure.FeeType.OTHER, "25000.00"),
                    new Item("Culturals and Events Fee", FeeStructure.FeeType.OTHER, "1525.00")));

    /** What one run changed (all zero on a re-run). */
    public record Report(int structuresCreated, int chargesCreated) {
    }

    private final FeeStructureRepository structures;
    private final StudentFeeAccountRepository accounts;
    private final StudentRepository students;

    public FeeTemplateDevSeeder(FeeStructureRepository structures, StudentFeeAccountRepository accounts,
                                StudentRepository students) {
        this.structures = structures;
        this.accounts = accounts;
        this.students = students;
    }

    @Override
    @Transactional
    public void run(String... args) {
        logger.info("Fee templates dev data: {}", seed());
    }

    @Transactional
    public Report seed() {
        int structuresCreated = 0, chargesCreated = 0;
        for (Short year : new TreeSet<>(TEMPLATES.keySet())) {
            Map<String, FeeStructure> existing = new HashMap<>();
            structures.findByStudentYearAndAcademicYearIsNullAndActiveTrueOrderByIdAsc(year)
                    .forEach(f -> existing.put(f.getName().toLowerCase(Locale.ROOT), f));
            List<FeeStructure> template = new ArrayList<>();
            for (Item item : TEMPLATES.get(year)) {
                FeeStructure f = existing.get(item.particular().toLowerCase(Locale.ROOT));
                if (f == null) {
                    f = new FeeStructure();
                    f.setName(item.particular());
                    f.setFeeType(item.type());
                    f.setStudentYear(year);
                    f.setAmount(new BigDecimal(item.amount()));
                    f.setActive(true);
                    f = structures.save(f);
                    structuresCreated++;
                }
                template.add(f);
            }
            structures.flush();

            List<Student> cohort = students.findActiveInActiveBatchOfYear(year);
            for (FeeStructure f : template) {
                Set<Long> charged = accounts.findStudentIdsByFeeStructureId(f.getId());
                List<StudentFeeAccount> created = new ArrayList<>();
                for (Student s : cohort) {
                    if (!charged.contains(s.getId())) created.add(new StudentFeeAccount(s, f, null));
                }
                accounts.saveAll(created);
                chargesCreated += created.size();
            }
        }
        accounts.flush();
        return new Report(structuresCreated, chargesCreated);
    }
}

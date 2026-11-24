package com.java.erp.modules.fees;

import com.fasterxml.jackson.databind.JsonNode;
import com.java.erp.config.FeeTemplateDevSeeder;
import com.java.erp.modules.fees.repository.FeeStructureRepository;
import com.java.erp.modules.fees.repository.StudentFeeAccountRepository;
import com.java.erp.modules.system.entity.Batch;
import com.java.erp.modules.system.entity.Section;
import com.java.erp.support.ErpModulesTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Year 1 / Year 2 fee templates (dev seed values), per-year breakdown and concessions. */
class FeeTemplateIntegrationTest extends ErpModulesTestBase {

    @Autowired private FeeStructureRepository structureRepository;
    @Autowired private StudentFeeAccountRepository accountRepository;

    private FeeTemplateDevSeeder seeder;
    private StudentLogin firstYear;
    private StudentLogin secondYear;   // the fixture batch is Year 2

    @BeforeEach
    void seed() {
        Batch y1 = batchRepository.save(new Batch("FY1-" + unique(), 2026, 2030, (short) 1));
        firstYear = studentLogin(sectionRepository.save(new Section(y1, "A")));
        secondYear = studentLogin(section);
        seeder = new FeeTemplateDevSeeder(structureRepository, accountRepository, studentRepository);
        seeder.seed();
    }

    private static Map<String, BigDecimal> items(JsonNode list, String nameField, String amountField) {
        Map<String, BigDecimal> m = new LinkedHashMap<>();
        list.forEach(i -> m.put(i.get(nameField).asText(), i.get(amountField).decimalValue().setScale(2)));
        return m;
    }

    @Test
    void templatesMatchTheSuppliedStructuresAndYear3And4AreNotConfigured() throws Exception {
        JsonNode templates = data(getAs(hod, "/fees/templates").andExpect(status().isOk()));
        assertThat(templates).hasSize(4);
        JsonNode y1 = templates.get(0);
        assertThat(y1.get("configured").asBoolean()).isTrue();
        assertThat(items(y1.get("items"), "particular", "actualAmount")).containsOnly(
                Map.entry("Tuition Fee", new BigDecimal("130000.00")),
                Map.entry("Placement Value Added Course", new BigDecimal("25000.00")),
                Map.entry("Book Fee", new BigDecimal("25000.00")),
                Map.entry("e-learning", new BigDecimal("25000.00")));
        assertThat(y1.get("totalActualAmount").decimalValue()).isEqualByComparingTo("205000");
        assertThat(y1.get("defaultConcessionAmount").decimalValue()).isEqualByComparingTo("0");
        assertThat(y1.get("defaultPayableAmount").decimalValue()).isEqualByComparingTo("205000");

        JsonNode y2 = templates.get(1);
        assertThat(items(y2.get("items"), "particular", "actualAmount")).containsOnly(
                Map.entry("Tuition Fee", new BigDecimal("130000.00")),
                Map.entry("e-learning", new BigDecimal("25000.00")),
                Map.entry("Culturals and Events Fee", new BigDecimal("1525.00")));
        assertThat(y2.get("totalActualAmount").decimalValue()).isEqualByComparingTo("156525");
        assertThat(y2.get("defaultPayableAmount").decimalValue()).isEqualByComparingTo("156525");

        for (int i = 2; i < 4; i++) {
            assertThat(templates.get(i).get("configured").asBoolean()).isFalse();
            assertThat(templates.get(i).get("items")).isEmpty();
            assertThat(templates.get(i).get("totalActualAmount").isNull()).isTrue();
        }
        getAs(staff, "/fees/templates").andExpect(status().isForbidden());
        // Re-seeding changes nothing.
        assertThat(seeder.seed()).isEqualTo(new FeeTemplateDevSeeder.Report(0, 0));
    }

    @Test
    void studentFeeAccountShowsYearBreakdownWithReadyTotals() throws Exception {
        JsonNode mine = data(getAs(firstYear.user(), "/fees/me").andExpect(status().isOk()));
        assertThat(mine.get("years")).hasSize(1);
        JsonNode year = mine.get("years").get(0);
        assertThat(year.get("year").asInt()).isEqualTo(1);
        assertThat(year.get("label").asText()).isEqualTo("Year 1");
        assertThat(year.get("totalActualAmount").decimalValue()).isEqualByComparingTo("205000");
        assertThat(year.get("totalConcessionAmount").decimalValue()).isEqualByComparingTo("0");
        assertThat(year.get("totalPayableAmount").decimalValue()).isEqualByComparingTo("205000");
        assertThat(year.get("totalPaidAmount").decimalValue()).isEqualByComparingTo("0");
        assertThat(year.get("totalBalanceAmount").decimalValue()).isEqualByComparingTo("205000");
        assertThat(year.get("itemBreakdown")).hasSize(4).allSatisfy(i -> {
            assertThat(i.get("concessionAmount").decimalValue()).isEqualByComparingTo("0");
            assertThat(i.get("payableAmount").decimalValue()).isEqualByComparingTo(i.get("actualAmount").decimalValue());
            assertThat(i.get("balanceAmount").decimalValue()).isEqualByComparingTo(i.get("payableAmount").decimalValue());
        });
        assertThat(mine.get("totalPayable").decimalValue()).isEqualByComparingTo("205000");

        JsonNode second = data(getAs(hod, "/fees/students/" + secondYear.student().getId()).andExpect(status().isOk()));
        assertThat(second.get("years").get(0).get("year").asInt()).isEqualTo(2);
        assertThat(second.get("years").get(0).get("totalActualAmount").decimalValue()).isEqualByComparingTo("156525");
        getAs(firstYear.user(), "/fees/students/" + secondYear.student().getId()).andExpect(status().isForbidden());
    }

    @Test
    void concessionReducesPayableAndPaymentsFollowIt() throws Exception {
        JsonNode tuition = null;
        for (JsonNode i : data(getAs(firstYear.user(), "/fees/me")).get("years").get(0).get("itemBreakdown")) {
            if (i.get("particular").asText().equals("Tuition Fee")) tuition = i;
        }
        long accountId = tuition.get("feeAccountId").asLong();

        putAs(hod, "/fees/accounts/" + accountId + "/concession", Map.of("concessionAmount", "130000.01")).andExpect(status().isBadRequest());
        putAs(hod, "/fees/accounts/" + accountId + "/concession", Map.of("concessionAmount", "-1")).andExpect(status().isBadRequest());
        putAs(staff, "/fees/accounts/" + accountId + "/concession", Map.of("concessionAmount", "5000")).andExpect(status().isForbidden());

        JsonNode conceded = data(putAs(hod, "/fees/accounts/" + accountId + "/concession", Map.of("concessionAmount", "5000.00"))
                .andExpect(status().isOk()));
        assertThat(conceded.get("payableAmount").decimalValue()).isEqualByComparingTo("125000");
        assertThat(conceded.get("balance").decimalValue()).isEqualByComparingTo("125000");

        postAs(hod, "/fees/accounts/" + accountId + "/payments", Map.of("amount", "125000.01", "paymentMethod", "UPI"))
                .andExpect(status().isBadRequest());
        postAs(hod, "/fees/accounts/" + accountId + "/payments", Map.of("amount", "100000.00", "paymentMethod", "UPI"))
                .andExpect(status().isCreated());
        // The concession cannot push payable below what has been paid.
        putAs(hod, "/fees/accounts/" + accountId + "/concession", Map.of("concessionAmount", "30000.01")).andExpect(status().isBadRequest());
        postAs(hod, "/fees/accounts/" + accountId + "/payments", Map.of("amount", "25000.00", "paymentMethod", "CASH"))
                .andExpect(status().isCreated());

        JsonNode year = data(getAs(firstYear.user(), "/fees/me")).get("years").get(0);
        assertThat(year.get("totalConcessionAmount").decimalValue()).isEqualByComparingTo("5000");
        assertThat(year.get("totalPayableAmount").decimalValue()).isEqualByComparingTo("200000");
        assertThat(year.get("totalPaidAmount").decimalValue()).isEqualByComparingTo("125000");
        assertThat(year.get("totalBalanceAmount").decimalValue()).isEqualByComparingTo("75000");
        JsonNode paid = null;
        for (JsonNode i : year.get("itemBreakdown")) if (i.get("feeAccountId").asLong() == accountId) paid = i;
        assertThat(paid.get("status").asText()).isEqualTo("PAID");
        assertThat(paid.get("balanceAmount").decimalValue()).isEqualByComparingTo("0");
    }

    @Test
    void yearTemplatesValidateTheirInput() throws Exception {
        postAs(hod, "/fees/structures", Map.of("name", "Lab Fee", "feeType", "LAB", "amount", "100.00"))
                .andExpect(status().isBadRequest());     // neither academicYear nor studentYear
        postAs(hod, "/fees/structures", Map.of("name", "Tuition Fee", "feeType", "TUITION", "studentYear", 1, "amount", "1.00"))
                .andExpect(status().isConflict());       // already in the Year 1 template
        postAs(hod, "/fees/structures", Map.of("name", "Hostel", "feeType", "OTHER", "studentYear", 5, "amount", "1.00"))
                .andExpect(status().isBadRequest());
        JsonNode created = data(postAs(hod, "/fees/structures", Map.of("name", "Lab Fee", "feeType", "LAB", "studentYear", 3,
                "amount", "500.00")).andExpect(status().isCreated()));
        assertThat(created.get("studentYear").asInt()).isEqualTo(3);
        assertThat(created.get("academicYear").isNull()).isTrue();
    }
}

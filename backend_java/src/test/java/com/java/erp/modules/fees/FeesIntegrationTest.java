package com.java.erp.modules.fees;

import com.fasterxml.jackson.databind.JsonNode;
import com.java.erp.modules.system.entity.Student;
import com.java.erp.support.ErpModulesTestBase;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FeesIntegrationTest extends ErpModulesTestBase {

    private Map<String, Object> structure(String amount) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", "Tuition " + unique());
        m.put("feeType", "TUITION");
        m.put("academicYear", "2026-2027");
        m.put("semester", 3);
        m.put("batchId", batch.getId());
        m.put("amount", amount);
        m.put("dueDate", LocalDate.now().plusDays(30).toString());
        return m;
    }

    private long createStructure(String amount) throws Exception {
        return data(postAs(hod, "/fees/structures", structure(amount)).andExpect(status().isCreated())).get("id").asLong();
    }

    /** Assigns the fee to the given students and returns their account ids by student id. */
    private Map<Long, Long> assign(long structureId, List<Student> list) throws Exception {
        postAs(hod, "/fees/structures/" + structureId + "/assign",
                Map.of("studentIds", list.stream().map(Student::getId).toList())).andExpect(status().isOk());
        JsonNode accounts = data(getAs(hod, "/fees/accounts?feeStructureId=" + structureId + "&size=200"));
        Map<Long, Long> result = new HashMap<>();
        accounts.get("content").forEach(a -> result.put(a.get("studentId").asLong(), a.get("id").asLong()));
        return result;
    }

    private JsonNode pay(long accountId, String amount) throws Exception {
        return data(postAs(hod, "/fees/accounts/" + accountId + "/payments", Map.of("amount", amount, "paymentMethod", "UPI"))
                .andExpect(status().isCreated()));
    }

    @Test
    void hodCreatesFeeStructure() throws Exception {
        postAs(hod, "/fees/structures", structure("45000.00"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.amount").value(45000.00))
                .andExpect(jsonPath("$.data.batchId").value(batch.getId()));
        Map<String, Object> bad = structure("0");
        postAs(hod, "/fees/structures", bad).andExpect(status().isBadRequest());
        bad = structure("10.555");
        postAs(hod, "/fees/structures", bad).andExpect(status().isBadRequest());
    }

    @Test
    void assignChargesSkipsAlreadyChargedStudents() throws Exception {
        long id = createStructure("1000.00");
        students(section, 2);
        students(otherSection, 1);
        postAs(hod, "/fees/structures/" + id + "/assign", Map.of("sectionId", section.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.assigned").value(2));
        postAs(hod, "/fees/structures/" + id + "/assign", null) // whole batch
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetedStudents").value(3))
                .andExpect(jsonPath("$.data.assigned").value(1))
                .andExpect(jsonPath("$.data.alreadyAssigned").value(2));
        putAs(hod, "/fees/structures/" + id, structure("2000.00")).andExpect(status().isConflict());
    }

    @Test
    void partialThenFullPaymentWithDecimalSafeBalances() throws Exception {
        long id = createStructure("1000.10");
        Student s = student(section);
        long account = assign(id, List.of(s)).get(s.getId());

        pay(account, "0.10");
        pay(account, "300.20");
        getAs(hod, "/fees/accounts/" + account).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.account.status").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.data.account.paidAmount").value(300.30))
                .andExpect(jsonPath("$.data.account.balance").value(699.80))
                .andExpect(jsonPath("$.data.payments.length()").value(2));

        postAs(hod, "/fees/accounts/" + account + "/payments", Map.of("amount", "699.81", "paymentMethod", "CASH"))
                .andExpect(status().isBadRequest());
        pay(account, "699.80");
        getAs(hod, "/fees/accounts/" + account)
                .andExpect(jsonPath("$.data.account.status").value("PAID"))
                .andExpect(jsonPath("$.data.account.balance").value(0.00));
        postAs(hod, "/fees/accounts/" + account + "/payments", Map.of("amount", "1.00", "paymentMethod", "CASH"))
                .andExpect(status().isConflict());
    }

    @Test
    void waiverClosesTheBalance() throws Exception {
        long id = createStructure("500.00");
        Student s = student(section);
        long account = assign(id, List.of(s)).get(s.getId());
        pay(account, "100.00");
        postAs(hod, "/fees/accounts/" + account + "/waive", Map.of("reason", "Scholarship"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WAIVED"))
                .andExpect(jsonPath("$.data.balance").value(0.00));
        postAs(hod, "/fees/accounts/" + account + "/payments", Map.of("amount", "1.00", "paymentMethod", "CASH"))
                .andExpect(status().isConflict());
    }

    @Test
    void receiptNumbersAreUniqueAndStable() throws Exception {
        long id = createStructure("100.00");
        List<Student> list = students(section, 3);
        Map<Long, Long> accounts = assign(id, list);
        Set<String> numbers = new HashSet<>();
        for (Long account : accounts.values()) {
            JsonNode receipt = pay(account, "40.00");
            assertThat(receipt.get("receiptNumber").asText()).matches("RCPT-\\d{4}-\\d{6}");
            numbers.add(receipt.get("receiptNumber").asText());
            JsonNode again = data(getAs(hod, "/receipts/" + receipt.get("id").asLong()).andExpect(status().isOk()));
            assertThat(again.get("receiptNumber").asText()).isEqualTo(receipt.get("receiptNumber").asText());
        }
        assertThat(numbers).hasSize(3);
    }

    @Test
    void studentSeesOwnFeesAndReceiptsOnly() throws Exception {
        long id = createStructure("1000.00");
        StudentLogin me = studentLogin(section);
        Student other = student(section);
        Map<Long, Long> accounts = assign(id, List.of(me.student(), other));
        JsonNode mine = pay(accounts.get(me.student().getId()), "400.00");
        JsonNode theirs = pay(accounts.get(other.getId()), "1000.00");

        getAs(me.user(), "/fees/me").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accounts.length()").value(1))
                .andExpect(jsonPath("$.data.totalCharged").value(1000.00))
                .andExpect(jsonPath("$.data.totalPaid").value(400.00))
                .andExpect(jsonPath("$.data.totalOutstanding").value(600.00));
        getAs(me.user(), "/receipts/me").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].receiptNumber").value(mine.get("receiptNumber").asText()));
        getAs(me.user(), "/receipts/" + mine.get("id").asLong()).andExpect(status().isOk());
        getAs(me.user(), "/receipts/" + theirs.get("id").asLong()).andExpect(status().isForbidden());
        getAs(me.user(), "/fees/accounts").andExpect(status().isForbidden());
        getAs(me.user(), "/receipts").andExpect(status().isForbidden());
    }

    @Test
    void hodSeesAccountsSummaryAndReceipts() throws Exception {
        long id = createStructure("100.00");
        List<Student> list = students(section, 2);
        Map<Long, Long> accounts = assign(id, list);
        pay(accounts.get(list.get(0).getId()), "100.00");

        getAs(hod, "/fees/accounts?feeStructureId=" + id + "&outstandingOnly=true")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content.length()").value(1));
        getAs(hod, "/fees/summary").andExpect(status().isOk()).andExpect(jsonPath("$.data.accountsByStatus.PAID").isNumber());
        getAs(hod, "/receipts?studentId=" + list.get(0).getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1));
    }

    @Test
    void staffHaveNoFinancialAccess() throws Exception {
        long id = createStructure("100.00");
        postAs(staff, "/fees/structures", structure("10.00")).andExpect(status().isForbidden());
        postAs(staff, "/fees/structures/" + id + "/assign", null).andExpect(status().isForbidden());
        getAs(staff, "/fees/accounts").andExpect(status().isForbidden());
        getAs(staff, "/fees/summary").andExpect(status().isForbidden());
        getAs(staff, "/receipts").andExpect(status().isForbidden());
        getAs(staff, "/fees/me").andExpect(status().isForbidden());
    }
}

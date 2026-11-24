package com.java.erp.modules.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.java.erp.modules.auth.entity.User;
import com.java.erp.modules.system.entity.Batch;
import com.java.erp.modules.system.entity.Section;
import com.java.erp.support.GradebookTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AcademicStructureIntegrationTest extends GradebookTestBase {

    private Batch inactiveBatch;
    private Section inactiveSection;

    @BeforeEach
    void setUpInactiveBatch() {
        inactiveBatch = new Batch("OLD-" + unique(), 2018, 2022, (short) 4);
        inactiveBatch.setActive(false);
        inactiveBatch = batchRepository.save(inactiveBatch);
        inactiveSection = sectionRepository.save(new Section(inactiveBatch, "A"));
    }

    private List<JsonNode> list(String url, User user) throws Exception {
        List<JsonNode> items = new ArrayList<>();
        data(mvc.perform(get(url).header("Authorization", token(user))).andExpect(status().isOk())).forEach(items::add);
        return items;
    }

    private static JsonNode byId(List<JsonNode> items, Long id) {
        return items.stream().filter(n -> n.get("id").asLong() == id).findFirst().orElse(null);
    }

    @Test
    void hodAndStaffListActiveBatchesOnly() throws Exception {
        for (User user : List.of(hod, staff)) {
            List<JsonNode> batches = list("/batches", user);
            JsonNode item = byId(batches, batch.getId());
            assertThat(item).isNotNull();
            assertThat(item.get("name").asText()).isEqualTo(batch.getBatchName());
            assertThat(item.get("currentYear").asInt()).isEqualTo(2);
            assertThat(item.get("active").asBoolean()).isTrue();
            assertThat(byId(batches, inactiveBatch.getId())).isNull();
            assertThat(batches).allMatch(b -> b.get("active").asBoolean());
        }
    }

    @Test
    void hodAndStaffListSectionsWithTheirBatch() throws Exception {
        for (User user : List.of(hod, staff)) {
            List<JsonNode> sections = list("/sections", user);
            JsonNode item = byId(sections, section.getId());
            assertThat(item).isNotNull();
            assertThat(item.get("name").asText()).isEqualTo("A");
            assertThat(item.get("batchId").asLong()).isEqualTo(batch.getId());
            assertThat(item.get("batchName").asText()).isEqualTo(batch.getBatchName());
            assertThat(item.get("active").asBoolean()).isTrue();
            assertThat(byId(sections, otherSection.getId()).get("batchId").asLong()).isEqualTo(batch.getId());
            assertThat(byId(sections, inactiveSection.getId())).isNull();
        }
    }

    @Test
    void sectionsCanBeFilteredByBatch() throws Exception {
        List<JsonNode> sections = list("/sections?batchId=" + batch.getId(), hod);
        assertThat(sections).extracting(n -> n.get("id").asLong())
                .containsExactly(section.getId(), otherSection.getId());
        assertThat(list("/sections?batchId=" + inactiveBatch.getId(), hod)).isEmpty();
    }

    @Test
    void studentsCannotReadLookups() throws Exception {
        User student = studentLogin(section).user();
        mvc.perform(get("/batches").header("Authorization", token(student))).andExpect(status().isForbidden());
        mvc.perform(get("/sections").header("Authorization", token(student))).andExpect(status().isForbidden());
        mvc.perform(get("/batches")).andExpect(status().isUnauthorized());
    }
}

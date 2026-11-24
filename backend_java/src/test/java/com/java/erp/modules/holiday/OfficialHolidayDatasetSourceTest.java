package com.java.erp.modules.holiday;

import com.java.erp.config.JacksonConfig;
import com.java.erp.modules.holiday.entity.Holiday;
import com.java.erp.modules.holiday.source.HolidaySource.HolidayRecord;
import com.java.erp.modules.holiday.source.HolidaySource.HolidaySourceUnavailableException;
import com.java.erp.modules.holiday.source.OfficialHolidayDatasetSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The bundled official dataset (no network). */
class OfficialHolidayDatasetSourceTest {

    private static OfficialHolidayDatasetSource source(String location) {
        return new OfficialHolidayDatasetSource(new DefaultResourceLoader(), new JacksonConfig().objectMapper(), location);
    }

    @Test
    void bundled2026DatasetHasCentralAndTamilNaduHolidays() {
        List<HolidayRecord> all = source("classpath:holidays/").fetch(2026).orElseThrow();
        List<HolidayRecord> national = all.stream().filter(r -> r.scope() == Holiday.Scope.NATIONAL).toList();
        List<HolidayRecord> tamilNadu = all.stream().filter(r -> r.scope() == Holiday.Scope.TAMIL_NADU).toList();

        assertThat(national).hasSize(14);
        assertThat(tamilNadu).hasSize(23);
        assertThat(all).allSatisfy(r -> assertThat(r.date().getYear()).isEqualTo(2026));
        assertThat(national).anySatisfy(r -> {
            assertThat(r.date()).isEqualTo(LocalDate.of(2026, 1, 26));
            assertThat(r.name()).isEqualTo("Republic Day");
            assertThat(r.reference()).contains("DoPT");
        });
        assertThat(tamilNadu).anySatisfy(r -> {
            assertThat(r.date()).isEqualTo(LocalDate.of(2026, 1, 15));
            assertThat(r.name()).isEqualTo("Pongal");
            assertThat(r.reference()).contains("Tamil Nadu");
        });
        // Pongal is a Tamil Nadu holiday, not a central compulsory one; bank closing day is left out.
        assertThat(national).noneMatch(r -> r.name().contains("Pongal"));
        assertThat(all).noneMatch(r -> r.date().equals(LocalDate.of(2026, 4, 1)));
    }

    @Test
    void yearWithoutAFileHasNoData() {
        assertThat(source("classpath:holidays/").fetch(1999)).isEmpty();
    }

    @Test
    void invalidFilesAreReportedAsUnavailable(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("2030.json"), "{\"year\": 2031, \"sources\": [], \"holidays\": []}");
        Files.writeString(dir.resolve("2031.json"), "not json");
        OfficialHolidayDatasetSource source = source(dir.toUri().toString());
        assertThatThrownBy(() -> source.fetch(2030)).isInstanceOf(HolidaySourceUnavailableException.class);
        assertThatThrownBy(() -> source.fetch(2031)).isInstanceOf(HolidaySourceUnavailableException.class);
    }
}

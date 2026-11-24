package com.java.erp.modules.holiday.source;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.erp.modules.holiday.entity.Holiday;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.*;

/**
 * Holidays transcribed from the official government notifications, one JSON file per year
 * ({@code <holidays.dataset-location><year>.json}, default {@code classpath:holidays/}).
 *
 *   NATIONAL   - DoPT O.M. "Holidays to be observed in Central Government Offices" (compulsory list).
 *   TAMIL_NADU - Tamil Nadu G.O. declaring public holidays under the Negotiable Instruments Act, 1881.
 *
 * No public API publishes both lists (the official documents are PDFs), so the data is reviewed and
 * versioned here. A new year is added by dropping in its file (no code change); pointing
 * HOLIDAYS_DATASET_LOCATION at a folder (e.g. file:/srv/erp/holidays/) allows that without a rebuild.
 */
@Component
public class OfficialHolidayDatasetSource implements HolidaySource {

    /** File format. sources: one official reference per scope. */
    record Dataset(Integer year, List<SourceRef> sources, List<Entry> holidays) {
        record SourceRef(Holiday.Scope scope, String reference, String notes) {
        }

        record Entry(LocalDate date, String name, Holiday.Scope scope) {
        }
    }

    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private final String location;

    public OfficialHolidayDatasetSource(ResourceLoader resourceLoader, ObjectMapper objectMapper,
                                        @Value("${holidays.dataset-location:classpath:holidays/}") String location) {
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
        this.location = location.endsWith("/") ? location : location + "/";
    }

    @Override
    public String name() {
        return "official-dataset";
    }

    @Override
    public Set<Holiday.Scope> scopes() {
        return EnumSet.of(Holiday.Scope.NATIONAL, Holiday.Scope.TAMIL_NADU);
    }

    @Override
    public Optional<List<HolidayRecord>> fetch(int year) {
        Resource resource = resourceLoader.getResource(location + year + ".json");
        if (!resource.exists()) {
            return Optional.empty();
        }
        Dataset dataset;
        try (InputStream in = resource.getInputStream()) {
            dataset = objectMapper.readValue(in, Dataset.class);
        } catch (IOException e) {
            throw new HolidaySourceUnavailableException("Holiday dataset for " + year + " cannot be read: " + e.getMessage(), e);
        }
        return Optional.of(validate(year, dataset));
    }

    private static List<HolidayRecord> validate(int year, Dataset dataset) {
        if (dataset.year() == null || dataset.year() != year || dataset.holidays() == null || dataset.sources() == null) {
            throw invalid(year, "year, sources and holidays are required and the year must be " + year);
        }
        Map<Holiday.Scope, String> references = new EnumMap<>(Holiday.Scope.class);
        for (Dataset.SourceRef ref : dataset.sources()) {
            if (ref.scope() == null || ref.reference() == null || ref.reference().isBlank()) {
                throw invalid(year, "every source needs a scope and a reference");
            }
            references.put(ref.scope(), ref.reference().trim());
        }
        List<HolidayRecord> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Dataset.Entry e : dataset.holidays()) {
            if (e.date() == null || e.scope() == null || e.name() == null || e.name().isBlank()) {
                throw invalid(year, "every holiday needs a date, a name and a scope");
            }
            if (e.date().getYear() != year) {
                throw invalid(year, e.date() + " is not in " + year);
            }
            if (e.scope() == Holiday.Scope.COLLEGE) {
                throw invalid(year, "the government dataset cannot contain COLLEGE holidays (see holidays/college/)");
            }
            String reference = references.get(e.scope());
            if (reference == null) {
                throw invalid(year, "no source reference for scope " + e.scope());
            }
            if (!seen.add(e.date() + "|" + e.scope() + "|" + e.name().trim())) {
                throw invalid(year, "duplicate holiday " + e.name().trim() + " on " + e.date());
            }
            result.add(new HolidayRecord(e.date(), e.name().trim(), e.scope(), reference));
        }
        return result;
    }

    private static HolidaySourceUnavailableException invalid(int year, String reason) {
        return new HolidaySourceUnavailableException("Holiday dataset for " + year + " is invalid: " + reason, null);
    }
}

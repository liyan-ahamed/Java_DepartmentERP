package com.java.erp.modules.holiday.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * One holiday. COLLEGE rows form the institution calendar: imported once from the college calendar file or
 * entered by the HOD, and effective only while that year's {@link HolidayCalendar} is PUBLISHED.
 * NATIONAL / TAMIL_NADU rows are government reference lists written only by the sync; they never count as
 * institution holidays. A holiday is a calendar exception: it never changes the recurring timetable.
 */
@Entity
@Table(name = "holidays")
public class Holiday {

    /**
     * NATIONAL = India central government (DoPT compulsory list); TAMIL_NADU = Tamil Nadu state list (both reference
     * only); COLLEGE = the college's own calendar.
     */
    public enum Scope { NATIONAL, TAMIL_NADU, COLLEGE }

    /**
     * What the frontend displays. GENERAL_HOLIDAY = rows of the imported college calendar; PUBLIC_HOLIDAY = government
     * reference rows only (never accepted on writes); the others are chosen by the HOD.
     */
    public enum Category {
        GENERAL_HOLIDAY, PUBLIC_HOLIDAY, NATIONAL, STATE, INSTITUTIONAL, ACADEMIC_BREAK, OTHER;

        public static Category forScope(Scope scope) {
            return scope == Scope.COLLEGE ? GENERAL_HOLIDAY : PUBLIC_HOLIDAY;
        }
    }

    /** Source recorded for holidays entered through the API. */
    public static final String ERP_SOURCE = "Entered in the ERP";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;

    /** Inclusive last day of a multi-day holiday; null = one day. Same calendar year as holidayDate. */
    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 20)
    private Scope scope;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private Category category;

    @Column(name = "holiday_year", nullable = false)
    private Integer year;

    @Column(name = "source", nullable = false, length = 150)
    private String source;

    /** Null for holidays entered in the ERP. */
    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    /** Cohorts the holiday applies to; empty = the whole institution. */
    @ElementCollection
    @CollectionTable(name = "holiday_batches", joinColumns = @JoinColumn(name = "holiday_id"))
    @Column(name = "batch_id", nullable = false)
    private Set<Long> batchIds = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Holiday() {
    }

    /** A synced row (government list or college calendar file). */
    public Holiday(LocalDate holidayDate, String name, Scope scope, String source, LocalDateTime lastSyncedAt) {
        setHolidayDate(holidayDate);
        this.name = name;
        this.scope = scope;
        this.category = Category.forScope(scope);
        this.source = source;
        this.lastSyncedAt = lastSyncedAt;
    }

    /** A college holiday entered by the HOD. */
    public static Holiday college(LocalDate startDate, String name, Category category) {
        Holiday h = new Holiday();
        h.setHolidayDate(startDate);
        h.name = name;
        h.scope = Scope.COLLEGE;
        h.category = category;
        h.source = ERP_SOURCE;
        return h;
    }

    /** Last day covered (endDate, or holidayDate for a one-day holiday). */
    public LocalDate getLastDate() {
        return endDate == null ? holidayDate : endDate;
    }

    public Long getId() { return id; }
    public LocalDate getHolidayDate() { return holidayDate; }
    public void setHolidayDate(LocalDate holidayDate) {
        this.holidayDate = holidayDate;
        this.year = holidayDate.getYear();
    }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Scope getScope() { return scope; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public Integer getYear() { return year; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public LocalDateTime getLastSyncedAt() { return lastSyncedAt; }
    public void setLastSyncedAt(LocalDateTime lastSyncedAt) { this.lastSyncedAt = lastSyncedAt; }
    public Set<Long> getBatchIds() { return batchIds; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

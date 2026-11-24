package com.java.erp.modules.holiday.entity;

import com.java.erp.modules.auth.entity.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Publication state of one CALENDAR year (Jan-Dec) of the college holiday calendar.
 * No row = NOT_CREATED. Only PUBLISHED years affect the timetable, attendance and dashboards,
 * and only PUBLISHED years are visible to STAFF / STUDENT.
 */
@Entity
@Table(name = "holiday_calendars")
public class HolidayCalendar {

    public enum Status { DRAFT, PUBLISHED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "calendar_year", nullable = false, unique = true, updatable = false)
    private Integer year;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "published_by")
    private User publishedBy;

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

    public HolidayCalendar() {
    }

    public HolidayCalendar(int year) {
        this.year = year;
    }

    public void publish(User by, LocalDateTime at) {
        status = Status.PUBLISHED;
        publishedBy = by;
        publishedAt = at;
    }

    public void unpublish() {
        status = Status.DRAFT;
        publishedBy = null;
        publishedAt = null;
    }

    public boolean isPublished() {
        return status == Status.PUBLISHED;
    }

    public Long getId() { return id; }
    public Integer getYear() { return year; }
    public Status getStatus() { return status; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public User getPublishedBy() { return publishedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

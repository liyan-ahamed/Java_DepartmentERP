package com.java.erp.modules.calendar.entity;

import com.java.erp.modules.auth.entity.User;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "calendar_entries")
public class CalendarEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;
    @Column(nullable = false, length = 255)
    private String title;
    @Column(name = "is_holiday", nullable = false)
    private boolean holiday;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected CalendarEntry() {}
    public CalendarEntry(LocalDate eventDate, String title, boolean holiday, User createdBy) {
        this.eventDate = eventDate;
        this.title = title;
        this.holiday = holiday;
        this.createdBy = createdBy;
    }
    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); }
    public Long getId() { return id; }
    public LocalDate getEventDate() { return eventDate; }
    public String getTitle() { return title; }
    public boolean isHoliday() { return holiday; }
    public User getCreatedBy() { return createdBy; }
}

package com.java.erp.modules.holiday.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Keeps the holiday cache filled for the current and the next calendar year: once at startup and
 * then on {@code holidays.sync.cron} (default weekly, Monday 03:00). Failures are logged only; the
 * cached holidays stay. Switched off with {@code holidays.sync.enabled=false} (tests).
 */
@Component
@ConditionalOnProperty(name = "holidays.sync.enabled", havingValue = "true", matchIfMissing = true)
public class HolidaySyncScheduler {

    private static final Logger logger = LoggerFactory.getLogger(HolidaySyncScheduler.class);

    private final HolidaySyncService syncService;

    public HolidaySyncScheduler(HolidaySyncService syncService) {
        this.syncService = syncService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        syncCurrentAndNextYear();
    }

    @Scheduled(cron = "${holidays.sync.cron:0 0 3 * * MON}")
    public void syncCurrentAndNextYear() {
        int year = LocalDate.now().getYear();
        for (int y = year; y <= year + 1; y++) {
            try {
                var result = syncService.sync(y);
                logger.info("Holiday sync {}: {} ({})", y, result.status(), result.message());
            } catch (RuntimeException e) {
                logger.warn("Holiday sync {} failed, cached holidays kept: {}", y, e.getMessage());
            }
        }
    }
}

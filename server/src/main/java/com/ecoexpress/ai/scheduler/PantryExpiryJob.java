package com.ecoexpress.ai.scheduler;

import com.ecoexpress.ai.service.PantryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily pantry expiry reminders (PRD §5.5): nudges users about items about to spoil so they use
 * them — and buy replacements — rather than throwing food away.
 *
 * <p><b>Single-instance assumption.</b> Like {@code ReservationExpiryJob}, {@code @Scheduled} fires
 * on every running instance. With one app instance (the launch shape) that is correct. Before
 * scaling horizontally this must move behind a shared lock (ShedLock), or N instances will each
 * send the reminder. It is not idempotent across instances within the same tick — the
 * {@code expiryNotifiedAt} stamp only prevents re-notifying on a LATER run.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PantryExpiryJob {

    /** How many days ahead counts as "expiring soon". Matches the storefront's 3-day badge. */
    @Value("${ecoexpress.pantry.expiry-window-days:3}")
    private int windowDays;

    private final PantryService pantryService;

    /**
     * Runs once a day at 08:00 IST — early enough that a reminder is useful for the same day's
     * cooking, not so early it arrives overnight. Wrapped so a bad run never kills the scheduler.
     */
    @Scheduled(cron = "${ecoexpress.pantry.expiry-cron:0 0 8 * * *}", zone = "Asia/Kolkata")
    public void sendExpiryReminders() {
        try {
            int notified = pantryService.sendExpiryReminders(windowDays);
            if (notified > 0) {
                log.info("Pantry expiry job reminded {} user(s)", notified);
            }
        } catch (Exception e) {
            // Never let a bad run stop future runs — the next day retries.
            log.error("Pantry expiry job failed; will retry next run", e);
        }
    }
}

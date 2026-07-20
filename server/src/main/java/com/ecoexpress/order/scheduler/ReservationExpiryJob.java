package com.ecoexpress.order.scheduler;

import com.ecoexpress.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Releases stock held by orders that were placed but never paid.
 *
 * <p>Without this, every abandoned checkout permanently strands its reserved units: the
 * shelf still has them, the storefront reports sold out, and nobody notices until someone
 * counts. This job is the counterpart to reserving stock at checkout — the reservation has
 * to be given back when payment does not arrive.
 *
 * <p><b>Single-instance assumption.</b> {@code @Scheduled} fires on every running instance.
 * With one app instance (the launch shape) that is correct. Before scaling horizontally,
 * this must move behind a shared lock (ShedLock) or a dedicated scheduler, or N instances
 * will all try to release the same reservations — which is safe (the release is idempotent
 * and clamped) but wasteful.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationExpiryJob {

    /**
     * How long an unpaid order may hold its reservation. Kept in sync with the intent of
     * the cart.reservation_ttl_minutes setting; a payment window of 30 minutes is generous
     * for UPI, which settles in seconds.
     */
    @Value("${ecoexpress.orders.payment-window-minutes:30}")
    private long paymentWindowMinutes;

    private final OrderService orderService;

    /**
     * Runs every 5 minutes. The window is 30 minutes, so an abandoned order is released
     * within ~35 minutes of placement — soon enough that stock is not stranded, late
     * enough that a customer mid-payment is not cancelled out from under them.
     *
     * <p>Wrapped so a failure in one run is logged and does not kill the scheduler thread.
     */
    @Scheduled(fixedDelayString = "PT5M")
    public void releaseExpiredReservations() {
        try {
            int released = orderService.releaseExpiredReservations(
                    Duration.ofMinutes(paymentWindowMinutes));
            if (released > 0) {
                log.info("Reservation-expiry job released {} unpaid order(s)", released);
            }
        } catch (Exception e) {
            // Never let a bad run stop future runs — the next tick retries.
            log.error("Reservation-expiry job failed; will retry next tick", e);
        }
    }
}

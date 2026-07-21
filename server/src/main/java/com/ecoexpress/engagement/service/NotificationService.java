package com.ecoexpress.engagement.service;

import com.ecoexpress.common.email.EmailSender;
import com.ecoexpress.engagement.domain.Notification;
import com.ecoexpress.engagement.domain.NotificationChannel;
import com.ecoexpress.engagement.domain.NotificationType;
import com.ecoexpress.engagement.repository.NotificationRepository;
import com.ecoexpress.identity.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Creates and reads user notifications.
 *
 * <p>Other modules call {@link #notify} to raise an in-app notification on an event (order placed,
 * shipped, delivered). Kept deliberately thin — actual email/SMS/push delivery is an outbox job's
 * job, reading {@code sent_at IS NULL}; this service only records the notification.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    /** Transactional events worth an email, not just an in-app dot. Marketing types stay in-app. */
    private static final Set<NotificationType> EMAIL_TYPES = EnumSet.of(
            NotificationType.ORDER_PLACED, NotificationType.ORDER_SHIPPED,
            NotificationType.ORDER_DELIVERED, NotificationType.ORDER_CANCELLED,
            NotificationType.PAYMENT_FAILED, NotificationType.REFUND_PROCESSED,
            NotificationType.PANTRY_EXPIRY, NotificationType.BACK_IN_STOCK);

    private final NotificationRepository notificationRepository;
    private final EmailSender emailSender;

    /**
     * Raise a notification for a user: always an in-app row, plus a transactional email for the
     * events that warrant one. Never throws into the caller's flow — a failed notification must not
     * roll back the order that triggered it.
     */
    @Transactional
    public void notify(User user, NotificationType type, String title, String body, String payloadJson) {
        try {
            notificationRepository.save(Notification.builder()
                    .user(user)
                    .type(type)
                    .channel(NotificationChannel.IN_APP)
                    .title(title)
                    .body(body)
                    .payload(payloadJson)
                    .build());
        } catch (Exception e) {
            log.error("Failed to create {} notification for user {}", type,
                    user == null ? "?" : user.getId(), e);
        }

        // Email is best-effort and async (see EmailSender). Pull the address inside the tx and hand
        // the sender only primitives — never the entity — so nothing lazy-loads off the request thread.
        if (user != null && EMAIL_TYPES.contains(type) && user.getEmail() != null) {
            try {
                emailSender.send(user.getEmail(), title, renderEmail(title, body));
            } catch (Exception e) {
                log.warn("Could not queue email for {}: {}", type, e.getMessage());
            }
        }
    }

    /** Minimal branded HTML wrapper around a notification's title and body. */
    private static String renderEmail(String title, String body) {
        String safeTitle = escape(title);
        String safeBody = escape(body);
        return """
                <div style="font-family:Arial,Helvetica,sans-serif;max-width:520px;margin:0 auto;\
                border:1px solid #e5e7eb;border-radius:12px;overflow:hidden">
                  <div style="background:#2f8f4e;padding:16px 24px;color:#fff;font-size:18px;font-weight:bold">\
                EcoExpress</div>
                  <div style="padding:24px;color:#111827">
                    <h2 style="margin:0 0 8px;font-size:18px">%s</h2>
                    <p style="margin:0;font-size:14px;line-height:1.6;color:#374151">%s</p>
                  </div>
                  <div style="padding:16px 24px;background:#f9fafb;color:#6b7280;font-size:12px">\
                EcoExpress · Organic groceries for India. This is a transactional email about your account.</div>
                </div>""".formatted(safeTitle, safeBody);
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notificationRepository.countUnread(userId);
    }

    @Transactional
    public void markRead(UUID userId, UUID notificationId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            // Scoped to the owner — you cannot mark someone else's notification read.
            if (n.getUser().getId().equals(userId) && n.getReadAt() == null) {
                n.setReadAt(Instant.now());
            }
        });
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return notificationRepository.markAllRead(userId, Instant.now());
    }
}

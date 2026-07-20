package com.ecoexpress.engagement.api;

import com.ecoexpress.common.security.AuthenticatedUser;
import com.ecoexpress.engagement.domain.Notification;
import com.ecoexpress.engagement.repository.NotificationRepository;
import com.ecoexpress.engagement.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The signed-in user's notifications. Scoped to their own id. */
@Tag(name = "Notifications")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;

    public record NotificationView(
            UUID id, String type, String title, String body, boolean read, Instant createdAt) {}

    @Operation(summary = "My notifications")
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<Notification> page = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId(), pageable);
        List<NotificationView> items = page.getContent().stream().map(this::view).toList();
        return ResponseEntity.ok(Map.of(
                "unread", notificationService.unreadCount(user.getId()),
                "notifications", items));
    }

    @Operation(summary = "Unread count (for the bell badge)")
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unread(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(Map.of("unread", notificationService.unreadCount(user.getId())));
    }

    @Operation(summary = "Mark one read")
    @PostMapping("/{id}/read")
    public ResponseEntity<Void> read(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        notificationService.markRead(user.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Mark all read")
    @PostMapping("/read-all")
    public ResponseEntity<Map<String, Integer>> readAll(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(Map.of("marked", notificationService.markAllRead(user.getId())));
    }

    private NotificationView view(Notification n) {
        return new NotificationView(n.getId(), n.getType().name(), n.getTitle(),
                n.getBody(), n.isRead(), n.getCreatedAt());
    }
}

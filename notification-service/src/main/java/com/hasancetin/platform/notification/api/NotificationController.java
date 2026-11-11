package com.hasancetin.platform.notification.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.hasancetin.platform.notification.application.NotificationService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public record NotificationResponse(UUID id, String recipient, String subject, String body, Instant createdAt) {
    }

    @GetMapping
    public List<NotificationResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        return notificationService.findForUser(jwt.getSubject()).stream()
                .map(n -> new NotificationResponse(n.getId(), n.getRecipient(), n.getSubject(), n.getBody(),
                        n.getCreatedAt()))
                .toList();
    }
}

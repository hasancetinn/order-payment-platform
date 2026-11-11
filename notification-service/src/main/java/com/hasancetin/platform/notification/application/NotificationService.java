package com.hasancetin.platform.notification.application;

import java.util.List;

import com.hasancetin.platform.common.events.AuthEvents.UserRegistered;
import com.hasancetin.platform.common.events.OrderEvents.OrderCancelled;
import com.hasancetin.platform.common.events.OrderEvents.OrderConfirmed;
import com.hasancetin.platform.common.idempotency.IdempotencyGuard;
import com.hasancetin.platform.notification.domain.Notification;
import com.hasancetin.platform.notification.domain.NotificationRepository;
import com.hasancetin.platform.notification.domain.UserContact;
import com.hasancetin.platform.notification.domain.UserContactRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationRepository notifications;
    private final UserContactRepository contacts;
    private final NotificationSender sender;
    private final IdempotencyGuard idempotency;

    public NotificationService(NotificationRepository notifications, UserContactRepository contacts,
                               NotificationSender sender, IdempotencyGuard idempotency) {
        this.notifications = notifications;
        this.contacts = contacts;
        this.sender = sender;
        this.idempotency = idempotency;
    }

    @Transactional
    public void handleUserRegistered(UserRegistered event) {
        if (!idempotency.markProcessed(event.eventId())) {
            return;
        }
        contacts.save(new UserContact(event.userId(), event.email()));
        notify(event.userId(), event.email(), "Welcome!",
                "Your account has been created. You can now place orders.");
    }

    @Transactional
    public void handleOrderConfirmed(OrderConfirmed event) {
        if (!idempotency.markProcessed(event.eventId())) {
            return;
        }
        notify(event.userId(), recipientOf(event.userId()), "Order confirmed",
                "Your order " + event.orderId() + " (" + event.totalAmount() + " " + event.currency()
                        + ") has been confirmed.");
    }

    @Transactional
    public void handleOrderCancelled(OrderCancelled event) {
        if (!idempotency.markProcessed(event.eventId())) {
            return;
        }
        notify(event.userId(), recipientOf(event.userId()), "Order cancelled",
                "Your order " + event.orderId() + " was cancelled. Reason: " + event.reason());
    }

    @Transactional(readOnly = true)
    public List<Notification> findForUser(String userId) {
        return notifications.findTop50ByUserIdOrderByCreatedAtDesc(userId);
    }

    private String recipientOf(String userId) {
        // fall back to the user id if the registration event has not been processed (yet)
        return contacts.findById(userId).map(UserContact::getEmail).orElse(userId);
    }

    private void notify(String userId, String recipient, String subject, String body) {
        notifications.save(new Notification(userId, recipient, subject, body));
        sender.send(recipient, subject, body);
    }
}

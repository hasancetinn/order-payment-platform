package com.hasancetin.platform.notification.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

/** Demo channel: "sends" by logging. Swap for an SMTP / provider implementation without touching the service. */
@Component
public class LoggingNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationSender.class);

    @Override
    public void send(String recipient, String subject, String body) {
        log.info("NOTIFICATION to <{}> | {} | {}", recipient, subject, body);
    }
}

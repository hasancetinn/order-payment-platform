package com.hasancetin.platform.notification.application;

/** Port to the delivery channel (SMTP, SMS gateway, push, ...). */
public interface NotificationSender {

    void send(String recipient, String subject, String body);
}

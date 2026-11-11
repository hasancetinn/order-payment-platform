package com.hasancetin.platform.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Local copy of the contact data of a user, filled from {@code UserRegistered} events (event-carried state transfer),
 * so this service never has to call auth-service synchronously.
 */
@Entity
@Table(name = "user_contact")
public class UserContact {

    @Id
    @Column(name = "user_id")
    private String userId;

    @Column(nullable = false)
    private String email;

    protected UserContact() {
    }

    public UserContact(String userId, String email) {
        this.userId = userId;
        this.email = email;
    }

    public String getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }
}

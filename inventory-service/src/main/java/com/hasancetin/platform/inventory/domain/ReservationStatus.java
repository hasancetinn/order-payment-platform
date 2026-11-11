package com.hasancetin.platform.inventory.domain;

public enum ReservationStatus {
    /** Stock is held for an order whose payment is still pending. */
    RESERVED,
    /** Payment succeeded, the stock is sold. */
    COMMITTED,
    /** Payment failed, the stock was returned to the shelf. */
    RELEASED
}

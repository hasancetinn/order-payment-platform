package com.hasancetin.platform.common.events;

import java.util.List;

public final class Topics {

    public static final String AUTH_EVENTS = "auth-events";
    public static final String ORDER_EVENTS = "order-events";
    public static final String INVENTORY_EVENTS = "inventory-events";
    public static final String PAYMENT_EVENTS = "payment-events";

    public static final List<String> ALL = List.of(AUTH_EVENTS, ORDER_EVENTS, INVENTORY_EVENTS, PAYMENT_EVENTS);

    private Topics() {
    }
}

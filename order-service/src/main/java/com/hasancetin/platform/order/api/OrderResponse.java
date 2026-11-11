package com.hasancetin.platform.order.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.hasancetin.platform.order.domain.Order;
import com.hasancetin.platform.order.domain.OrderStatus;

public record OrderResponse(UUID id, OrderStatus status, BigDecimal totalAmount, String currency,
                            String cancellationReason, Instant createdAt, Instant updatedAt, List<Item> items) {

    public record Item(String sku, int quantity, BigDecimal unitPrice) {
    }

    public static OrderResponse from(Order order) {
        return new OrderResponse(order.getId(), order.getStatus(), order.getTotalAmount(), order.getCurrency(),
                order.getCancellationReason(), order.getCreatedAt(), order.getUpdatedAt(),
                order.getItems().stream().map(i -> new Item(i.getSku(), i.getQuantity(), i.getUnitPrice())).toList());
    }
}

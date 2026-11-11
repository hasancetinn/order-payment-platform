package com.hasancetin.platform.order.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.hasancetin.platform.common.events.OrderLine;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    private String currency;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {
    }

    public static Order create(String userId, String currency, String idempotencyKey, List<OrderLine> lines) {
        Order order = new Order();
        order.id = UUID.randomUUID();
        order.userId = userId;
        order.currency = currency;
        order.idempotencyKey = idempotencyKey;
        order.status = OrderStatus.PENDING;
        order.createdAt = Instant.now();
        order.updatedAt = order.createdAt;
        BigDecimal total = BigDecimal.ZERO;
        for (OrderLine line : lines) {
            order.items.add(new OrderItem(order, line.sku(), line.quantity(), line.unitPrice()));
            total = total.add(line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())));
        }
        order.totalAmount = total;
        return order;
    }

    /** @return {@code true} if the state changed; a late or duplicate saga message returns {@code false} */
    public boolean confirm() {
        if (status != OrderStatus.PENDING) {
            return false;
        }
        status = OrderStatus.CONFIRMED;
        updatedAt = Instant.now();
        return true;
    }

    /** @return {@code true} if the state changed; a late or duplicate saga message returns {@code false} */
    public boolean cancel(String reason) {
        if (status != OrderStatus.PENDING) {
            return false;
        }
        status = OrderStatus.CANCELLED;
        cancellationReason = reason;
        updatedAt = Instant.now();
        return true;
    }

    public List<OrderLine> toLines() {
        return items.stream().map(i -> new OrderLine(i.getSku(), i.getQuantity(), i.getUnitPrice())).toList();
    }

    public UUID getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<OrderItem> getItems() {
        return items;
    }
}

package com.hasancetin.platform.order.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.hasancetin.platform.common.events.InventoryEvents.StockRejected;
import com.hasancetin.platform.common.events.OrderEvents.OrderCancelled;
import com.hasancetin.platform.common.events.OrderEvents.OrderConfirmed;
import com.hasancetin.platform.common.events.OrderEvents.OrderCreated;
import com.hasancetin.platform.common.events.OrderLine;
import com.hasancetin.platform.common.events.PaymentEvents.PaymentCompleted;
import com.hasancetin.platform.common.events.PaymentEvents.PaymentFailed;
import com.hasancetin.platform.common.events.Topics;
import com.hasancetin.platform.common.idempotency.IdempotencyGuard;
import com.hasancetin.platform.common.outbox.OutboxService;
import com.hasancetin.platform.order.api.CreateOrderRequest;
import com.hasancetin.platform.order.api.OrderResponse;
import com.hasancetin.platform.order.domain.Order;
import com.hasancetin.platform.order.domain.OrderRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Owns the order aggregate. It starts the saga by writing {@code OrderCreated} to the outbox and finishes it by
 * reacting to inventory / payment events. Every state change and the event it produces share one transaction.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orders;
    private final OutboxService outbox;
    private final IdempotencyGuard idempotency;

    public OrderService(OrderRepository orders, OutboxService outbox, IdempotencyGuard idempotency) {
        this.orders = orders;
        this.outbox = outbox;
        this.idempotency = idempotency;
    }

    @Transactional
    public OrderResponse createOrder(String userId, String idempotencyKey, CreateOrderRequest request) {
        if (idempotencyKey != null) {
            Optional<Order> existing = orders.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
            if (existing.isPresent()) {
                log.info("Idempotent replay of order {} for key {}", existing.get().getId(), idempotencyKey);
                return OrderResponse.from(existing.get());
            }
        }

        Order order = Order.create(userId, request.currency(), idempotencyKey, mergeBySku(request));
        orders.save(order);
        outbox.publish(Topics.ORDER_EVENTS, OrderCreated.of(order.getId(), userId, order.toLines(),
                order.getTotalAmount(), order.getCurrency()));
        log.info("Order {} created for user {}, total {} {}", order.getId(), userId,
                order.getTotalAmount(), order.getCurrency());
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(String userId, UUID orderId) {
        return orders.findById(orderId)
                .filter(o -> o.getUserId().equals(userId))
                .map(OrderResponse::from)
                // 404 instead of 403 so that order ids of other users are not revealed
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listOrders(String userId) {
        return orders.findTop50ByUserIdOrderByCreatedAtDesc(userId).stream().map(OrderResponse::from).toList();
    }

    // ---- saga reactions -------------------------------------------------------------------------------------

    @Transactional
    public void handlePaymentCompleted(PaymentCompleted event) {
        if (!idempotency.markProcessed(event.eventId())) {
            return;
        }
        orders.findById(event.orderId()).ifPresent(order -> {
            if (order.confirm()) {
                outbox.publish(Topics.ORDER_EVENTS, OrderConfirmed.of(order.getId(), order.getUserId(),
                        order.getTotalAmount(), order.getCurrency()));
                log.info("Order {} confirmed", order.getId());
            }
        });
    }

    @Transactional
    public void handleStockRejected(StockRejected event) {
        cancel(event.eventId(), event.orderId(), "Stock rejected: " + event.reason());
    }

    @Transactional
    public void handlePaymentFailed(PaymentFailed event) {
        cancel(event.eventId(), event.orderId(), "Payment failed: " + event.reason());
    }

    private void cancel(UUID eventId, UUID orderId, String reason) {
        if (!idempotency.markProcessed(eventId)) {
            return;
        }
        orders.findById(orderId).ifPresent(order -> {
            if (order.cancel(reason)) {
                outbox.publish(Topics.ORDER_EVENTS, OrderCancelled.of(order.getId(), order.getUserId(), reason));
                log.info("Order {} cancelled: {}", order.getId(), reason);
            }
        });
    }

    private static List<OrderLine> mergeBySku(CreateOrderRequest request) {
        Map<String, OrderLine> merged = new LinkedHashMap<>();
        for (CreateOrderRequest.Item item : request.items()) {
            merged.merge(item.sku(), new OrderLine(item.sku(), item.quantity(), item.unitPrice()), (a, b) -> {
                if (a.unitPrice().compareTo(b.unitPrice()) != 0) {
                    throw new IllegalArgumentException("Conflicting unit prices for " + a.sku());
                }
                return new OrderLine(a.sku(), a.quantity() + b.quantity(), a.unitPrice());
            });
        }
        return List.copyOf(merged.values());
    }
}

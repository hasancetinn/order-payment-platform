package com.hasancetin.platform.inventory.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.hasancetin.platform.common.events.InventoryEvents.StockRejected;
import com.hasancetin.platform.common.events.InventoryEvents.StockReserved;
import com.hasancetin.platform.common.events.OrderEvents.OrderCreated;
import com.hasancetin.platform.common.events.OrderLine;
import com.hasancetin.platform.common.events.PaymentEvents.PaymentCompleted;
import com.hasancetin.platform.common.events.PaymentEvents.PaymentFailed;
import com.hasancetin.platform.common.events.Topics;
import com.hasancetin.platform.common.idempotency.IdempotencyGuard;
import com.hasancetin.platform.common.outbox.OutboxService;
import com.hasancetin.platform.inventory.domain.Product;
import com.hasancetin.platform.inventory.domain.ProductRepository;
import com.hasancetin.platform.inventory.domain.ReservationStatus;
import com.hasancetin.platform.inventory.domain.StockReservation;
import com.hasancetin.platform.inventory.domain.StockReservationRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Saga participant: reserves stock for new orders and compensates (releases it) when payment fails. */
@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final ProductRepository products;
    private final StockReservationRepository reservations;
    private final OutboxService outbox;
    private final IdempotencyGuard idempotency;

    public InventoryService(ProductRepository products, StockReservationRepository reservations,
                            OutboxService outbox, IdempotencyGuard idempotency) {
        this.products = products;
        this.reservations = reservations;
        this.outbox = outbox;
        this.idempotency = idempotency;
    }

    @Transactional
    public void handleOrderCreated(OrderCreated event) {
        if (!idempotency.markProcessed(event.eventId())) {
            return;
        }
        if (reservations.existsByOrderId(event.orderId())) {
            log.info("Order {} already has a reservation, ignoring", event.orderId());
            return;
        }

        Map<String, Integer> wanted = new TreeMap<>();
        for (OrderLine line : event.items()) {
            wanted.merge(line.sku(), line.quantity(), Integer::sum);
        }

        Map<String, Product> locked = products.lockBySkuIn(wanted.keySet()).stream()
                .collect(Collectors.toMap(Product::getSku, Function.identity()));

        List<String> problems = new ArrayList<>();
        wanted.forEach((sku, quantity) -> {
            Product product = locked.get(sku);
            if (product == null) {
                problems.add(sku + " does not exist");
            } else if (!product.canReserve(quantity)) {
                problems.add(sku + " has only " + product.getAvailableQuantity() + " left (requested " + quantity + ")");
            }
        });

        if (!problems.isEmpty()) {
            String reason = String.join("; ", problems);
            outbox.publish(Topics.INVENTORY_EVENTS, StockRejected.of(event.orderId(), reason));
            log.info("Stock rejected for order {}: {}", event.orderId(), reason);
            return;
        }

        wanted.forEach((sku, quantity) -> {
            locked.get(sku).reserve(quantity);
            reservations.save(new StockReservation(event.orderId(), sku, quantity));
        });
        outbox.publish(Topics.INVENTORY_EVENTS, StockReserved.of(event.orderId(), event.userId(),
                event.totalAmount(), event.currency()));
        log.info("Stock reserved for order {}", event.orderId());
    }

    /** Compensation: put the reserved quantities back on the shelf. */
    @Transactional
    public void handlePaymentFailed(PaymentFailed event) {
        if (!idempotency.markProcessed(event.eventId())) {
            return;
        }
        List<StockReservation> held = reservations.findByOrderId(event.orderId());
        Map<String, Product> locked = products
                .lockBySkuIn(held.stream().map(StockReservation::getSku).distinct().sorted().toList()).stream()
                .collect(Collectors.toMap(Product::getSku, Function.identity()));
        for (StockReservation reservation : held) {
            if (reservation.getStatus() == ReservationStatus.RESERVED) {
                locked.get(reservation.getSku()).release(reservation.getQuantity());
                reservation.release();
            }
        }
        log.info("Stock released for order {} after failed payment", event.orderId());
    }

    @Transactional
    public void handlePaymentCompleted(PaymentCompleted event) {
        if (!idempotency.markProcessed(event.eventId())) {
            return;
        }
        reservations.findByOrderId(event.orderId()).forEach(StockReservation::commit);
        log.info("Stock committed for order {}", event.orderId());
    }

    @Transactional(readOnly = true)
    public List<Product> listProducts() {
        return products.findAllByOrderBySku();
    }
}

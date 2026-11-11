package com.hasancetin.platform.order.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import com.hasancetin.platform.order.application.OrderService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Accepts an order and starts the saga. The response is {@code 202 Accepted}: the final status
     * (CONFIRMED / CANCELLED) is reached asynchronously, poll {@code GET /orders/{id}} to follow it.
     * Sending the same {@code Idempotency-Key} again returns the original order instead of creating a new one.
     */
    @PostMapping
    public ResponseEntity<OrderResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                @Valid @RequestBody CreateOrderRequest request) {
        OrderResponse response = orderService.createOrder(jwt.getSubject(), idempotencyKey, request);
        return ResponseEntity.accepted().location(URI.create("/orders/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    public OrderResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return orderService.getOrder(jwt.getSubject(), id);
    }

    @GetMapping
    public List<OrderResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return orderService.listOrders(jwt.getSubject());
    }
}

package com.hasancetin.platform.payment.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.hasancetin.platform.payment.application.PaymentService;
import com.hasancetin.platform.payment.domain.PaymentStatus;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    public record PaymentResponse(UUID id, UUID orderId, BigDecimal amount, String currency, PaymentStatus status,
                                  String failureReason, Instant createdAt) {
    }

    @GetMapping("/order/{orderId}")
    public PaymentResponse getByOrder(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
        return paymentService.findForUser(jwt.getSubject(), orderId)
                .map(p -> new PaymentResponse(p.getId(), p.getOrderId(), p.getAmount(), p.getCurrency(),
                        p.getStatus(), p.getFailureReason(), p.getCreatedAt()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
    }
}

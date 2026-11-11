package com.hasancetin.platform.payment.provider;

import java.math.BigDecimal;
import java.util.UUID;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

/**
 * Resilient facade around the payment provider.
 * <p>
 * Aspect order is Retry(CircuitBreaker(call)): every attempt is recorded by the circuit breaker, transient errors are
 * retried, and once retries are exhausted (or the breaker is open and rejects calls) the fallback turns the outage
 * into a controlled "declined" result, which makes the saga compensate instead of blocking the consumer.
 */
@Component
public class PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(PaymentGateway.class);
    static final String INSTANCE = "paymentProvider";

    private final PaymentProvider provider;

    public PaymentGateway(PaymentProvider provider) {
        this.provider = provider;
    }

    @Retry(name = INSTANCE, fallbackMethod = "unavailable")
    @CircuitBreaker(name = INSTANCE)
    public ChargeResult charge(UUID orderId, BigDecimal amount, String currency) {
        return provider.charge(orderId, amount, currency);
    }

    @SuppressWarnings("unused") // resolved by name through resilience4j
    private ChargeResult unavailable(UUID orderId, BigDecimal amount, String currency, Throwable cause) {
        log.warn("Payment provider unavailable for order {}: {}", orderId, cause.toString());
        return ChargeResult.declined("Payment provider unavailable, please try again later");
    }
}

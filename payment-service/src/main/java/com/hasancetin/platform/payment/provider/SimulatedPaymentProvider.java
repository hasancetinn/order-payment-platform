package com.hasancetin.platform.payment.provider;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Stand-in for a real card processor so the whole saga can be demonstrated without external accounts.
 * <ul>
 *   <li>amounts above {@code payment.simulation.decline-above} are declined (business failure)</li>
 *   <li>{@code payment.simulation.failure-rate} (0..1) randomly simulates provider outages (technical failure),
 *       which exercises retry and circuit breaker</li>
 * </ul>
 */
@Component
public class SimulatedPaymentProvider implements PaymentProvider {

    private final BigDecimal declineAbove;
    private final double failureRate;

    public SimulatedPaymentProvider(@Value("${payment.simulation.decline-above:10000}") BigDecimal declineAbove,
                                    @Value("${payment.simulation.failure-rate:0.0}") double failureRate) {
        this.declineAbove = declineAbove;
        this.failureRate = failureRate;
    }

    @Override
    public ChargeResult charge(UUID orderId, BigDecimal amount, String currency) {
        if (failureRate > 0 && ThreadLocalRandom.current().nextDouble() < failureRate) {
            throw new PaymentProviderUnavailableException("Simulated payment provider outage");
        }
        if (amount.compareTo(declineAbove) > 0) {
            return ChargeResult.declined("Amount exceeds the card limit of " + declineAbove + " " + currency);
        }
        return ChargeResult.approved("sim-" + UUID.randomUUID());
    }
}

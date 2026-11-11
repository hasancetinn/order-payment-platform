package com.hasancetin.platform.payment.provider;

import java.math.BigDecimal;
import java.util.UUID;

/** Port to the external card processor. */
public interface PaymentProvider {

    /**
     * @return an approval or a business decline
     * @throws PaymentProviderUnavailableException on technical problems (timeouts, 5xx), which may be retried
     */
    ChargeResult charge(UUID orderId, BigDecimal amount, String currency);
}

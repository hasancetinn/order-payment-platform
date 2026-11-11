package com.hasancetin.platform.payment.provider;

public class PaymentProviderUnavailableException extends RuntimeException {

    public PaymentProviderUnavailableException(String message) {
        super(message);
    }
}

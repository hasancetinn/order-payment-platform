package com.hasancetin.platform.payment.provider;

public record ChargeResult(boolean approved, String reference, String failureReason) {

    public static ChargeResult approved(String reference) {
        return new ChargeResult(true, reference, null);
    }

    public static ChargeResult declined(String reason) {
        return new ChargeResult(false, null, reason);
    }
}

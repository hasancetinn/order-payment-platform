package com.hasancetin.platform.order.api;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateOrderRequest(
        @NotEmpty @Valid List<Item> items,
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be an ISO 4217 code such as EUR") String currency) {

    public record Item(
            @NotBlank String sku,
            @Min(1) int quantity,
            @NotNull @DecimalMin("0.01") BigDecimal unitPrice) {
    }
}

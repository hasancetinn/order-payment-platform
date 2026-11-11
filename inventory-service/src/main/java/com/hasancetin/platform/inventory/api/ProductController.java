package com.hasancetin.platform.inventory.api;

import java.math.BigDecimal;
import java.util.List;

import com.hasancetin.platform.inventory.application.InventoryService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final InventoryService inventoryService;

    public ProductController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    public record ProductResponse(String sku, String name, BigDecimal price, int availableQuantity) {
    }

    @GetMapping
    public List<ProductResponse> list() {
        return inventoryService.listProducts().stream()
                .map(p -> new ProductResponse(p.getSku(), p.getName(), p.getPrice(), p.getAvailableQuantity()))
                .toList();
    }
}

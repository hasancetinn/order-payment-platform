package com.hasancetin.platform.inventory.domain;

import java.util.Collection;
import java.util.List;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, String> {

    /**
     * Row-locks the products in a stable (sku) order, so two orders reserving overlapping SKUs can never
     * deadlock and stock can never be oversold.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.sku in :skus order by p.sku")
    List<Product> lockBySkuIn(@Param("skus") Collection<String> skus);

    List<Product> findAllByOrderBySku();
}

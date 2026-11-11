package com.hasancetin.platform.inventory.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {

    boolean existsByOrderId(UUID orderId);

    List<StockReservation> findByOrderId(UUID orderId);
}

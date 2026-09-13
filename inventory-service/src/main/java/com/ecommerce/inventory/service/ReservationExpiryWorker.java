package com.ecommerce.inventory.service;

import com.ecommerce.inventory.entity.InventoryReservation;
import com.ecommerce.inventory.repository.InventoryReservationRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ReservationExpiryWorker {
    private final InventoryReservationRepository reservations;
    private final InventoryService inventoryService;
    private final MeterRegistry meters;
    @Scheduled(fixedDelayString = "${inventory.reservation.expiry-scan-delay:30000}")
    void releaseExpired() {
        for (InventoryReservation reservation : reservations.findExpiredReserved(Instant.now())) {
            if (inventoryService.releaseExpiredReservation(reservation.getId())) {
                meters.counter("inventory_reservations_expired_released_total").increment();
            }
        }
    }
}

package com.computerstore.shipping.repository;

import java.util.List;
import java.util.UUID;
import com.computerstore.shipping.domain.ShipmentEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentEventRepository extends JpaRepository<ShipmentEvent, Long> {
    boolean existsByEventKey(String eventKey);
    List<ShipmentEvent> findByShipmentIdOrderByOccurredAtAscIdAsc(UUID shipmentId);
}

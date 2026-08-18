package com.computerstore.payment.repository;

import com.computerstore.payment.domain.PaymentEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, Long> {
    boolean existsByEventKey(String eventKey);
    Optional<PaymentEvent> findByEventKey(String eventKey);
}

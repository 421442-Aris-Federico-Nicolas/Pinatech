package com.computerstore.service.repository;

import java.util.List;
import com.computerstore.service.domain.TicketStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketStatusHistoryRepository extends JpaRepository<TicketStatusHistory, Long> {
    List<TicketStatusHistory> findByTicketIdOrderByChangedAtAsc(Long ticketId);
}
